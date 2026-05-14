package org.lain.engine.client.mixin.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public class GuiMixin {
    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
            )
    )
    public void engine$hideCrosshairAttackIndicator(GuiGraphics instance, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        if (ClientMixinAccess.INSTANCE.isCrosshairAttackIndicatorVisible() || sprite.getPath() == GuiAccessor.engine$getCrosshairTexture().getPath()) {
            instance.blitSprite(pipeline, sprite, x, y, width, height);
        }
    }

    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"
            )
    )
    public void engine$hideCrosshairAttackIndicator2(GuiGraphics instance, RenderPipeline pipeline, Identifier sprite, int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height) {
        if (ClientMixinAccess.INSTANCE.isCrosshairAttackIndicatorVisible() || sprite.getPath() == GuiAccessor.engine$getCrosshairTexture().getPath()) {
            instance.blitSprite(pipeline, sprite, textureWidth, textureHeight, x, y, u, v, width, height);
        }
    }
}
