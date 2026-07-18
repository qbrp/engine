package org.lain.engine.mixin;

import net.minecraft.server.MinecraftServer;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(
            method = "processPacketsAndTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V"
            )
    )
    private void engine$processPacketsAndTick(CallbackInfo ci) {
        ServerMixinAccess.INSTANCE.onProcessPackets();
    }
}
