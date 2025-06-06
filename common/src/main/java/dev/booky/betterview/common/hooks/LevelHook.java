package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:08 03.06.2025)

import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.booky.betterview.common.ChunkCacheEntry;
import dev.booky.betterview.common.config.BvLevelConfig;
import dev.booky.betterview.common.util.ChunkTagResult;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@NullMarked
public interface LevelHook {

    CompletableFuture<@Nullable ByteBuf> getCachedChunkBuf(McChunkPos chunkPos);

    CompletableFuture<@Nullable ChunkTagResult> readChunk(McChunkPos chunkPos);

    CompletableFuture<ByteBuf> loadChunk(int chunkX, int chunkZ);

    boolean checkChunkGeneration();

    void resetChunkGeneration();

    ByteBuf getEmptyChunkBuf(McChunkPos chunkPos);

    boolean isVoidWorld();

    Object dimension();

    BvLevelConfig getConfig();

    LoadingCache<McChunkPos, ChunkCacheEntry> getChunkCache();

    Key getName();
}
