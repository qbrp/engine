package org.lain.engine.client.mixin.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lain.engine.client.render.ui.MovingWallpapers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Unique
    private boolean blurredBackground = false;

    // Причина добавления: ServerHandler не тикается при паузе в одиночной игре, т.к. интегрированный сервер ставится на паузу
    @Inject(
            method = "isPauseScreen",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$cancelIsPauseScreen(CallbackInfoReturnable<Boolean> cir) {
        if (!(((Screen)(Object)this) instanceof PauseScreen)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(
            method = "renderPanorama",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$renderMovingBackground(GuiGraphics guiGraphics, float delta, CallbackInfo ci) {
        if (!(((Screen)(Object)this) instanceof TitleScreen)) {
            MovingWallpapers.INSTANCE.render(guiGraphics, delta);
            blurredBackground = true;
            ci.cancel();
        } else {
            blurredBackground = false;
        }
    }

    @Inject(
            method = "renderBlurredBackground",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$fixBlurTwiceRenderCrash(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (blurredBackground) {
            ci.cancel();
        }
    }
}
