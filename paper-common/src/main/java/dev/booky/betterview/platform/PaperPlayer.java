package dev.booky.betterview.platform;
// Created by booky10 in BetterView (16:27 03.06.2025)

import dev.booky.betterview.common.BvdManager;
import dev.booky.betterview.common.BvdPlayer;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.hooks.PlayerHook;
import dev.booky.betterview.common.util.BetterViewUtil;
import dev.booky.betterview.common.util.CachedLookup;
import dev.booky.betterview.common.util.McChunkPos;
import dev.booky.betterview.nms.BypassedPacket;
import dev.booky.betterview.nms.PaperNmsInterface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PaperPlayer implements PlayerHook {

    private final BvdManager manager;
    private final Player player;
    private final BvdPlayer bvdPlayer;

    public PaperPlayer(BvdManager manager, Player player) {
        this.manager = manager;
        this.player = player;
        this.bvdPlayer = new BvdPlayer(this);
    }

    @Override
    public LevelHook getLevel() {
        // don't need to cache this, called once per tick
        return this.manager.getLevel(this.player.getWorld().key());
    }

    @Override
    public McChunkPos getChunkPos() {
        return PaperNmsInterface.SERVICE.getChunkPos(this.player);
    }

    @Override
    public int getSendViewDistance() {
        return this.player.getSendViewDistance();
    }

    @Override
    public int getRequestedViewDistance() {
        return PaperNmsInterface.SERVICE.getRequestedViewDistance(this.player);
    }

    @Override
    public void sendViewDistancePacket(int distance) {
        Object packet = PaperNmsInterface.SERVICE.constructClientboundSetChunkCacheRadiusPacket(distance);
        this.getNettyChannel().write(new BypassedPacket(packet));
    }

    @Override
    public void sendChunkUnload(int chunkX, int chunkZ) {
        ByteBuf packetId = PaperNmsInterface.SERVICE.getClientboundForgetLevelChunkPacketId();
        ByteBuf chunkPos = BetterViewUtil.encodeChunkPos(McChunkPos.getChunkKey(chunkX, chunkZ));
        CompositeByteBuf packetBuf = PooledByteBufAllocator.DEFAULT.compositeBuffer(2)
                .addComponent(true, packetId)
                .addComponent(true, chunkPos);
        this.getNettyChannel().write(new BypassedPacket(packetBuf));
    }

    @Override
    public Channel getNettyChannel() {
        return PaperNmsInterface.SERVICE.getNettyChannel(this.player);
    }

    @Override
    public BvdPlayer getBvdPlayer() {
        return this.bvdPlayer;
    }
}
