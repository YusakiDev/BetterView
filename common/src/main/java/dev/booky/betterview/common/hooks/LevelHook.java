package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:08 03.06.2025)

import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.booky.betterview.common.BvdCacheEntry;
import dev.booky.betterview.common.config.BvLevelConfig;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@NullMarked
public interface LevelHook {

    @Nullable ChunkHook getCachedChunk(long chunkKey);

    CompletableFuture<@Nullable ChunkTagHook> readChunk(McChunkPos chunkPos);

    // PlatformHooks.get().scheduleChunkLoad(level, pos.x, pos.z, true, ChunkStatus.LIGHT, true, Priority.LOW,
    //         generatedChunk -> future.complete(BvdChunk.chunkToBytesOrEmpty(level, generatedChunk)));
    void loadChunk(int chunkX, int chunkZ, Consumer<ChunkHook> onComplete);

    // if (BvdManager.GENERATED_CHUNKS.getAndIncrement() > TJCServerConfig.bvdMaxGeneratedChunksTick) {
    // } else if (level.bvdGeneratedChunks.getAndIncrement() > level.tjcConfig.bvdMaxGeneratedChunksTick) {
    boolean checkChunkGeneration();

    void resetChunkGeneration();

    ByteBuf getEmptyChunkBuf(McChunkPos chunkPos);

    boolean isVoidWorld();

    // ResourceKey<Level>
    Object dimension();

    int getBetterViewDistance();

    BvLevelConfig getConfig();

    LoadingCache<McChunkPos, BvdCacheEntry> getBvdCache();
}
