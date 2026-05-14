package org.lain.engine.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow @Nullable public LocalPlayer player;

    @Shadow protected abstract boolean startAttack();

    @Inject(
            method = "continueAttack",
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
            method = "startAttack",
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
            method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At("HEAD")
    )
    public void engine$disconnect(Screen screen, boolean bl, CallbackInfo ci) {
        ClientMixinAccess.INSTANCE.onDisconnect();
    }
}
