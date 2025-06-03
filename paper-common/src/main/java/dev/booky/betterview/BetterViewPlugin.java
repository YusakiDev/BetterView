package dev.booky.betterview;
// Created by booky10 in BetterView (15:40 03.06.2025)

import dev.booky.betterview.common.BvdManager;
import dev.booky.betterview.listener.TickListener;
import dev.booky.betterview.platform.PaperBetterView;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

@NullMarked
public class BetterViewPlugin extends JavaPlugin {

    private final BvdManager manager;

    public BetterViewPlugin() {
        Path configPath = this.getDataPath().resolve("config.yml");
        this.manager = new BvdManager(new PaperBetterView(), configPath);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new TickListener(this.manager), this);

        // run task after server has finished starting
        Bukkit.getScheduler().runTask(this, this.manager::onPostLoad);
    }
}
