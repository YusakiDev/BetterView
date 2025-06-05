package dev.booky.betterview.mixin.listener;
// Created by booky10 in BetterView (04:52 05.06.2025)

import com.llamalad7.mixinextras.sugar.Local;
import dev.booky.betterview.BetterViewMod;
import dev.booky.betterview.common.BvdManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@NullMarked
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    shift = At.Shift.AFTER
            )
    )
    private void postPlayerAdd(CallbackInfo ci, @Local(argsOnly = true) ServerPlayer player) {
        BvdManager manager = BetterViewMod.INSTANCE.getManager();
        manager.getPlayer(player.getUUID()); // load player
    }

    @Inject(
            method = "respawn",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    shift = At.Shift.AFTER
            )
    )
    private void postPlayerRespawn(CallbackInfoReturnable<ServerPlayer> ci, @Local(ordinal = 1) ServerPlayer player) {
        // unregister player with this uuid and register again to handle respawning
        BvdManager manager = BetterViewMod.INSTANCE.getManager();
        manager.unregisterPlayer(player.getUUID());
        manager.getPlayer(player.getUUID()); // load new player instance
    }

    @Inject(
            method = "remove",
            at = @At("TAIL")
    )
    private void postPlayerRemoval(ServerPlayer player, CallbackInfo ci) {
        BetterViewMod.INSTANCE.getManager().unregisterPlayer(player.getUUID());
    }
}
