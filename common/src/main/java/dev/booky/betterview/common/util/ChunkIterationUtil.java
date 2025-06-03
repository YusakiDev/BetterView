package dev.booky.betterview.common.util;
// Created by booky10 in BetterView (15:00 03.06.2025)

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Objects;

@NullMarked
public class ChunkIterationUtil {

    public static final int MAX_CHUNK_DISTANCE = 128;

    public static final long[][] BVD_RADIUS_ITERATION_LIST = new long[MAX_CHUNK_DISTANCE + 2 + 1][];

    static {
        // uses Moonrise's ParallelSearchRadiusIteration generation to make this more FAF (fast-as-fuck)
        for (int radius = 0; radius < BVD_RADIUS_ITERATION_LIST.length; radius++) {
            int finalRadius = radius;
            BVD_RADIUS_ITERATION_LIST[radius] = Arrays.stream(generateBFSOrder(radius))
                    .mapToObj(McChunkPos::new)
                    .filter(pos -> BetterViewUtil.isWithinRange(
                            pos.getX(), pos.getZ(), finalRadius))
                    // .sorted(Comparator.comparingInt(p -> p.x * p.x + p.z * p.z))
                    .mapToLong(McChunkPos::getKey)
                    .toArray();
        }
    }

    // copied from https://github.com/Tuinity/Moonrise/blob/b15e8398e78ae10a4a92e02f3f2c332fbc278797/src/main/java/ca/spottedleaf/moonrise/patches/chunk_system/util/ParallelSearchRadiusIteration.java#L29-L320
    // licensed under the terms of GPL, which is the only reason we are also GPL
    // the only modifications made are replacing calls to "CoordinateUtils" with our own calls to "McChunkPos"

    private static class CustomLongArray extends LongArrayList {

        public CustomLongArray() {
            super();
        }

        public CustomLongArray(final int expected) {
            super(expected);
        }

        public boolean addAll(final CustomLongArray list) {
            this.addElements(this.size, list.a, 0, list.size);
            return list.size != 0;
        }

        public void addUnchecked(final long value) {
            this.a[this.size++] = value;
        }

        public void forceSize(final int to) {
            this.size = to;
        }

        @Override
        public int hashCode() {
            long h = 1L;

            Objects.checkFromToIndex(0, this.size, this.a.length);

            for (int i = 0; i < this.size; ++i) {
                h = HashCommon.mix(h + this.a[i]);
            }

            return (int) h;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof CustomLongArray other)) {
                return false;
            }

            return this.size == other.size && Arrays.equals(this.a, 0, this.size, other.a, 0, this.size);
        }
    }

    private static int getDistanceSize(final int radius, final int max) {
        if (radius == 0) {
            return 1;
        }
        final int diff = radius - max;
        if (diff <= 0) {
            return 4 * radius;
        }
        return 4 * (max - Math.max(0, diff - 1));
    }

    private static int getQ1DistanceSize(final int radius, final int max) {
        if (radius == 0) {
            return 1;
        }
        final int diff = radius - max;
        if (diff <= 0) {
            return radius + 1;
        }
        return max - diff + 1;
    }

    private static final class BasicFIFOLQueue {

        private final long[] values;
        private int head, tail;

        public BasicFIFOLQueue(final int cap) {
            if (cap <= 1) {
                throw new IllegalArgumentException();
            }
            this.values = new long[cap];
        }

        public boolean isEmpty() {
            return this.head == this.tail;
        }

        public long removeFirst() {
            final long ret = this.values[this.head];

            if (this.head == this.tail) {
                throw new IllegalStateException();
            }

            ++this.head;
            if (this.head == this.values.length) {
                this.head = 0;
            }

            return ret;
        }

        public void addLast(final long value) {
            this.values[this.tail++] = value;

            if (this.tail == this.head) {
                throw new IllegalStateException();
            }

            if (this.tail == this.values.length) {
                this.tail = 0;
            }
        }
    }

    private static CustomLongArray[] makeQ1BFS(final int radius) {
        final CustomLongArray[] ret = new CustomLongArray[2 * radius + 1];
        final BasicFIFOLQueue queue = new BasicFIFOLQueue(Math.max(1, 4 * radius) + 1);
        final LongOpenHashSet seen = new LongOpenHashSet((radius + 1) * (radius + 1));

        seen.add(McChunkPos.getChunkKey(0, 0));
        queue.addLast(McChunkPos.getChunkKey(0, 0));
        while (!queue.isEmpty()) {
            final long chunk = queue.removeFirst();
            final int chunkX = McChunkPos.getChunkX(chunk);
            final int chunkZ = McChunkPos.getChunkZ(chunk);

            final int index = Math.abs(chunkX) + Math.abs(chunkZ);
            final CustomLongArray list = ret[index];
            if (list != null) {
                list.addUnchecked(chunk);
            } else {
                (ret[index] = new CustomLongArray(getQ1DistanceSize(index, radius))).addUnchecked(chunk);
            }

            for (int i = 0; i < 4; ++i) {
                // 0 -> -1, 0
                // 1 -> 0, -1
                // 2 -> 1, 0
                // 3 -> 0, 1

                final int signInv = -(i >>> 1); // 2/3 -> -(1), 0/1 -> -(0)
                // note: -n = (~n) + 1
                // (n ^ signInv) - signInv = signInv == 0 ? ((n ^ 0) - 0 = n) : ((n ^ -1) - (-1) = ~n + 1)

                final int axis = i & 1; // 0/2 -> 0, 1/3 -> 1
                final int dx = ((axis - 1) ^ signInv) - signInv; // 0 -> -1, 1 -> 0
                final int dz = (-axis ^ signInv) - signInv; // 0 -> 0, 1 -> -1

                final int neighbourX = chunkX + dx;
                final int neighbourZ = chunkZ + dz;
                final long neighbour = McChunkPos.getChunkKey(neighbourX, neighbourZ);

                if ((neighbourX | neighbourZ) < 0 || Math.max(Math.abs(neighbourX), Math.abs(neighbourZ)) > radius) {
                    // don't enqueue out of range
                    continue;
                }

                if (!seen.add(neighbour)) {
                    continue;
                }

                queue.addLast(neighbour);
            }
        }

        return ret;
    }

    // doesn't appear worth optimising this function now, even though it's 70% of the call
    private static CustomLongArray spread(final CustomLongArray input, final int size) {
        final LongLinkedOpenHashSet notAdded = new LongLinkedOpenHashSet(input);
        final CustomLongArray added = new CustomLongArray(size);

        while (!notAdded.isEmpty()) {
            if (added.isEmpty()) {
                added.addUnchecked(notAdded.removeLastLong());
                continue;
            }

            long maxChunk = -1L;
            int maxDist = 0;

            // select the chunk from the not yet added set that has the largest minimum distance from
            // the current set of added chunks

            for (final LongIterator iterator = notAdded.iterator(); iterator.hasNext(); ) {
                final long chunkKey = iterator.nextLong();
                final int chunkX = McChunkPos.getChunkX(chunkKey);
                final int chunkZ = McChunkPos.getChunkZ(chunkKey);

                int minDist = Integer.MAX_VALUE;

                final int len = added.size();
                final long[] addedArr = added.elements();
                Objects.checkFromToIndex(0, len, addedArr.length);
                for (int i = 0; i < len; ++i) {
                    final long addedKey = addedArr[i];
                    final int addedX = McChunkPos.getChunkX(addedKey);
                    final int addedZ = McChunkPos.getChunkZ(addedKey);

                    // here we use square distance because chunk generation uses neighbours in a square radius
                    final int dist = Math.max(Math.abs(addedX - chunkX), Math.abs(addedZ - chunkZ));

                    minDist = Math.min(dist, minDist);
                }

                if (minDist > maxDist) {
                    maxDist = minDist;
                    maxChunk = chunkKey;
                }
            }

            // move the selected chunk from the not added set to the added set

            if (!notAdded.remove(maxChunk)) {
                throw new IllegalStateException();
            }

            added.addUnchecked(maxChunk);
        }

        return added;
    }

    private static void expandQuadrants(final CustomLongArray input, final int size) {
        final int len = input.size();
        final long[] array = input.elements();

        int writeIndex = size - 1;
        for (int i = len - 1; i >= 0; --i) {
            final long key = array[i];
            final int chunkX = McChunkPos.getChunkX(key);
            final int chunkZ = McChunkPos.getChunkZ(key);

            if ((chunkX | chunkZ) < 0 || (i != 0 && chunkX == 0 && chunkZ == 0)) {
                throw new IllegalStateException();
            }

            // Q4
            if (chunkZ != 0) {
                array[writeIndex--] = McChunkPos.getChunkKey(chunkX, -chunkZ);
            }
            // Q3
            if (chunkX != 0 && chunkZ != 0) {
                array[writeIndex--] = McChunkPos.getChunkKey(-chunkX, -chunkZ);
            }
            // Q2
            if (chunkX != 0) {
                array[writeIndex--] = McChunkPos.getChunkKey(-chunkX, chunkZ);
            }

            array[writeIndex--] = key;
        }

        input.forceSize(size);

        if (writeIndex != -1) {
            throw new IllegalStateException();
        }
    }

    private static long[] generateBFSOrder(final int radius) {
        // by using only the first quadrant, we can reduce the total element size by 4 when spreading
        final CustomLongArray[] byDistance = makeQ1BFS(radius);

        // to increase generation parallelism, we want to space the chunks out so that they are not nearby when generating
        // this also means we are minimising locality
        // but, we need to maintain sorted order by manhatten distance

        // per manhatten distance we transform the chunk list so that each element is maximally spaced out from each other
        for (int i = 0, len = byDistance.length; i < len; ++i) {
            final CustomLongArray points = byDistance[i];
            final int expectedSize = getDistanceSize(i, radius);

            final CustomLongArray spread = spread(points, expectedSize);
            // add in Q2, Q3, Q4
            expandQuadrants(spread, expectedSize);

            byDistance[i] = spread;
        }

        // now, rebuild the list so that it still maintains manhatten distance order
        final CustomLongArray ret = new CustomLongArray((2 * radius + 1) * (2 * radius + 1));

        for (final CustomLongArray dist : byDistance) {
            ret.addAll(dist);
        }

        return ret.elements();
    }
}
