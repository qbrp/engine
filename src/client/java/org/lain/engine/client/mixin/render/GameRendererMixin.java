package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.GameRenderer;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(
            method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/systems/ProjectionType;)V")
    )
    public void engine$onSetProjectionMatrix(CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.onSetWorldProjectionMatrix();
    }
}
