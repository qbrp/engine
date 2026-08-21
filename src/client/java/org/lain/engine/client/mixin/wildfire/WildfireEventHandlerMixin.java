package org.lain.engine.client.mixin.wildfire;

import com.wildfire.main.WildfireEventHandler;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WildfireEventHandler.class, remap = false)
public class WildfireEventHandlerMixin {
    @Inject(
            method = "onClientTick",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private static void onClientTick(CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isEngineLoaded()) {
            ci.cancel();
        }
    }
}
