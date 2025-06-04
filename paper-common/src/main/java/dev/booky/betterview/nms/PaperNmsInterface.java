package dev.booky.betterview.nms;
// Created by booky10 in BetterView (16:21 03.06.2025)

import dev.booky.betterview.common.BvdManager;
import dev.booky.betterview.common.BvdPlayer;
import dev.booky.betterview.common.util.ChunkTagResult;
import dev.booky.betterview.common.util.McChunkPos;
import dev.booky.betterview.common.util.ServicesUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@NullMarked
public interface PaperNmsInterface {

    PaperNmsInterface SERVICE = ServicesUtil.loadService(PaperNmsInterface.class);

    String BETTERVIEW_HANDLER = "betterview_handler";

    long getNanosPerServerTick();

    int getRequestedViewDistance(Player player);

    McChunkPos getChunkPos(Player player);

    Channel getNettyChannel(Player player);

    Object constructClientboundSetChunkCacheRadiusPacket(int distance);

    ByteBuf getClientboundForgetLevelChunkPacketId();

    ByteBuf getClientboundLevelChunkWithLightPacketId();

    CompletableFuture<@Nullable ByteBuf> getLoadedChunkBuf(World world, McChunkPos chunkPos);

    CompletableFuture<@Nullable ChunkTagResult> readChunkTag(World world, McChunkPos chunkPos);

    CompletableFuture<ByteBuf> loadChunk(World world, int chunkX, int chunkZ);

    boolean checkVoidWorld(World world);

    Object getDimensionId(World world);

    ByteBuf buildEmptyChunkData(World world);

    void injectPacketHandler(BvdManager manager, NamespacedKey listenerKey);

    void uninjectPacketHandler(NamespacedKey listenerKey);

    void saveNetworkPlayer(Channel channel, BvdPlayer bvdPlayer);
}
