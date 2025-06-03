package dev.booky.betterview.nms.v1211;
// Created by booky10 in BetterView (16:37 03.06.2025)

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import dev.booky.betterview.common.util.ChunkTagResult;
import dev.booky.betterview.common.util.McChunkPos;
import dev.booky.betterview.nms.PaperNmsInterface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@NullMarked
public class NmsAdapter implements PaperNmsInterface {

    // magic packet id values
    static final byte FORGET_LEVEL_CHUNK_PACKET_ID = 0x22;
    static final ByteBuf FORGET_LEVEL_CHUNK_PACKET_ID_BUF =
            Unpooled.wrappedBuffer(new byte[]{FORGET_LEVEL_CHUNK_PACKET_ID});
    static final byte LEVEL_CHUNK_WITH_LIGHT_PACKET_ID = 0x28;
    static final ByteBuf LEVEL_CHUNK_WITH_LIGHT_PACKET_ID_BUF =
            Unpooled.wrappedBuffer(new byte[]{LEVEL_CHUNK_WITH_LIGHT_PACKET_ID});

    public NmsAdapter() {
        if (SharedConstants.getProtocolVersion() != 767) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public long getNanosPerServerTick() {
        return MinecraftServer.getServer().tickRateManager().nanosecondsPerTick();
    }

    @Override
    public int getRequestedViewDistance(Player player) {
        return ((CraftPlayer) player).getHandle().requestedViewDistance();
    }

    @Override
    public McChunkPos getChunkPos(Player player) {
        ChunkPos pos = ((CraftPlayer) player).getHandle().chunkPosition();
        return new McChunkPos(pos.x, pos.z);
    }

    @Override
    public Channel getNettyChannel(Player player) {
        return ((CraftPlayer) player).getHandle().connection.connection.channel;
    }

    @Override
    public Object constructClientboundSetChunkCacheRadiusPacket(int distance) {
        return new ClientboundSetChunkCacheRadiusPacket(distance);
    }

    @Override
    public ByteBuf getClientboundForgetLevelChunkPacketId() {
        return FORGET_LEVEL_CHUNK_PACKET_ID_BUF.retainedSlice();
    }

    @Override
    public ByteBuf getClientboundLevelChunkWithLightPacketId() {
        return LEVEL_CHUNK_WITH_LIGHT_PACKET_ID_BUF.retainedSlice();
    }

    @Override
    public @Nullable ByteBuf getLoadedChunkBuf(World world, McChunkPos chunkPos) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        NewChunkHolder holder = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos.getKey());
        if (holder == null) {
            return null;
        }
        ChunkAccess access = holder.getChunkIfPresent(ChunkStatus.LIGHT);
        return access instanceof LevelChunk ? ChunkWriter.writeFullOrEmpty(access) : null;
    }

    @Override
    public CompletableFuture<@Nullable ChunkTagResult> readChunkTag(World world, McChunkPos chunkPos) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        ChunkPos nmsPos = new ChunkPos(chunkPos.getX(), chunkPos.getZ());
        return level.chunkSource.chunkMap.read(nmsPos).thenApply(tag -> {
            if (tag.isEmpty()) {
                return null;
            } else if (!ChunkTagTransformer.isChunkLit(tag.get())) {
                return ChunkTagResult.EMPTY;
            }
            ByteBuf chunkBuf = ChunkTagTransformer.transformToBytesOrEmpty(level, tag.get(), nmsPos);
            return new ChunkTagResult(chunkBuf);
        });
    }
}
