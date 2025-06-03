package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:08 03.06.2025)

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@NullMarked
public interface BetterViewHook {

    long getNanosPerServerTick();

    LevelHook constructLevel(Key worldName);

    PlayerHook constructPlayer(UUID playerId);
}
