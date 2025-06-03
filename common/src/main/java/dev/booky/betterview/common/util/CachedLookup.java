package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (19:49 03.06.2025)

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

// not thread safe TODO is this even needed
@NullMarked
public class CachedLookup<T, Z> implements Supplier<T> {

    private final Function<Z, T> constructor;
    private @Nullable T value;

    private final Supplier<Z> cacheKeySupplier;
    private @Nullable Z cacheKey;

    public CachedLookup(Function<Z, T> constructor, Supplier<Z> cacheKeySupplier) {
        this.constructor = constructor;
        this.cacheKeySupplier = cacheKeySupplier;
    }

    @Override
    public T get() {
        T value = this.value;
        Z cacheKey = this.cacheKeySupplier.get();
        if (value == null || !Objects.equals(this.cacheKey, cacheKey)) {
            value = this.constructor.apply(cacheKey);
            this.value = value;
            this.cacheKey = cacheKey;
            return value;
        }
        return value;
    }
}
