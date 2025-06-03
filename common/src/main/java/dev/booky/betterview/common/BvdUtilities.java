package dev.booky.betterview.common;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.util.ParallelSearchRadiusIteration;
import ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil;
import com.mojang.serialization.Codec;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@NullMarked
public final class BvdUtilities {

    private static final Heightmap.Types[] NON_CLIENT_HEIGHTMAPS = Arrays.stream(Heightmap.Types.values())
            .filter(Predicate.not(Heightmap.Types::sendToClient))
            .toArray(Heightmap.Types[]::new);

    // magic packet id values
    private static final ByteBuf FORGET_LEVEL_CHUNK_PACKET_ID = Unpooled.wrappedBuffer(new byte[]{0x22});
    private static final ByteBuf LEVEL_CHUNK_WITH_LIGHT_PACKET_ID = Unpooled.wrappedBuffer(new byte[]{0x28});

    public static final int MAX_CHUNK_DISTANCE = 128;
    // use moonrise's ParallelSearchRadiusIteration generation to make this more FAF (fast-as-fuck)
    static final long[][] BVD_RADIUS_ITERATION_LIST = Util.make(() -> {
        long[][] list = new long[MAX_CHUNK_DISTANCE + 2 + 1][];
        for (int radius = 0; radius < list.length; radius++) {
            int finalRadius = radius;
            list[radius] = Arrays.stream(ParallelSearchRadiusIteration.generateBFSOrder(radius))
                    .mapToObj(McChunkPos::new)
                    .filter(pos -> isWithinRange(
                            pos.x, pos.z, finalRadius))
                    // .sorted(Comparator.comparingInt(p -> p.x * p.x + p.z * p.z))
                    .mapToLong(McChunkPos::getKey)
                    .toArray();
        }
        return list;
    });

    // limit everything to minecraft's max dimension size
    static final int MAX_LEVEL_SIZE_CHUNKS = Level.MAX_LEVEL_SIZE << 4;

    private BvdUtilities() {
    }

    public static boolean isChunkLit(CompoundTag tag) {
        ChunkStatus status = ChunkStatus.byName(tag.getString("Status"));
        if (!status.isOrAfter(ChunkStatus.LIGHT)) {
            return false;
        }
        if (tag.get(SerializableChunkData.IS_LIGHT_ON_TAG) == null) {
            return false;
        }

        int lightVersion = tag.getInt(SaveUtil.STARLIGHT_VERSION_TAG);
        return lightVersion == SaveUtil.getLightVersion();
    }

    public static CompletableFuture<@Nullable CompoundTag> readChunk(ServerLevel level, McChunkPos chunkPos) {
        return level.chunkSource.chunkMap.readChunk(chunkPos)
                .thenApply(chunkTag -> chunkTag.orElse(null));
    }

    public static @Nullable LevelChunk getCachedChunk(ServerLevel level, long longKey) {
        NewChunkHolder holder = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(longKey);
        if (holder == null) {
            return null;
        }
        ChunkAccess accessed = holder.getChunkIfPresent(ChunkStatus.LIGHT);
        if (accessed instanceof LevelChunk) {
            return (LevelChunk) accessed;
        }
        return null;
    }

    public static @Nullable BvdChunk loadChunk(ServerLevel level, McChunkPos pos, CompoundTag tag) {
        ListTag sectionTags = tag.getList(SerializableChunkData.SECTIONS_TAG, Tag.TAG_COMPOUND);
        LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];

        int minSection = WorldUtil.getMinLightSection(level);
        boolean hasSky = level.dimensionType().hasSkyLight();

        byte[][] blockLight = new byte[WorldUtil.getTotalLightSections(level)][];
        byte[][] skyLight = hasSky ? new byte[blockLight.length][] : null;

        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Codec<PalettedContainer<Holder<Biome>>> biomeCodec = SerializableChunkData.makeBiomeCodecRW(biomeRegistry);

        boolean onlyAir = true;
        for (int i = 0; i < sectionTags.size(); ++i) {
            CompoundTag sectionTag = sectionTags.getCompound(i);
            byte sectionY = sectionTag.getByte("Y");
            int sectionIndex = level.getSectionIndexFromSectionY(sectionY);

            if (sectionIndex >= 0 && sectionIndex < sections.length) {
                BlockState[] presetBlockStates = level.chunkPacketBlockController
                        .getPresetBlockStates(level, pos, sectionY);

                PalettedContainer<BlockState> blocks;
                if (sectionTag.contains("block_states", Tag.TAG_COMPOUND)) {
                    Codec<PalettedContainer<BlockState>> blockStateCodec;
                    if (presetBlockStates != null) {
                        blockStateCodec = PalettedContainer.codecRW(
                                Block.BLOCK_STATE_REGISTRY,
                                BlockState.CODEC,
                                PalettedContainer.Strategy.SECTION_STATES,
                                Blocks.AIR.defaultBlockState(),
                                presetBlockStates
                        );
                    } else {
                        blockStateCodec = SerializableChunkData.BLOCK_STATE_CODEC;
                    }

                    blocks = blockStateCodec
                            .parse(NbtOps.INSTANCE, sectionTag.getCompound("block_states"))
                            .getOrThrow();
                } else {
                    blocks = new PalettedContainer<>(
                            Block.BLOCK_STATE_REGISTRY,
                            Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES,
                            presetBlockStates
                    );
                }

                PalettedContainer<Holder<Biome>> biomes;
                if (sectionTag.contains("biomes", Tag.TAG_COMPOUND)) {
                    biomes = biomeCodec
                            .parse(NbtOps.INSTANCE, sectionTag.getCompound("biomes"))
                            .getOrThrow();
                } else {
                    biomes = new PalettedContainer<>(
                            biomeRegistry.asHolderIdMap(),
                            biomeRegistry.getOrThrow(Biomes.PLAINS),
                            PalettedContainer.Strategy.SECTION_BIOMES,
                            null
                    );
                }

                LevelChunkSection section = new LevelChunkSection(blocks, biomes);
                sections[sectionIndex] = section;

                if (!section.hasOnlyAir()) {
                    onlyAir = false;
                }
            }

            if (sectionTag.contains(SerializableChunkData.BLOCK_LIGHT_TAG, Tag.TAG_BYTE_ARRAY)) {
                blockLight[sectionY - minSection] = sectionTag.getByteArray(SerializableChunkData.BLOCK_LIGHT_TAG);
            }

            if (hasSky && sectionTag.contains(SerializableChunkData.SKY_LIGHT_TAG, Tag.TAG_BYTE_ARRAY)) {
                skyLight[sectionY - minSection] = sectionTag.getByteArray(SerializableChunkData.SKY_LIGHT_TAG);
            }
        }

        if (onlyAir) {
            return null;
        }

        CompoundTag heightmaps = tag.getCompound(SerializableChunkData.HEIGHTMAPS_TAG).copy();
        for (int i = 0, len = NON_CLIENT_HEIGHTMAPS.length; i < len; i++) {
            heightmaps.remove(NON_CLIENT_HEIGHTMAPS[i].getSerializationKey());
        }

        return BvdChunk.create(level, sections, heightmaps, blockLight, skyLight);
    }

    public static ByteBuf buildChunkUnload(long chunkKey) {
        // encode chunk location to bytes
        final ByteBuf chunkPos = Unpooled.wrappedBuffer(new byte[]{
                (byte) (chunkKey >> 56), (byte) (chunkKey >> 48), (byte) (chunkKey >> 40), (byte) (chunkKey >> 32),
                (byte) (chunkKey >> 24), (byte) (chunkKey >> 16), (byte) (chunkKey >> 8), (byte) chunkKey,
        });
        return Unpooled.compositeBuffer(2)
                .addComponent(true, FORGET_LEVEL_CHUNK_PACKET_ID.retainedSlice())
                .addComponent(true, chunkPos);
    }

    public static ByteBuf getEmptyChunk(McChunkPos pos, ServerLevel level) {
        if (level.emptyChunkData == null) {
            throw new IllegalStateException("Tried creating empty chunk for non-void level");
        }
        final CompositeByteBuf meta = buildMetaChunkPacket(pos.x, pos.z);
        return meta.addComponent(true, level.emptyChunkData.retainedSlice());
    }

    public static ByteBuf buildFullChunkPacket(McChunkPos pos, BvdChunk chunk) {
        final CompositeByteBuf meta = buildMetaChunkPacket(pos.x, pos.z);
        return meta.addComponent(true, buildChunkPacket(chunk));
    }

    public static CompositeByteBuf buildMetaChunkPacket(int chunkX, int chunkZ) {
        // encode chunk location to bytes
        final ByteBuf chunkPos = Unpooled.wrappedBuffer(new byte[]{
                (byte) (chunkX >> 24), (byte) (chunkX >> 16), (byte) (chunkX >> 8), (byte) chunkX,
                (byte) (chunkZ >> 24), (byte) (chunkZ >> 16), (byte) (chunkZ >> 8), (byte) chunkZ,
        });
        return Unpooled.compositeBuffer(3) // leave room for chunk data
                .addComponent(true, LEVEL_CHUNK_WITH_LIGHT_PACKET_ID.retainedSlice())
                .addComponent(true, chunkPos);
    }

    public static ByteBuf buildChunkPacket(BvdChunk chunk) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer();

        // Chunk data: Heightmaps
        buf.writeBytes(chunk.heightmapsTag);

        // Chunk data: Blocks & Biomes
        int serializedSize = 0;
        for (LevelChunkSection section : chunk.sections) {
            serializedSize += section.getSerializedSize();
        }
        FriendlyByteBuf chunkData = new FriendlyByteBuf(
                PooledByteBufAllocator.DEFAULT.directBuffer(serializedSize));
        try {
            for (LevelChunkSection section : chunk.sections) {
                section.write(chunkData, null, 0);
            }
            VarInt.write(buf, chunkData.readableBytes());
            buf.writeBytes(chunkData);
        } finally {
            chunkData.release();
        }

        // Skipped chunk data: Block entities
        buf.writeByte(0); // varint

        writeLightData(buf, chunk.blockLight, chunk.skyLight);
        return buf;
    }

    private static void writeBitSet(ByteBuf buf, long[] set) {
        int len = set.length;
        VarInt.write(buf, len);
        for (int i = 0; i < len; ++i) {
            buf.writeLong(set[i]);
        }
    }

    private static void writeByteArrayList(ByteBuf buf, List<byte[]> list) {
        int len = list.size();
        if (len == 0) {
            buf.writeByte(0); // varint
        } else if (len == 1) {
            buf.writeByte(1); // varint
            FriendlyByteBuf.writeByteArray(buf, list.getFirst());
        } else {
            VarInt.write(buf, len);
            for (int i = 0; i < len; ++i) {
                FriendlyByteBuf.writeByteArray(buf, list.get(i));
            }
        }
    }

    private static void writeLightData(ByteBuf buf, byte[][] blockLight, byte @Nullable [][] skyLight) {
        if (skyLight == null) {
            writeNoSkyLightData(buf, blockLight);
            return;
        }

        // Light data: Generating the light data
        List<byte[]> skyData = new ArrayList<>(skyLight.length);
        BitSet notSkyEmpty = new BitSet();
        BitSet skyEmpty = new BitSet();

        int blockLightLen = blockLight.length;
        List<byte[]> blockData = new ArrayList<>(blockLightLen);
        BitSet notBlockEmpty = new BitSet();
        BitSet blockEmpty = new BitSet();

        for (int indexY = 0; indexY < blockLightLen; indexY++) {
            byte[] sky = skyLight[indexY];
            if (sky == null) {
                skyEmpty.set(indexY);
            } else {
                notSkyEmpty.set(indexY);
                skyData.add(sky);
            }
            byte[] block = blockLight[indexY];
            if (block == null) {
                blockEmpty.set(indexY);
            } else {
                notBlockEmpty.set(indexY);
                blockData.add(block);
            }
        }

        // Light data: Writing the light data
        writeBitSet(buf, notSkyEmpty.toLongArray());
        writeBitSet(buf, notBlockEmpty.toLongArray());
        writeBitSet(buf, skyEmpty.toLongArray());
        writeBitSet(buf, blockEmpty.toLongArray());
        writeByteArrayList(buf, skyData);
        writeByteArrayList(buf, blockData);
    }

    private static void writeNoSkyLightData(ByteBuf buf, byte[][] blockLight) {
        // Light data: Generating the light data
        int blockLightLen = blockLight.length;
        List<byte[]> blockData = new ArrayList<>(blockLightLen);
        BitSet notBlockEmpty = new BitSet();
        BitSet blockEmpty = new BitSet();

        for (int indexY = 0; indexY < blockLightLen; indexY++) {
            byte[] block = blockLight[indexY];
            if (block == null) {
                blockEmpty.set(indexY);
            } else {
                notBlockEmpty.set(indexY);
                blockData.add(block);
            }
        }

        // Light data: Writing the light data
        buf.writeByte(0); // sky light y mask length
        writeBitSet(buf, notBlockEmpty.toLongArray());
        buf.writeByte(0); // sky light empty y mask length
        writeBitSet(buf, blockEmpty.toLongArray());
        buf.writeByte(0); // sky light data length
        writeByteArrayList(buf, blockData);
    }

    public static boolean isWithinRange(int posX, int posZ, int viewDistance) {
        int absX = Math.abs(posX);
        int absZ = Math.abs(posZ);
        // check done by extra moonrise logic
        int squareDist = Math.max(absX, absZ);
        if (squareDist > viewDistance + 1) {
            return false; // outside square distance
        }
        // check done by vanilla server logic
        long distX = Math.max(0, absX - 2);
        long distZ = Math.max(0, absZ - 2);
        long distXZSqrt = distX * distX + distZ * distZ;
        int viewDistanceSqrt = viewDistance * viewDistance;
        return distXZSqrt < viewDistanceSqrt; // outside cylindrical distance
    }
}
