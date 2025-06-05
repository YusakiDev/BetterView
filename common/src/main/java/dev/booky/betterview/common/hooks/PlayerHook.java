package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:23 03.06.2025)

import dev.booky.betterview.common.BvdPlayer;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.channel.Channel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface PlayerHook {

    LevelHook getLevel();

    McChunkPos getChunkPos();

    int getSendViewDistance();

    int getRequestedViewDistance();

    void sendViewDistancePacket(int distance);

    void sendChunkUnload(int chunkX, int chunkZ);

    Channel getNettyChannel();

    BvdPlayer getBvdPlayer();

    boolean isValid();
}
