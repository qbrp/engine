package org.lain.engine.client.mixin.ui;

import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.world.ClientChunkLoadProgress;
import net.minecraft.text.Text;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class DownloadingTerrainScreenMixin {
    @Shadow public abstract void close();

    @Shadow private ClientChunkLoadProgress chunkLoadProgress;

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            ),
            index = 1
    )
    private Text engine$modifyDisplayedText(net.minecraft.text.Text text) {
        if (!chunkLoadProgress.isDone()) {
            return text;
        } else {
            return Text.of("Подготовка Engine...");
        }
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/world/ClientChunkLoadProgress;isDone()Z"
            ),
            cancellable = true
    )
    private void engine$tick(CallbackInfo ci) {
         if (ClientMixinAccess.INSTANCE.isEngineLoaded()) {
             close();
         }
        ci.cancel();
    }
}