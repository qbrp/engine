package org.lain.engine.client.mixin;

import net.minecraft.client.KeyMapping;
import org.lain.engine.client.script.KeyMappingsStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Inject(
            method = "isDown",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$disableIsDown(CallbackInfoReturnable<Boolean> cir) {
        if (KeyMappingsStatus.INSTANCE.getDisabled().contains((KeyMapping)(Object)this)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(
            method = "consumeClick",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$disableConsumeClick(CallbackInfoReturnable<Boolean> cir) {
        if (KeyMappingsStatus.INSTANCE.getDisabled().contains((KeyMapping)(Object)this)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
