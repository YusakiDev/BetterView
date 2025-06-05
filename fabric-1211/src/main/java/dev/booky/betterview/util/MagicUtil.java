package dev.booky.betterview.util;
// Created by booky10 in BetterView (04:08 05.06.2025)

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class MagicUtil {

    // magic packet id values
    public static final byte FORGET_LEVEL_CHUNK_PACKET_ID = 0x21;
    public static final ByteBuf FORGET_LEVEL_CHUNK_PACKET_ID_BUF =
            Unpooled.wrappedBuffer(new byte[]{FORGET_LEVEL_CHUNK_PACKET_ID});
    public static final byte LEVEL_CHUNK_WITH_LIGHT_PACKET_ID = 0x27;
    public static final ByteBuf LEVEL_CHUNK_WITH_LIGHT_PACKET_ID_BUF =
            Unpooled.wrappedBuffer(new byte[]{LEVEL_CHUNK_WITH_LIGHT_PACKET_ID});

    private MagicUtil() {
    }
}
