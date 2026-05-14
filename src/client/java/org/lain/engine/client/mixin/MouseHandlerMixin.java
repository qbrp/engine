package org.lain.engine.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at=@At(value = "HEAD"), cancellable = true)
    public void engine$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle()) {
            ClientMixinAccess mixinAccess = ClientMixinAccess.INSTANCE;
            mixinAccess.onScroll((float) vertical);
            if (!mixinAccess.isScrollAllowed()) {
                ci.cancel();
            }
        }
    }
}

