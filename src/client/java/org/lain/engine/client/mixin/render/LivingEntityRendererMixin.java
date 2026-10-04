package org.lain.engine.client.mixin.render;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.lain.engine.client.render.ui.character.CharacterPreviewPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$hidePreviewCharacterEntityName(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof CharacterPreviewPlayer) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
