package dev.booky.betterview.mixin.listener;
// Created by booky10 in BetterView (04:11 05.06.2025)

import dev.booky.betterview.BetterViewMod;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@NullMarked
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void postInit(CallbackInfo ci) {
        BetterViewMod.SERVER = new WeakReference<>((MinecraftServer) (Object) this);
    }

    @Inject(
            method = "tickChildren",
            at = @At("TAIL")
    )
    private void postServerTick(CallbackInfo ci) {
        BetterViewMod.INSTANCE.getManager().runTick();
    }
}
