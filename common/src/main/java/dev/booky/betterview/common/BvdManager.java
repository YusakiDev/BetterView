package dev.booky.betterview.common;
// Created by booky10 in BetterView (15:19 03.06.2025)

import dev.booky.betterview.common.BvdPlayer.ChunkQueueEntry;
import dev.booky.betterview.common.hooks.BetterViewHook;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.hooks.PlayerHook;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

// bvd processing logic is done asynchronously on the netty threads
// as this ensures dimension switches don't cause chunks to end up
// in the wrong dimension because of race conditions
@NullMarked
public final class BvdManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("BetterView");

    static final int TICK_LENGTH_DIVISOR = 2;
    static final int CHUNK_QUEUE_SIZE = 16;

    private final BetterViewHook hook;
    private final AtomicInteger generatedChunks = new AtomicInteger(0);

    public BvdManager(BetterViewHook hook) {
        this.hook = hook;
    }

    /*

    public static boolean checkPacket(CraftPacketContext ctx, Packet<?> input) {
        if (input instanceof ClientboundLevelChunkWithLightPacket
                || input instanceof ClientboundForgetLevelChunkPacket
                || input instanceof ClientboundLoginPacket
                || input instanceof ClientboundStartConfigurationPacket
                || input instanceof ClientboundRespawnPacket) {
            CraftPlayer player = (CraftPlayer) ctx.getPlayer();
            BvdPlayer bvd = player != null ? player.getHandle().tjc$bvd : null;
            if (bvd != null && bvd.enabled) {
                switch (input) {
                    case ClientboundLevelChunkWithLightPacket packet ->
                            bvd.serverChunkAdd(packet.getX(), packet.getZ());
                    case ClientboundForgetLevelChunkPacket packet -> {
                        // if the chunk is still in range, cancel the unload packet
                        return bvd.serverChunkRemove(packet.pos());
                    }
                    case ClientboundLoginPacket __ -> bvd.handleDimensionReset(null);
                    case ClientboundStartConfigurationPacket __ -> bvd.handleDimensionReset(null);
                    case ClientboundRespawnPacket packet -> bvd.handleDimensionReset(packet.commonPlayerSpawnInfo().dimension());
                    default -> {
                        // NO-OP
                    }
                }
            }
        }
        return false; // don't cancel packet
    }
     */

    // called on main thread
    public void runTick() {
        BetterViewHook hook = this.hook;
        if (!hook.getConfig().isEnabled()) {
            return; // disabled globally
        }

        List<? extends PlayerHook> players = new ArrayList<>(hook.getPlayers());
        if (players.isEmpty()) {
            return; // no players online
        }
        List<? extends LevelHook> levels = hook.getLevels();

        // reset chunk generation counters
        this.generatedChunks.set(0);
        for (LevelHook level : levels) {
            level.resetChunkGeneration();
        }

        // shuffle players to randomize who gets more/less time this tick
        Collections.shuffle(players);

        // based on how many netty threads exist and how often the server ticks,
        // calculate how much nanos each player gets to tick
        int nettyThreadCount = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        long tickLengthNanos = hook.getNanosPerServerTick() / TICK_LENGTH_DIVISOR;
        long maxTimePerPlayerNanos = (nettyThreadCount * tickLengthNanos) / Math.max(nettyThreadCount, players.size());

        for (PlayerHook player : players) {
            LevelHook level = player.getLevel();
            McChunkPos chunkPos = player.getChunkPos();
            // switch to netty threads for ticking
            player.getNettyChannel().eventLoop().execute(() -> {
                try {
                    long deadline = System.nanoTime() + maxTimePerPlayerNanos;
                    this.tickPlayer(player, level, chunkPos, deadline);
                } catch (Throwable throwable) {
                    LOGGER.error("Error while ticking player {} in level {}", player, level, throwable);
                }
            });
        }
    }

    private void tickPlayer(PlayerHook player, LevelHook level, McChunkPos chunkPos, long deadline) {
        BvdPlayer bvd = player.getBvdPlayer();

        // tick player movement
        bvd.move(level, chunkPos);

        if (!bvd.preTick()) {
            return; // don't tick player
        }

        // start processing chunks (process at least once)
        int chunksPerTick = this.hook.getConfig().getChunkSendLimit();
        do {
            // check if any chunks are built and ready for sending
            bvd.chunkQueue.removeIf(bvd::checkQueueEntry);

            if (bvd.chunkQueue.size() >= CHUNK_QUEUE_SIZE) {
                break; // fail-safe to prevent OOM because of too long queue
            }

            McChunkPos nextChunk = bvd.pollChunkPos();
            if (nextChunk == null) {
                break; // nothing left to process, player can see everything
            }

            // start building chunk queue and add to processing queue
            CompletableFuture<@Nullable ByteBuf> future = level.getBvdCache().get(nextChunk).get();
            bvd.chunkQueue.add(new ChunkQueueEntry(nextChunk, future));

            // check if a limit has been reached
            if (chunksPerTick-- <= 0) {
                break;
            }
        } while (deadline > System.nanoTime());
    }

    public AtomicInteger getGeneratedChunks() {
        return this.generatedChunks;
    }
}
