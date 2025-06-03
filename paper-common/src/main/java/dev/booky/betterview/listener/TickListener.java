package dev.booky.betterview.listener;
// Created by booky10 in BetterView (16:20 03.06.2025)

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.booky.betterview.common.BvdManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TickListener implements Listener {

    private final BvdManager manager;

    public TickListener(BvdManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onTick(ServerTickEndEvent event) {
        this.manager.runTick();
    }
}
