package org.lain.engine.client.mixin.render;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"
            )
    )
    public void engine$onSetProjectionMatrix(DeltaTracker deltaTracker, CallbackInfo ci) {
        ClientMixin.INSTANCE.onSetWorldProjectionMatrix();
    }
}
