package org.lain.engine.client.mixin;

import net.minecraft.client.toast.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class ToastManagerMixin {
    @Inject(
            method = "add",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$add(Toast toast, CallbackInfo ci) {
        if (toast instanceof SystemToast && toast.getType() == SystemToast.Type.UNSECURE_SERVER_WARNING) {
            ci.cancel();
        } else if (toast instanceof AdvancementToast) {
            ci.cancel();
        } else if (toast instanceof RecipeToast) {
            ci.cancel();
        }
    }
}
