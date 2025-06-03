package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (14:10 03.06.2025)

import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ChunkTagResult(@Nullable ByteBuf buffer) {

    // gets returned when the chunk does exist, but can't be sent yet
    public static final ChunkTagResult EMPTY = new ChunkTagResult(null);
}
