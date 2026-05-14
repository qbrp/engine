package org.lain.engine.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void engine$copy(CallbackInfo ci) {
        LocalPlayer entity = (LocalPlayer)(Object) this;
        ClientMixinAccess.INSTANCE.onClientPlayerEntityInitialized(entity);
    }
}
