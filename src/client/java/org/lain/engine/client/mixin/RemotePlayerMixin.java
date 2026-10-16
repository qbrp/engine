package org.lain.engine.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemotePlayer.class)
public class RemotePlayerMixin {
    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void engine$copy(CallbackInfo ci) {
        RemotePlayer entity = (RemotePlayer)(Object) this;
        ClientMixin.INSTANCE.onClientPlayerEntityInitialized(entity);
    }
}
