package org.lain.engine.client.mixin.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
            )
    )
    public void engine$hideCrosshairAttackIndicator(DrawContext instance, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        if (ClientMixinAccess.INSTANCE.isCrosshairAttackIndicatorVisible() || sprite.getPath() == InGameHudAcessor.engine$getCrosshairTexture().getPath()) {
            instance.drawGuiTexture(pipeline, sprite, x, y, width, height);
        }
    }

    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIII)V"
            )
    )
    public void engine$hideCrosshairAttackIndicator2(DrawContext instance, RenderPipeline pipeline, Identifier sprite, int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height) {
        if (ClientMixinAccess.INSTANCE.isCrosshairAttackIndicatorVisible() || sprite.getPath() == InGameHudAcessor.engine$getCrosshairTexture().getPath()) {
            instance.drawGuiTexture(pipeline, sprite, textureWidth, textureHeight, x, y, u, v, width, height);
        }
    }
}
