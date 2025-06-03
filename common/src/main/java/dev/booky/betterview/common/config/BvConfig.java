package dev.booky.betterview.common.config;
// Created by booky10 in BetterView (15:47 03.06.2025)

import dev.booky.betterview.common.config.serializer.DurationSerializer;
import dev.booky.betterview.common.config.serializer.KeySerializer;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@NullMarked
@ConfigSerializable
public class BvConfig {

    public static final TypeSerializerCollection SERIALIZERS = TypeSerializerCollection.builder()
            .register(Duration.class, DurationSerializer.INSTANCE)
            .register(Key.class, KeySerializer.INSTANCE)
            .build();

    private BvGlobalConfig global = new BvGlobalConfig();
    private Map<Key, BvLevelConfig> dimensions = new LinkedHashMap<>();

    public BvGlobalConfig getGlobalConfig() {
        return this.global;
    }

    public BvLevelConfig getLevelConfig(Key worldName) {
        return this.dimensions.computeIfAbsent(worldName, __ -> new BvLevelConfig());
    }
}
