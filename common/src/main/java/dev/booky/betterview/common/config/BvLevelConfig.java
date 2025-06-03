package dev.booky.betterview.common.config;
// Created by booky10 in BetterView (14:49 03.06.2025)

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.time.Duration;

@NullMarked
@ConfigSerializable
public class BvLevelConfig {

    @Setting("Whether the extended view distance is enabled for this dimension or not")
    private boolean enabled = true;
    @Setting("How many new chunks can be generated for this level in one tick")
    private int chunkGenerationLimit = 2;
    @Setting("The maximum extended view distance for this level")
    private int viewDistance = 32;
    @Setting("The cache duration for all extended chunks, after which they will be re-build")
    private Duration cacheDuration = Duration.ofMinutes(5L);

    private BvLevelConfig() {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getChunkGenerationLimit() {
        return this.chunkGenerationLimit;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public Duration getCacheDuration() {
        return this.cacheDuration;
    }
}
