package org.lain.engine.client.mixin.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(
            method = "renderFood",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$hideFoodIndicator(GuiGraphics guiGraphics, Player player, int i, int j, CallbackInfo ci) {
        if (!ClientMixinAccess.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderPlayerHealth",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$hideHealthIndicator(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!ClientMixinAccess.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderArmor",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private static void engine$hideArmorIndicator(GuiGraphics guiGraphics, Player player, int i, int j, int k, int l, CallbackInfo ci) {
        if (!ClientMixinAccess.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderHotbarAndDecorations",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"
            )
    )
    public void engine$hideExperienceIndicator(GuiGraphics guiGraphics, Font font, int i) {
        if (ClientMixinAccess.INSTANCE.isHotbarIndicatorsVisible()) {
            ContextualBarRenderer.renderExperienceLevel(guiGraphics, font, i);
        }
    }
}
