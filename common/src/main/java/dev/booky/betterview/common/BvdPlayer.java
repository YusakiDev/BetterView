package dev.booky.betterview.common;

import dev.booky.betterview.common.hooks.LevelHook;
import dev.booky.betterview.common.hooks.PlayerHook;
import dev.booky.betterview.common.util.BetterViewUtil;
import dev.booky.betterview.common.util.ChunkIterationUtil;
import dev.booky.betterview.common.util.McChunkPos;
import io.netty.buffer.ByteBuf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static dev.booky.betterview.common.BvdPlayer.ChunkLifecycle.BVD_LOADED;
import static dev.booky.betterview.common.BvdPlayer.ChunkLifecycle.BVD_QUEUED;
import static dev.booky.betterview.common.BvdPlayer.ChunkLifecycle.SERVER_LOADED;
import static dev.booky.betterview.common.BvdPlayer.ChunkLifecycle.UNLOADED;
import static dev.booky.betterview.common.util.BetterViewUtil.MC_MAX_DIMENSION_SIZE_CHUNKS;

@NullMarked
public final class BvdPlayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BetterViewDistance");

    // only allocate these empty arrays once
    private static final long[] EMPTY_LONG_ARRAY = new long[0];
    private static final BvdPlayer.ChunkState[] EMPTY_CHUNK_STATE_ARRAY = new BvdPlayer.ChunkState[0];

    // the last dimension the player was in; this is used to determine whether
    // the client will discard all chunks or not
    private Object networkDimension;

    // a list of chunk offsets which are ordered correctly and
    // can all be sent - as long as they are in bounds of the worldborder
    public long[] chunksInDistance = EMPTY_LONG_ARRAY;
    // the iteration index in the above list which is kept across ticks
    public int iterationIndex;
    // the current state of chunks, this array-based layout has been inspired by
    // how the vanilla client handles storing chunks
    public ChunkState[] chunkStates = EMPTY_CHUNK_STATE_ARRAY;

    public PlayerHook player;
    public LevelHook level;
    public McChunkPos chunkPos;

    // a queue of chunk packets currently being built asynchronously
    public final Queue<ChunkQueueEntry> chunkQueue = new ArrayDeque<>();

    public int distance;
    public int storageRadius;
    public int storageDiameter;

    public boolean enabled = false;
    public boolean initiated = false;

    public BvdPlayer(PlayerHook player) {
        this.player = player;
        this.level = player.getLevel();
        this.chunkPos = player.getChunkPos();
        this.networkDimension = this.level.dimension();
    }

    private static int calcIndex(int chunkX, int chunkZ, int storageDiameter) {
        return Math.floorMod(chunkX, storageDiameter) * storageDiameter
                + Math.floorMod(chunkZ, storageDiameter);
    }

    private boolean canStore(int chunkX, int chunkZ) {
        return Math.abs(chunkX - this.chunkPos.getX()) <= this.storageRadius
                && Math.abs(chunkZ - this.chunkPos.getZ()) <= this.storageRadius;
    }

    public void replacePlayer(PlayerHook player) {
        this.player = player;
    }

    private boolean canBeActivated(int clientDistance) {
        if (!this.initiated || !this.level.getConfig().isEnabled()) {
            return false;
        }
        if (clientDistance < 1) {
            return false; // no proper options packet has been received yet
        }
        // if moonrise's view distance is enough, don't activate BVD processing
        return this.getServerViewDistance() < clientDistance;
    }

    public void tryTriggerStart() {
        if (!this.initiated) {
            // reset chunk position as it may be invalid when this instance has been created
            this.chunkPos = this.player.getChunkPos();
            this.initiated = true;
        }
    }

    public void serverChunkAdd(int chunkX, int chunkZ) {
        if (!this.canStore(chunkX, chunkZ)) {
            LOGGER.error("Can't store server chunk {} {} state for {} at {} in {} with distance {}",
                    chunkX, chunkZ, this.player, this.chunkPos, this.level, this.distance);
            return;
        }
        int chunkIndex = calcIndex(chunkX, chunkZ, this.storageDiameter);
        ChunkState state = this.chunkStates[chunkIndex];
        if (state.lifecycle == BVD_QUEUED) {
            // remove from queue to prevent bvd from accidentally
            // replacing full vanilla server chunks
            this.purgeQueue(chunkX, chunkZ);
        }
        state.set(chunkX, chunkZ, SERVER_LOADED);
    }

    public boolean serverChunkRemove(int chunkX, int chunkZ) {
        boolean insideCylinder = BetterViewUtil.isWithinRange(
                chunkX - this.chunkPos.getX(),
                chunkZ - this.chunkPos.getZ(),
                this.distance);
        if (!insideCylinder) {
            // if we can still store this chunk pos, mark it as unloaded
            if (this.canStore(chunkX, chunkZ)) {
                int chunkIndex = calcIndex(chunkX, chunkZ, this.storageDiameter);
                this.chunkStates[chunkIndex].set(chunkX, chunkZ, UNLOADED);
            }
            return false; // don't keep loaded
        }
        // remove server-loaded state, as it has basically been unloaded for the server;
        // we shouldn't need to check whether we can store this chunk or not as the
        // storage has a larger size than our cylindrical distance check
        int chunkIndex = calcIndex(chunkX, chunkZ, this.storageDiameter);
        this.chunkStates[chunkIndex].set(chunkX, chunkZ, BVD_LOADED);
        return true; // keep loaded
    }

    public int getServerViewDistance() {
        return this.player.getSendViewDistance();
    }

    public int getClientViewDistance() {
        int requestedDistance = this.player.getRequestedViewDistance();
        int bvdDistance = this.level.getConfig().getViewDistance();
        return Math.min(requestedDistance, bvdDistance);
    }

    public void updateDistance(int newDistance) {
        this.distance = newDistance;
        // vanilla logic
        this.storageRadius = Math.max(2, newDistance) + 3;
        this.storageDiameter = this.storageRadius * 2 + 1;
        // update sorted chunk delta array, thanks moonrise!
        this.chunksInDistance = ChunkIterationUtil.BVD_RADIUS_ITERATION_LIST[newDistance];
        this.iterationIndex = 0;

        // migrate storage array
        ChunkState[] prevStates = this.chunkStates;
        int storageDiameter = this.storageDiameter;
        if (prevStates.length != storageDiameter * storageDiameter) {
            @MonotonicNonNull ChunkState[] newStates = new ChunkState[storageDiameter * storageDiameter];
            for (int i = 0, len = prevStates.length; i < len; i++) {
                ChunkState state = prevStates[i];
                if (!state.hasCoords()) {
                    continue; // skip, uncertain chunk coords
                }
                // ensure the new distance actually allows for us to store this chunk state
                if (this.canStore(state.chunkX, state.chunkZ)) {
                    int chunkIndex = calcIndex(state.chunkX, state.chunkZ, storageDiameter);
                    newStates[chunkIndex] = state;
                }
            }
            // try to re-use as much state objects as possible, but allocate
            // new ones if really needed
            for (int i = 0, len = storageDiameter * storageDiameter; i < len; i++) {
                if (newStates[i] == null) {
                    // construct new states with uncertain chunk coordinates
                    newStates[i] = new ChunkState();
                }
            }
            this.chunkStates = newStates;
        }

        // refresh player client view distance
        this.player.sendViewDistancePacket(newDistance);
    }

    public void move(LevelHook newLevel, McChunkPos newPos) {
        if (newLevel != this.level) {
            this.level = newLevel;
            if (this.enabled) {
                this.handleDimensionReset(null);
            }
            return;
        }

        McChunkPos previousPos = this.chunkPos;
        if (newPos.getKey() == previousPos.getKey()) {
            return; // nothing changed
        }
        this.chunkPos = newPos;

        // reset iteration index to ensure we iterate the chunk positions in the correct order
        this.iterationIndex = 0;

        if (this.enabled) {
            // check whether the player moved more than twice the view distance
            // (meaning all chunks would get discarded)
            if (previousPos.distanceSquared(newPos) > this.distance * this.distance) {
                this.unloadBvdChunks();
                this.disable();
                return; // skip useless unload checks
            }

            // unload chunks which are no longer in range
            int centerX = this.chunkPos.getX();
            int centerZ = this.chunkPos.getZ();
            for (int i = 0, len = this.chunkStates.length; i < len; i++) {
                ChunkState state = this.chunkStates[i];
                if (!state.hasCoords()) {
                    continue; // no coords, ignore
                }
                int chunkX = state.chunkX;
                int chunkZ = state.chunkZ;
                boolean keepLoaded = BetterViewUtil.isWithinRange(chunkX - centerX,
                        chunkZ - centerZ, this.distance);
                if (keepLoaded) {
                    continue; // keep chunk loaded, still within range
                }
                if (state.hasCoords()) {
                    ChunkLifecycle lifecycle = state.lifecycle;
                    // mark as unloaded and discard coords, they will (or already have) become invalid
                    state.set(0, 0, UNLOADED);
                    // only send unload packet if the chunk has actually been sent
                    if (lifecycle == BVD_LOADED) {
                        this.player.sendChunkUnload(chunkX, chunkZ);
                    } else if (lifecycle == BVD_QUEUED) {
                        // remove from queue to prevent it from possibly appearing at the wrong place
                        this.purgeQueue(chunkX, chunkZ);
                    }
                }
            }
        }
    }

    public @Nullable McChunkPos pollChunkPos() {
        int centerX = this.chunkPos.getX();
        int centerZ = this.chunkPos.getZ();
        // carry current array index across ticks and only reset
        // it once the player actually changes the chunk position
        while (this.iterationIndex < this.chunksInDistance.length) {
            long chunkPos = this.chunksInDistance[this.iterationIndex++];
            int chunkX = McChunkPos.getChunkX(chunkPos) + centerX;
            int chunkZ = McChunkPos.getChunkZ(chunkPos) + centerZ;

            int chunkIndex = calcIndex(chunkX, chunkZ, this.storageDiameter);
            ChunkState state = this.chunkStates[chunkIndex];
            if (state.lifecycle != UNLOADED) {
                continue; // already loaded (or queued)
            }

            // ensure the chunk is in the world boundaries; this will likely never occur
            if (chunkX < -MC_MAX_DIMENSION_SIZE_CHUNKS || chunkX > MC_MAX_DIMENSION_SIZE_CHUNKS
                    || chunkZ < -MC_MAX_DIMENSION_SIZE_CHUNKS || chunkZ > MC_MAX_DIMENSION_SIZE_CHUNKS) {
                continue; // out of bounds, next one
            }

            // although the chunk isn't loaded yet, mark it as loaded already
            state.set(chunkX, chunkZ, BVD_QUEUED);
            return new McChunkPos(chunkX, chunkZ);
        }
        // reset iteration index to zero after a full chunk range pass without any valid chunks
        this.iterationIndex = 0;
        return null;
    }

    public void purgeQueue(int chunkX, int chunkZ) {
        this.chunkQueue.removeIf(entry ->
                entry.chunkPos.getX() == chunkX && entry.chunkPos.getZ() == chunkZ);
    }

    public boolean checkQueueEntry(ChunkQueueEntry entry) {
        if (!entry.future.isDone()) {
            return false; // future not done yet, don't remove
        }

        // ensure the chunk state hasn't changed
        McChunkPos chunkPos = entry.chunkPos;
        int chunkIndex = calcIndex(chunkPos.getX(), chunkPos.getZ(), this.storageDiameter);
        ChunkState state = this.chunkStates[chunkIndex];
        if (state.lifecycle != BVD_QUEUED) {
            return true; // chunk no longer queued, remove entry
        }

        // check for errors and log them if available
        if (entry.future.isCompletedExceptionally()) {
            Throwable exception = entry.future.exceptionNow();
            LOGGER.error("Error while building chunk {} for {} in {}",
                    chunkPos, this.player, this.level, exception);
            state.lifecycle = UNLOADED;
            return true;
        }

        ByteBuf chunkBuf = entry.future.getNow(null);
        if (chunkBuf == null) {
            // ran into generation limit, try again later
            state.lifecycle = UNLOADED;
            // reset iteration index to prevent this entry from getting skipped
            // until all other chunks have been loaded
            this.iterationIndex = 0;
            return true;
        }

        // if the chunk buffer is empty, assume it's an empty chunk and
        // re-use the statically built empty chunk buffer
        ByteBuf finalChunkBuf = chunkBuf.isReadable()
                ? chunkBuf.retainedSlice()
                : this.level.getEmptyChunkBuf(chunkPos);
        this.player.getNettyChannel().write(finalChunkBuf);
        state.lifecycle = BVD_LOADED; // mark chunk as loaded by bvd
        return true;
    }

    // it doesn't matter if this method leaves behind lingering chunk coords,
    // as move() should clean up left behind coordinates which are now out-of-bounds
    public void unloadBvdChunks() {
        int centerX = this.chunkPos.getX();
        int centerZ = this.chunkPos.getZ();
        for (long chunkKeyOff : this.chunksInDistance) {
            int chunkX = McChunkPos.getChunkX(chunkKeyOff) + centerX;
            int chunkZ = McChunkPos.getChunkZ(chunkKeyOff) + centerZ;
            int chunkIndex = calcIndex(chunkX, chunkZ, this.storageDiameter);

            ChunkState state = this.chunkStates[chunkIndex];
            ChunkLifecycle lifecycle = state.lifecycle;
            if (lifecycle == BVD_LOADED || lifecycle == BVD_QUEUED) {
                if (lifecycle == BVD_LOADED) {
                    this.player.sendChunkUnload(chunkX, chunkZ);
                }
                state.lifecycle = UNLOADED; // mark as unloaded
            }
        }
        // clear all chunks which are currently queued for sending
        this.chunkQueue.clear();
    }

    public void handleDimensionReset(@Nullable Object networkDimension) {
        if (networkDimension != null) {
            if (this.networkDimension.equals(networkDimension)) {
                return; // nothing to do
            }
            this.networkDimension = networkDimension; // update dimension
        }

        // don't sent any unload packets on bulk unload
        for (int i = 0, len = this.chunkStates.length; i < len; i++) {
            this.chunkStates[i].set(0, 0, UNLOADED);
        }
        this.chunkQueue.clear();
        // disable temporarily
        this.disable();
    }

    /**
     * @return true if this player should be fully ticked
     */
    public boolean preTick() {
        int clientDistance = this.getClientViewDistance();
        if (this.enabled) {
            boolean valid = this.player.isValid();
            if (this.distance == clientDistance && valid) {
                return true; // continue processing as normal
            }
            if (!this.canBeActivated(clientDistance)) {
                // distance is no longer valid
                this.unloadBvdChunks();
                this.disable();
                return false;
            }
            if (!valid) {
                return false; // don't disable, just stop processing for now
            }
            // update distance
            this.updateDistance(clientDistance);
        }
        // check if BVD can be activated for this player
        if (!this.canBeActivated(clientDistance)) {
            return false;
        }
        // enable bvd processing and update the view distance
        this.enable(clientDistance);
        return true;
    }

    public void enable(int clientDistance) {
        if (!this.enabled) {
            this.enabled = true;
            this.updateDistance(clientDistance);
        }
    }

    public void disable() {
        if (!this.enabled) {
            return;
        }
        this.enabled = false;

        // switch back to moonrise view distance
        this.player.sendViewDistancePacket(this.getServerViewDistance());
    }

    public enum ChunkLifecycle {
        UNLOADED,
        SERVER_LOADED,
        BVD_QUEUED,
        BVD_LOADED,
    }

    public static final class ChunkState {

        private int chunkX;
        private int chunkZ;
        private ChunkLifecycle lifecycle = UNLOADED;

        public void set(int chunkX, int chunkZ, ChunkLifecycle lifecycle) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.lifecycle = lifecycle;
        }

        public boolean hasCoords() {
            return this.lifecycle != UNLOADED || this.chunkX != 0 || this.chunkZ != 0;
        }
    }

    public record ChunkQueueEntry(
            McChunkPos chunkPos,
            CompletableFuture<@Nullable ByteBuf> future
    ) {}
}
