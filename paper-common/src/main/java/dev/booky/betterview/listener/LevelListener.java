package dev.booky.betterview.listener;
// Created by booky10 in BetterView (22:32 03.06.2025)

import dev.booky.betterview.common.BetterViewManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class LevelListener implements Listener {

    private final BetterViewManager manager;

    public LevelListener(BetterViewManager manager) {
        this.manager = manager;

        // eagerly load all available dimensions once on startup to allow
        // population of config file - every other dimension gets lazy loaded
        for (World dimension : Bukkit.getWorlds()) {
            this.manager.getLevel(dimension.key());
        }
    }

    @EventHandler
    public void onLevelUnload(WorldUnloadEvent event) {
        this.manager.unregisterLevel(event.getWorld().key());
    }
}
