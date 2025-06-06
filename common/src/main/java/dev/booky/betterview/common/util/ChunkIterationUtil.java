package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (15:00 03.06.2025)

import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@NullMarked
public class ChunkIterationUtil {

    public static final int MAX_CHUNK_DISTANCE = 128;

    public static final long[][] RADIUS_ITERATION_LIST = new long[MAX_CHUNK_DISTANCE + 2 + 1][];

    static {
        // inspired by how Moonrise's ParallelSearchRadiusIteration works to make this more FAF (fast-as-fuck)
        for (int radius = 0; radius < RADIUS_ITERATION_LIST.length; radius++) {
            int fradius = radius;
            List<Integer> ints = IntStream.rangeClosed(-radius, radius).boxed().toList();
            RADIUS_ITERATION_LIST[radius] = ints.stream()
                    .flatMap(chunkX -> ints.stream()
                            .map(chunkZ -> new McChunkPos(chunkX, chunkZ)))
                    .filter(pos -> BetterViewUtil.isWithinRange(
                            pos.getX(), pos.getZ(), fradius))
                    .sorted(Comparator.comparingInt(p -> p.getX() * p.getX() + p.getZ() * p.getZ()))
                    .mapToLong(McChunkPos::getKey)
                    .toArray();
        }
    }
}
