package org.lain.engine.client.mixin;

import com.daqem.yamlconfig.client.gui.screen.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.mixin.screen.TitleScreenAccessor;
import org.lain.engine.client.render.ui.EngineTitleMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow @Nullable public LocalPlayer player;

    @Shadow protected abstract boolean startAttack();

    @Shadow
    private @org.jspecify.annotations.Nullable Overlay overlay;

    @Shadow
    public abstract void setScreen(@org.jspecify.annotations.Nullable Screen screen);

    @Shadow
    @org.jspecify.annotations.Nullable
    public Screen screen;

    @Shadow
    @org.jspecify.annotations.Nullable
    public ClientLevel level;

    @Inject(
            method = "setScreen",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$invokeOptionsChangedListener(Screen screen, CallbackInfo ci) {
        if (this.screen instanceof ConfigScreen && screen == null) {
            ClientMixin.INSTANCE.onYamlConfigScreenClosed();
        } else if (screen instanceof TitleScreen titleScreen && !(this.screen instanceof EngineTitleMenu)) {
            ClientMixin.INSTANCE.openEngineTitleMenu(((TitleScreenAccessor)titleScreen).engine$isFading());
            ci.cancel();
        } else if (screen == null && level == null) {
            ClientMixin.INSTANCE.openEngineTitleMenu(false);
            ci.cancel();
        }
    }

    @Inject(
            method = "continueAttack",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        ClientMixin mixinAccess = ClientMixin.INSTANCE;
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
        ClientMixin mixinAccess = ClientMixin.INSTANCE;
        if (mixinAccess.predictItemLeftClickInteraction()) {
            cir.cancel();
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
            at = @At("HEAD")
    )
    public void engine$disconnect(Screen screen, boolean bl, boolean bl2, CallbackInfo ci) {
        ClientMixin.INSTANCE.onDisconnect();
    }
}
