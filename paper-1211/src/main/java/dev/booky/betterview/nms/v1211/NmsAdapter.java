package dev.booky.betterview.nms.v1211;
// Created by booky10 in BetterView (16:37 03.06.2025)

import dev.booky.betterview.nms.PaperNmsInterface;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class NmsAdapter implements PaperNmsInterface {

    public NmsAdapter() {
        if (SharedConstants.getProtocolVersion() != 767) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public long getNanosPerServerTick() {
        return MinecraftServer.getServer().tickRateManager().nanosecondsPerTick();
    }

    @Override
    public int getRequestedViewDistance(Player player) {
        return ((CraftPlayer) player).getHandle().requestedViewDistance();
    }

    @Override
    public Object constructClientboundSetChunkCacheRadiusPacket(int distance) {
        return new ClientboundSetChunkCacheRadiusPacket(distance);
    }
}
