package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:23 03.06.2025)

import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface PlayerHook {

    LevelHook getLevel();

    McChunkPos getChunkPos();

    // return PlatformHooks.get().getSendViewDistance(this.player);
    int getSendViewDistance();

    // int requestedDistance = this.player.requestedViewDistance();
    int getRequestedViewDistance();

    // this.player.connection.send(new ClientboundSetChunkCacheRadiusPacket(newDistance));
    void sendViewDistancePacket(int distance);

    // long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);
    // ByteBuf unloadBuf = BvdUtilities.buildChunkUnload(chunkKey);
    // this.player.connection.connection.channel.write(unloadBuf);
    void sendChunkUnload(int chunkX, int chunkZ);

    // this.player.connection.connection.channel.write(finalChunkBuf);
    void sendPacketBuf(ByteBuf buf);
}
