package org.lain.engine.mixin;

import net.minecraft.server.MinecraftServer;
import org.lain.engine.mc.ServerMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(
            method = "tickServer",
            at = @At("HEAD")
    )
    private void engine$processPacketsAndTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ServerMixin.INSTANCE.onProcessPackets();
    }
}
