package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:08 03.06.2025)

import dev.booky.betterview.common.config.BvGlobalConfig;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public interface BetterViewHook {

    boolean checkChunkGeneration();

    BvGlobalConfig getConfig();

    long getNanosPerServerTick();

    List<? extends LevelHook> getLevels();

    List<? extends PlayerHook> getPlayers();
}
