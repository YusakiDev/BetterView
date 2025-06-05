package dev.booky.betterview.nms;
// Created by booky10 in BetterView (16:49 03.06.2025)

import org.jspecify.annotations.NullMarked;

@NullMarked
public class BypassedPacket {

    private final Object packet;

    public BypassedPacket(Object packet) {
        this.packet = packet;
    }

    public Object getPacket() {
        return this.packet;
    }
}
