package dev.booky.betterview.nms.v1214;
// Created by booky10 in BetterView (20:38 03.06.2025)

import dev.booky.betterview.common.antixray.AntiXrayProcessor;
import dev.booky.betterview.common.antixray.ReplacementPresets;
import dev.booky.betterview.common.antixray.ReplacementStrategy;
import dev.booky.betterview.nms.ReflectionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.stream.Stream;

@NullMarked
public final class ChunkWriter {

    static final Heightmap.Types[] SENDABLE_HEIGHTMAP_TYPES = Arrays.stream(Heightmap.Types.values())
            .filter(Heightmap.Types::sendToClient).toArray(Heightmap.Types[]::new);

    private static final VarHandle NON_EMPTY_BLOCK_COUNT = ReflectionUtil.getField(LevelChunkSection.class, short.class, 0);

    private static final int[] ANTI_XRAY_OBF_STATES = Stream.of(
                    Blocks.STONE, Blocks.DEEPSLATE,
                    Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                    Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                    Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
                    Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
                    Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                    Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                    Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
                    Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER
            )
            .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
            .mapToInt(Block.BLOCK_STATE_REGISTRY::getId)
            .toArray();
    private static final AntiXrayProcessor ANTI_XRAY = new AntiXrayProcessor(
            ReplacementStrategy::replaceStaticZero,
            ReplacementPresets.createStaticZeroSplit(
                    new int[]{Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState())},
                    new int[]{Block.BLOCK_STATE_REGISTRY.getId(Blocks.DEEPSLATE.defaultBlockState())}),
            ANTI_XRAY_OBF_STATES,
            Block.BLOCK_STATE_REGISTRY.size()
    );
    private static final AntiXrayProcessor ANTI_XRAY_R = new AntiXrayProcessor(
            ReplacementStrategy::replaceRandomLayered,
            ReplacementPresets.createStatic(ANTI_XRAY_OBF_STATES),
            ANTI_XRAY_OBF_STATES,
            Block.BLOCK_STATE_REGISTRY.size()
    );

    private ChunkWriter() {
    }

    private static boolean isEmpty(ChunkAccess chunk) {
        if (chunk instanceof EmptyLevelChunk) {
            return true;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0, len = sections.length; i < len; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    public static CompoundTag extractHeightmapTags(ChunkAccess chunk) {
        CompoundTag heightmapsTag = new CompoundTag();
        for (int i = 0, len = SENDABLE_HEIGHTMAP_TYPES.length; i < len; i++) {
            Heightmap.Types type = SENDABLE_HEIGHTMAP_TYPES[i];
            if (chunk.hasPrimedHeightmap(type)) {
                long[] heightmapData = chunk.getOrCreateHeightmapUnprimed(type).getRawData();
                heightmapsTag.put(type.getSerializationKey(), new LongArrayTag(heightmapData));
            }
        }
        return heightmapsTag;
    }

    public static ByteBuf writeFullOrEmpty(ChunkAccess chunk) {
        if (isEmpty(chunk)) {
            return Unpooled.EMPTY_BUFFER;
        }
        CompoundTag heightmapTags = extractHeightmapTags(chunk);
        // convert lighting data
        byte[][] blockLight = LightWriter.convertStarlightToBytes(chunk.starlight$getBlockNibbles(), false);
        byte[][] skyLight = LightWriter.convertStarlightToBytes(chunk.starlight$getSkyNibbles(), true);
        // delegate to chunk writing method
        return writeFull(chunk.locX, chunk.locZ, chunk.getMinSectionY(),
                heightmapTags, chunk.getSections(), blockLight, skyLight);
    }

    public static ByteBuf writeFull(
            int chunkX, int chunkZ, int minSectionY, CompoundTag heightmapsTag,
            LevelChunkSection[] sections, byte[][] blockLight, byte @Nullable [][] skyLight
    ) {
        // allocate pooled buffer
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer();
        try {
            // packet id
            buf.writeByte(NmsAdapter.LEVEL_CHUNK_WITH_LIGHT_PACKET_ID);
            // chunk position
            buf.writeInt(chunkX);
            buf.writeInt(chunkZ);
            // write buffer body
            writeFullBody(buf, minSectionY, heightmapsTag, sections, blockLight, skyLight);
            return buf.retain();
        } finally {
            buf.release();
        }
    }

    public static void writeFullBody(
            ByteBuf buf, int minSectionY,
            CompoundTag heightmapsTag, LevelChunkSection[] sections,
            byte[][] blockLight, byte @Nullable [][] skyLight
    ) {
        // write heightmaps nbt tag
        FriendlyByteBuf.writeNbt(buf, heightmapsTag);
        // directly write chunk data, don't create useless sub-buffer TODO re-implement this behavior
        ByteBuf subBuf = PooledByteBufAllocator.DEFAULT.buffer();
        try {
            FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(subBuf);
            for (int i = 0, len = sections.length; i < len; i++) {
                writeSection(friendlyBuf, sections[i], i + minSectionY);
            }
            VarInt.write(buf, subBuf.readableBytes());
            buf.writeBytes(subBuf);
        } finally {
            subBuf.release();
        }
        // ensure the vanilla client can read this data
//        if (buf.writerIndex() != expectedWriterIndex) {
//            throw new IllegalStateException("Expected writer index to be at "
//                    + expectedWriterIndex + ", got " + buf.writerIndex());
//        }
        // skip writing block entity list
        VarInt.write(buf, 0);
        // write light data
        LightWriter.writeLightData(buf, blockLight, skyLight);
    }

    private static void writeSection(FriendlyByteBuf buf, LevelChunkSection section, int sectionY) {
        buf.writeShort((short) NON_EMPTY_BLOCK_COUNT.get(section));

        int ri = buf.readerIndex();
        int wi = buf.writerIndex();
        section.states.write(buf, null, 0);
        buf.readerIndex(wi);
        ANTI_XRAY_R.process(buf, sectionY, true);
        buf.readerIndex(ri);

        section.getBiomes().write(buf, null, 0);
    }
}
