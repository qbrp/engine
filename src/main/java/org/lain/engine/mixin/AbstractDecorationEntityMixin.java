package org.lain.engine.mixin;

import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractDecorationEntity.class)
public class AbstractDecorationEntityMixin {
    @Inject(
            method = "canStayAttached",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$canStayAttached(CallbackInfoReturnable<Boolean> cir) {
        if (((Object)this) instanceof ItemFrameEntity) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
