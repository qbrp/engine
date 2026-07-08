package org.lain.engine.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.render.ui.TestGrapheneScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.internal.cef.startup.GrapheneNativeDownloadOverlay;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow @Nullable public LocalPlayer player;

    @Shadow protected abstract boolean startAttack();

    @Shadow
    private @org.jspecify.annotations.Nullable Overlay overlay;

    @Shadow
    public abstract void setScreen(@org.jspecify.annotations.Nullable Screen screen);

    @Inject(
            method = "setOverlay",
            at = @At("HEAD")
    )
    public void engine$showGrapheneTestScreen(Overlay overlay, CallbackInfo ci) {
        if (this.overlay instanceof GrapheneNativeDownloadOverlay && overlay == null && GrapheneCore.isInitialized()) {
            ClientMixinAccess.INSTANCE.setGrapheneTestScreen();
        }
    }

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
