package dev.booky.betterview.common.config.serializer;
// Created by booky10 in BetterView (15:54 03.06.2025)

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.function.Predicate;

@NullMarked
public final class KeySerializer extends ScalarSerializer<Key> {

    public static final TypeSerializer<Key> INSTANCE = new KeySerializer();

    private KeySerializer() {
        super(Key.class);
    }

    @SuppressWarnings("PatternValidation")
    @Override
    public Key deserialize(Type type, Object obj) {
        return Key.key(String.valueOf(obj));
    }

    @Override
    protected Object serialize(Key item, Predicate<Class<?>> typeSupported) {
        return String.valueOf(item);
    }
}
