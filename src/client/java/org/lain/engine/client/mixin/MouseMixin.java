package org.lain.engine.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at=@At(value = "HEAD"), cancellable = true)
    public void engine$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()) {
            ClientMixinAccess mixinAccess = ClientMixinAccess.INSTANCE;
            mixinAccess.onScroll((float) vertical);
            if (!mixinAccess.isScrollAllowed()) {
                ci.cancel();
            }
        }
    }
}

