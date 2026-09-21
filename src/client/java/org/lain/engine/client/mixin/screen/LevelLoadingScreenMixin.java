package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import org.lain.engine.client.handler.GameSessionJoinFlow;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ReceivingLevelScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Unique
    private static final Component ENGINE_AUTHORIZATION_TEXT = Component.literal("Авторизация...");

    @Unique
    private static final Component ENGINE_COMPILATION_TEXT = Component.literal("Компиляция ресурсов...");

    @Unique
    private static final Component ENGINE_CHARACTER_LOAD_TEXT = Component.literal("Загрузка персонажей...");

    @Shadow
    @Final
    private long createdAt;

    @Shadow
    @Final
    private BooleanSupplier levelReceived;

    @Shadow
    public abstract void onClose();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void engine$waitForSession(CallbackInfo ci) {
        boolean minecraftReady = this.levelReceived.getAsBoolean()
                || System.currentTimeMillis() > this.createdAt + 30_000L;
        if (minecraftReady && ClientMixin.INSTANCE.canCloseLevelLoadingScreen()) {
            this.onClose();
        }
        ci.cancel();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void engine$drawStateString(
            GuiGraphics guiGraphics,
            Font font,
            Component vanillaText,
            int x,
            int y,
            int color
    ) {
        GameSessionJoinFlow.State state = ClientMixin.INSTANCE.multiplayerConnectionState();
        if (!this.levelReceived.getAsBoolean() || state == null) {
            guiGraphics.drawCenteredString(font, vanillaText, x, y, color);
            return;
        }

        Component stateText = switch (state) {
            case AUTHORIZATION -> ENGINE_AUTHORIZATION_TEXT;
            case CHARACTER_LOAD -> ENGINE_CHARACTER_LOAD_TEXT;
            default -> ENGINE_COMPILATION_TEXT;
        };
        guiGraphics.drawCenteredString(font, stateText, x, y, color);
    }
}
