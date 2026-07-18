package org.lain.engine.client.mixin.render.wildfire;

import com.wildfire.render.GenderRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GenderRenderState.class, remap = false)
public class GenderRenderStateMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isUnderWater()Z"
            )
    )
    private boolean engine$allowNullEntityForBreathing(LivingEntity entity) {
        return entity != null && entity.isUnderWater();
    }
}