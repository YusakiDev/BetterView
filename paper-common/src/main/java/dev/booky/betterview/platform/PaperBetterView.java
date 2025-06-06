package dev.booky.betterview.platform;
// Created by booky10 in BetterView (15:42 03.06.2025)

import dev.booky.betterview.common.BetterViewManager;
import dev.booky.betterview.common.hooks.BetterViewHook;
import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.hooks.PlayerHook;
import dev.booky.betterview.nms.PaperNmsInterface;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@NullMarked
public class PaperBetterView implements BetterViewHook {

    private final BetterViewManager manager;

    public PaperBetterView(BetterViewManager manager) {
        this.manager = manager;
    }

    @Override
    public long getNanosPerServerTick() {
        return PaperNmsInterface.SERVICE.getNanosPerServerTick();
    }

    @SuppressWarnings("PatternValidation")
    @Override
    public LevelHook constructLevel(String worldName) {
        World world = Bukkit.getWorld(Key.key(worldName));
        if (world == null) {
            throw new IllegalStateException("Can't find level with name " + worldName);
        }
        return new PaperLevel(this.manager, world);
    }

    @Override
    public PlayerHook constructPlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Can't find player with uuid " + playerId);
        }
        return new PaperPlayer(this.manager, player);
    }
}
