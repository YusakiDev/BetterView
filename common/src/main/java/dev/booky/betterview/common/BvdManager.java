package dev.booky.betterview.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import de.tjcserver.server.TJCServerConfig;
import de.tjcserver.server.bvd.BvdPlayer.ChunkQueueEntry;
import de.tjcserver.server.packets.CraftPacketContext;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// bvd processing logic is done asynchronously on the netty threads
// as this ensures dimension switches don't cause chunks to end up
// in the wrong dimension because of race conditions
@NullMarked
public final class BvdManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("BetterViewDistance");

    static final int TICK_LENGTH_DIVISOR = 2;
    static final int CHUNK_QUEUE_SIZE = 16;
    static final AtomicInteger GENERATED_CHUNKS = new AtomicInteger(0);

    private BvdManager() {
    }

    public static LoadingCache<McChunkPos, BvdCacheEntry> buildCache(LevelHook level) {
        return Caffeine.newBuilder()
                .expireAfterWrite(level.tjcConfig.bvdCacheTime, TimeUnit.MINUTES)
                .<McChunkPos, BvdCacheEntry>removalListener((key, val, cause) -> {
                    if (val != null) {
                        val.release();
                    }
                })
                .build(pos -> new BvdCacheEntry(level, pos));
    }

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
                    case ClientboundLoginPacket __ -> bvd.handleDimensionReset();
                    case ClientboundStartConfigurationPacket __ -> bvd.handleDimensionReset();
                    case ClientboundRespawnPacket packet -> {
                        ResourceKey<Level> dimension = packet.commonPlayerSpawnInfo().dimension();
                        if (!bvd.networkDimension.equals(dimension)) {
                            bvd.networkDimension = dimension;
                            bvd.handleDimensionReset();
                        }
                    }
                    default -> {
                        // NO-OP
                    }
                }
            }
        }
        return false; // don't cancel packet
    }

    // called on main thread
    public static void runTick(MinecraftServer server) {
        // reset chunk generation counters
        GENERATED_CHUNKS.set(0);
        for (ServerLevel level : server.getAllLevels()) {
            level.bvdGeneratedChunks.set(0);
        }

        if (!TJCServerConfig.bvdEnabled) {
            return; // bvd disabled globally
        }
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().players);
        if (players.isEmpty()) {
            return; // no players online
        }
        // shuffle players to randomize who gets chunks sent this tick
        Collections.shuffle(players);

        // based on how many netty threads exist and how often the server ticks,
        // calculate how much nanos each player gets to tick
        int nettyThreadCount = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        long tickLengthNanos = server.tickRateManager().nanosecondsPerTick() / TICK_LENGTH_DIVISOR;
        long maxTimePerPlayerNanos = (nettyThreadCount * tickLengthNanos) / Math.max(nettyThreadCount, players.size());

        for (ServerPlayer player : players) {
            ServerLevel level = player.serverLevel();
            ChunkPos chunkPos = player.chunkPosition();
            // switch to netty threads for ticking
            Channel channel = player.connection.connection.channel;
            channel.eventLoop().execute(() -> {
                try {
                    long deadline = System.nanoTime() + maxTimePerPlayerNanos;
                    tickPlayer(player, level, chunkPos, deadline);
                } catch (Throwable throwable) {
                    LOGGER.error("Error while ticking player {} in level {}", player, level, throwable);
                }
            });
        }
    }

    private static void tickPlayer(ServerPlayer player, ServerLevel level, ChunkPos chunkPos, long deadline) {
        BvdPlayer bvd = player.tjc$bvd;

        // tick player movement
        bvd.move(level, chunkPos);

        if (!bvd.preTick()) {
            return; // don't tick player
        }

        // start processing chunks (process at least once)
        int chunksPerTick = TJCServerConfig.bvdMaxChunksSendPerTick;
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
            CompletableFuture<@Nullable ByteBuf> future = level.bvdCache.get(nextChunk).get();
            bvd.chunkQueue.add(new ChunkQueueEntry(nextChunk, future));

            // check if a limit has been reached
            if (chunksPerTick-- <= 0) {
                break;
            }
        } while (deadline > System.nanoTime());
    }
}
