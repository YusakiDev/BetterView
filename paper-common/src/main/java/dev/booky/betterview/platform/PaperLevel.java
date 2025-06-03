package dev.booky.betterview.platform;
// Created by booky10 in BetterView (16:27 03.06.2025)

import dev.booky.betterview.common.util.ChunkTagResult;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.util.McChunkPos;
import dev.booky.betterview.nms.PaperNmsInterface;
import io.netty.buffer.ByteBuf;
import org.bukkit.World;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class PaperLevel implements LevelHook {

    private final World world;

    @Override
    public @Nullable ByteBuf getCachedChunkBuf(McChunkPos chunkPos) {
        return PaperNmsInterface.SERVICE.getLoadedChunkBuf(this.world, chunkPos);
    }

    @Override
    public CompletableFuture<@Nullable ChunkTagResult> readChunk(McChunkPos chunkPos) {
        return PaperNmsInterface.SERVICE.readChunkTag(this.world, chunkPos);
    }
}
