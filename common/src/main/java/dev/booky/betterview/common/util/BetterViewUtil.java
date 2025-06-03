package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (15:15 03.06.2025)

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.booky.betterview.common.BvdCacheEntry;
import dev.booky.betterview.common.hooks.LevelHook;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BetterViewUtil {

    public static final int MC_MAX_DIMENSION_SIZE = 30_000_000;
    public static final int MC_MAX_DIMENSION_SIZE_CHUNKS = MC_MAX_DIMENSION_SIZE << 4;

    private BetterViewUtil() {
    }

    public static LoadingCache<McChunkPos, BvdCacheEntry> buildCache(LevelHook level) {
        return Caffeine.newBuilder()
                .expireAfterWrite(level.getConfig().getCacheDuration())
                .<McChunkPos, BvdCacheEntry>removalListener((key, val, cause) -> {
                    if (val != null) {
                        val.release();
                    }
                })
                .build(pos -> new BvdCacheEntry(level, pos));
    }

    public static boolean isWithinRange(int posX, int posZ, int viewDistance) {
        int absX = Math.abs(posX);
        int absZ = Math.abs(posZ);
        // check done by extra moonrise logic
        int squareDist = Math.max(absX, absZ);
        if (squareDist > viewDistance + 1) {
            return false; // outside square distance
        }
        // check done by vanilla server logic
        long distX = Math.max(0, absX - 2);
        long distZ = Math.max(0, absZ - 2);
        long distXZSqrt = distX * distX + distZ * distZ;
        int viewDistanceSqrt = viewDistance * viewDistance;
        return distXZSqrt < viewDistanceSqrt; // outside cylindrical distance
    }
}
