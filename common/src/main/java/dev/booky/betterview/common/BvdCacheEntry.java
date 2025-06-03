package dev.booky.betterview.common;
// Created by booky10 in TJCServer (21:07 12.05.2025)

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import de.tjcserver.server.TJCServerConfig;
import dev.booky.betterview.common.hooks.ChunkHook;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

// wrap cache value to allow dynamic "invalidation", as the validity of this cache entry
// is only known after asynchronously building the chunk byte buffer
@NullMarked
public final class BvdCacheEntry {

    private final LevelHook level;
    private final McChunkPos pos;

    private volatile @Nullable CompletableFuture<@Nullable ByteBuf> future;

    public BvdCacheEntry(LevelHook level, McChunkPos pos) {
        this.level = level;
        this.pos = pos;
    }

    private CompletableFuture<@Nullable ByteBuf> tryGet() {
        LevelHook level = this.level;
        McChunkPos pos = this.pos;

        // load chunk from level cache; I'm not sure whether this is thread-safe or not,
        // but this hasn't caused any issues yet - so I'll just assume it's thread-safe :)
        ChunkHook chunk = level.getCachedChunk(pos.getKey());
        if (chunk != null) {
            return CompletableFuture.completedFuture(BvdChunk.chunkToBytesOrEmpty(level, chunk));
        }

        // read chunk directly from storage; this is passed to an IO thread anyway, so it
        // doesn't matter that this is called off-thread I think
        return BvdUtilities.readChunk(level, pos).thenComposeAsync(chunkTag -> {
            // if the is fully lit (chunk upgrading is done while reading), create chunk data directly
            if (chunkTag != null && BvdUtilities.isChunkLit(chunkTag)) {
                BvdChunk bvdChunk = BvdUtilities.loadChunk(level, pos, chunkTag);
                return CompletableFuture.completedFuture(bvdChunk == null
                        ? Unpooled.EMPTY_BUFFER : bvdChunk.buildFullPacket(pos));
            }

            // skip useless chunk generation if this is a void world
            if (chunkTag == null && level.voidWorld) {
                return CompletableFuture.completedFuture(Unpooled.EMPTY_BUFFER);
            }

            if (BvdManager.GENERATED_CHUNKS.getAndIncrement() > TJCServerConfig.bvdMaxGeneratedChunksTick) {
                return CompletableFuture.completedFuture(null); // global generation limit reached
            } else if (level.bvdGeneratedChunks.getAndIncrement() > level.tjcConfig.bvdMaxGeneratedChunksTick) {
                return CompletableFuture.completedFuture(null); // per-level generation limit reached
            }

            // call moonrise chunk system to generate chunk to LIGHT stage
            CompletableFuture<ByteBuf> future = new CompletableFuture<>();
            PlatformHooks.get().scheduleChunkLoad(level, pos.x, pos.z, true, ChunkStatus.LIGHT, true, Priority.LOW,
                    generatedChunk -> future.complete(BvdChunk.chunkToBytesOrEmpty(level, generatedChunk)));
            return future;
        });
    }

    public CompletableFuture<@Nullable ByteBuf> get() {
        // all of this code runs synchronized on this cache entry to
        synchronized (this) {
            {
                CompletableFuture<@Nullable ByteBuf> future = this.future;
                if (future != null) {
                    // future is valid, return immediately
                    return future;
                }
            }
            CompletableFuture<@Nullable ByteBuf> future = CompletableFuture.completedFuture(null)
                    // launch chunk handling asynchronously to minimize lock time
                    .thenComposeAsync(__ -> this.tryGet())
                    .thenApply(buf -> {
                        if (buf == null) {
                            // can't cause deadlock as this is run asynchronously
                            synchronized (this) {
                                this.future = null;
                            }
                        }
                        return buf;
                    });
            // mark the running future as valid; if chunk generation fails,
            // it will be automatically marked as invalid after it's done
            this.future = future;
            return future;
        }
    }

    public void release() {
        synchronized (this) {
            CompletableFuture<@Nullable ByteBuf> future = this.future;
            if (future != null) {
                future.thenAccept(ReferenceCountUtil::release);
            }
        }
    }
}
