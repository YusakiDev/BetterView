package dev.booky.betterview.platform;
// Created by booky10 in BetterView (16:27 03.06.2025)

import dev.booky.betterview.common.hooks.PlayerHook;
import dev.booky.betterview.nms.BypassedPacket;
import dev.booky.betterview.nms.PaperNmsInterface;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PaperPlayer implements PlayerHook {

    private final Player player;

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
        // TODO
    }
}
