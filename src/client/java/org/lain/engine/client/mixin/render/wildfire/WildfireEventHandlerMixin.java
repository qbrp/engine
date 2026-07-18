package org.lain.engine.client.mixin.render.wildfire;

import com.wildfire.main.WildfireEventHandler;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WildfireEventHandler.class)
public class WildfireEventHandlerMixin {
    @Redirect(
            method = "clientJoin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;addToast(Lnet/minecraft/client/gui/components/toasts/Toast;)V"
            )
    )
    private static void engine$disableToast(ToastManager instance, Toast toast) {}
}
