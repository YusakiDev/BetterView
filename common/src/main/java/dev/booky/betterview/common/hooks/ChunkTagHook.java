package dev.booky.betterview.common.hooks;
// Created by booky10 in BetterView (14:10 03.06.2025)

import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ChunkTagHook {

    boolean isChunkLit();

    ByteBuf toBytesOrEmpty(LevelHook level, McChunkPos pos);
}
