package org.lain.engine.client.mixin.ui;

import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.text.Text;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(DownloadingTerrainScreen.class)
public abstract class DownloadingTerrainScreenMixin {
    @Shadow @Final private BooleanSupplier shouldClose;

    @Shadow public abstract void close();

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            ),
            index = 1
    )
    private Text engine$modifyDisplayedText(net.minecraft.text.Text text) {
        if (!shouldClose.getAsBoolean()) {
            return text;
        } else {
            return Text.of("Подготовка Engine...");
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void engine$tick(CallbackInfo ci) {
         if (ClientMixinAccess.INSTANCE.isEngineLoaded()) {
             close();
         }
        ci.cancel();
    }
}