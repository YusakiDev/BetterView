package dev.booky.betterview.nms;
// Created by booky10 in BetterView (16:21 03.06.2025)

import dev.booky.betterview.common.util.ChunkTagResult;
import dev.booky.betterview.common.util.McChunkPos;
import dev.booky.betterview.common.util.ServicesUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@NullMarked
public interface PaperNmsInterface {

    PaperNmsInterface SERVICE = ServicesUtil.loadService(PaperNmsInterface.class);

    long getNanosPerServerTick();

    int getRequestedViewDistance(Player player);

    McChunkPos getChunkPos(Player player);

    Channel getNettyChannel(Player player);

    Object constructClientboundSetChunkCacheRadiusPacket(int distance);

    ByteBuf getClientboundForgetLevelChunkPacketId();

    ByteBuf getClientboundLevelChunkWithLightPacketId();

    @Nullable ByteBuf getLoadedChunkBuf(World world, McChunkPos chunkPos);

    CompletableFuture<@Nullable ChunkTagResult> readChunkTag(World world, McChunkPos chunkPos);
}
