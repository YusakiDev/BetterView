package dev.booky.betterview.common;

import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@NullMarked
public final class BvdChunk {

    public final ServerLevel level;
    public final LevelChunkSection[] sections;
    public final byte[] heightmapsTag;
    public final byte[][] blockLight;
    public final byte @Nullable [][] skyLight;

    public BvdChunk(
            ServerLevel level,
            LevelChunkSection[] sections,
            CompoundTag heightmapsTag,
            byte[][] blockLight,
            byte @Nullable [][] skyLight
    ) {
        this.level = level;
        this.sections = sections;
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            NbtIo.writeAnyTag(heightmapsTag, out);
            this.heightmapsTag = out.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Error while writing heightmaps to bytes", exception);
        }
        this.blockLight = blockLight;
        this.skyLight = skyLight;
    }

    public static BvdChunk create(
            ServerLevel level,
            LevelChunkSection[] sections,
            CompoundTag heightmapsTag,
            byte[][] blockLight,
            byte @Nullable [][] skyLight
    ) {
        return new BvdChunk(level, sections, heightmapsTag, blockLight, skyLight);
    }

    private static boolean isEmpty(ChunkAccess chunk) {
        if (chunk instanceof EmptyLevelChunk) {
            return true;
        }
        final LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0, len = sections.length; i < len; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    private static byte[] @Nullable [] nibbleToBytes(final SWMRNibbleArray[] nibbles) {
        final int len = nibbles.length;
        final byte[] @Nullable [] result = new byte[len][];
        for (int i = 0; i < len; i++) {
            SWMRNibbleArray nibble = nibbles[i];
            result[i] = nibble.isInitialisedVisible()
                    ? nibble.storageVisible : null;
        }
        return result;
    }

    public static ByteBuf chunkToBytesOrEmpty(ServerLevel level, ChunkAccess chunk) {
        BvdChunk bvdChunk = wrapChunkOrNull(level, chunk);
        if (bvdChunk == null) {
            return Unpooled.EMPTY_BUFFER;
        }
        return bvdChunk.buildFullPacket(chunk.getPos());
    }

    public static ByteBuf chunkToBytes(ServerLevel level, ChunkAccess chunk) {
        return wrapChunk(level, chunk).buildFullPacket(chunk.getPos());
    }

    public static @Nullable BvdChunk wrapChunkOrNull(ServerLevel level, ChunkAccess chunk) {
        return !isEmpty(chunk) ? wrapChunk(level, chunk) : null;
    }

    public static BvdChunk wrapChunk(ServerLevel level, ChunkAccess chunk) {
        CompoundTag heightmaps = new CompoundTag();
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (!entry.getKey().sendToClient()) {
                continue;
            }
            heightmaps.putLongArray(entry.getKey().getSerializationKey(), entry.getValue().getRawData());
        }

        return new BvdChunk(
                level, chunk.getSections(), heightmaps,
                nibbleToBytes(chunk.starlight$getBlockNibbles()),
                nibbleToBytes(chunk.starlight$getSkyNibbles())
        );
    }

    public ByteBuf buildFullPacket(McChunkPos pos) {
        return BvdUtilities.buildFullChunkPacket(pos, this);
    }

    public ByteBuf buildPacket() {
        return BvdUtilities.buildChunkPacket(this);
    }
}
