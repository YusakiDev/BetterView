package dev.booky.betterview.common.config;
// Created by booky10 in BetterView (14:54 03.06.2025)

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@NullMarked
@ConfigSerializable
public class BvGlobalConfig {

    @Setting("Whether the extended view distance is enabled or not")
    private boolean enabled = true;
    @Setting("How many new chunks can be generated globally in one tick")
    private int chunkGenerationLimit = 3;
    @Setting("The maximum amount of chunks sent to a player in a tick")
    private int chunkSendLimit = 3;

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getChunkGenerationLimit() {
        return this.chunkGenerationLimit;
    }

    public int getChunkSendLimit() {
        return this.chunkSendLimit;
    }
}
