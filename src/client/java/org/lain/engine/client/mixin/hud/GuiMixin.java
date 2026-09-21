package org.lain.engine.client.mixin.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.client.mc.ClientMixin;
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
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"
            )
    )
    private void engine$hideCrosshairAttackIndicator(
            GuiGraphics guiGraphics,
            ResourceLocation sprite,
            int x,
            int y,
            int width,
            int height
    ) {
        if (ClientMixin.INSTANCE.isCrosshairAttackIndicatorVisible()
                || sprite.equals(GuiAccessor.engine$getCrosshairTexture())) {
            guiGraphics.blitSprite(sprite, x, y, width, height);
        }
    }

    @Redirect(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"
            )
    )
    private void engine$hideCrosshairAttackProgress(
            GuiGraphics guiGraphics,
            ResourceLocation sprite,
            int textureWidth,
            int textureHeight,
            int u,
            int v,
            int x,
            int y,
            int width,
            int height
    ) {
        if (ClientMixin.INSTANCE.isCrosshairAttackIndicatorVisible()) {
            guiGraphics.blitSprite(sprite, textureWidth, textureHeight, u, v, x, y, width, height);
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void engine$hideFoodIndicator(GuiGraphics guiGraphics, Player player, int y, int right, CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void engine$hideHealthIndicator(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void engine$hideArmorIndicator(
            GuiGraphics guiGraphics,
            Player player,
            int y,
            int rows,
            int rowHeight,
            int left,
            CallbackInfo ci
    ) {
        if (!ClientMixin.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void engine$hideExperienceBar(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void engine$hideExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }
}
