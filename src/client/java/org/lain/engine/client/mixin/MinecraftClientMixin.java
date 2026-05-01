package org.lain.engine.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public ClientPlayerEntity player;

    @Shadow protected abstract boolean doAttack();

    @Inject(
            method = "handleBlockBreaking",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        ClientMixinAccess mixinAccess = ClientMixinAccess.INSTANCE;
        if (!mixinAccess.canBreakBlocks()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "doAttack",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$doAttack(CallbackInfoReturnable<Boolean> cir) {
        ClientMixinAccess mixinAccess = ClientMixinAccess.INSTANCE;
        if (mixinAccess.predictItemLeftClickInteraction()) {
            cir.cancel();
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V",
            at = @At("HEAD")
    )
    public void engine$disconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.onDisconnect();
    }
}
