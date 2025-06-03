package dev.booky.betterview.listener;
// Created by booky10 in BetterView (22:32 03.06.2025)

import dev.booky.betterview.common.BvdManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class LevelListener implements Listener {

    private final BvdManager manager;

    public LevelListener(BvdManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onLevelUnload(WorldUnloadEvent event) {
        this.manager.unregisterLevel(event.getWorld().key());
    }
}
