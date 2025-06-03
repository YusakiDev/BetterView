package dev.booky.betterview.common.config.serializer;
// Created by booky10 in BetterView (15:54 03.06.2025)

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.function.Predicate;

@NullMarked
public final class DurationSerializer extends ScalarSerializer<Duration> {

    public static final TypeSerializer<Duration> INSTANCE = new DurationSerializer();

    private DurationSerializer() {
        super(Duration.class);
    }

    @Override
    public Duration deserialize(Type type, Object obj) {
        return Duration.parse(String.valueOf(obj));
    }

    @Override
    protected Object serialize(Duration item, Predicate<Class<?>> typeSupported) {
        return String.valueOf(item);
    }
}
