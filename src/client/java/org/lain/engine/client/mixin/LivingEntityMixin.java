package org.lain.engine.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(
            method = "getScale",
            at = @At("TAIL")
    )
    private void engine$getRenderScale(CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof AbstractClientPlayer) {
            ClientMixin.INSTANCE.getPlayerRenderNodeScale(((AbstractClientPlayer) (Object) this));
        }
    }
}
