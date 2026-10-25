package org.lain.engine.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at=@At(value = "HEAD"), cancellable = true)
    public void engine$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow()) {
            ClientMixin mixinAccess = ClientMixin.INSTANCE;
            mixinAccess.onScroll((float) vertical);
            if (!mixinAccess.isScrollAllowed()) {
                ci.cancel();
            }
        }
    }

    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void engine$scaleTurnForZoom(
            LocalPlayer player,
            double deltaYaw,
            double deltaPitch
    ) {
        double zoom = Math.max(1.0, ClientMixin.INSTANCE.getZoom());
        player.turn(deltaYaw / zoom, deltaPitch / zoom);
    }
}

