package org.lain.engine.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    public void engine$copy(CallbackInfo ci) {
        ClientPlayerEntity entity = (ClientPlayerEntity)(Object) this;
        ClientMixinAccess.INSTANCE.onClientPlayerEntityInitialized(entity);
    }
}
