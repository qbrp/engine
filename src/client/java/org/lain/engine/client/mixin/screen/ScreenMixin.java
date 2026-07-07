package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
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
}
