package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (03:28 19.05.2025)

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class McChunkPos {

    private final int posX;
    private final int posZ;
    private final long key;

    public McChunkPos(int posX, int posZ, long key) {
        this.posX = posX;
        this.posZ = posZ;
        this.key = key;
    }

    public static long getChunkKey(int posX, int posZ) {
        return ((long) posZ << Integer.SIZE) | posX;
    }

    public static int getChunkX(long key) {
        return (int) key;
    }

    public static int getChunkZ(long key) {
        return (int) (key >>> Integer.SIZE);
    }

    public int getPosX() {
        return this.posX;
    }

    public int getPosZ() {
        return this.posZ;
    }

    public long getKey() {
        return this.key;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof McChunkPos that)) return false;
        return this.key == that.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.posX, this.posZ);
    }
}
