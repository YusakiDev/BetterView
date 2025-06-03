package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:09 03.06.2025)

import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ChunkHook {

    ByteBuf toBytesOrEmpty();
}
