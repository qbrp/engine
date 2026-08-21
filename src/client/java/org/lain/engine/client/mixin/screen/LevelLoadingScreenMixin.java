package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import org.lain.engine.client.handler.GameSessionJoinFlow;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
    @Shadow
    private LevelLoadTracker loadTracker;

    @Unique
    private final static Component AUTHORIZATION_STATE_TEXT = Component.literal("Авторизация...");
    @Unique
    private final static Component COMPILATION_STATE_TEXT = Component.literal("Компиляция ресурсов...");
    @Unique
    private final static Component CHARACTER_LOAD_TEXT = Component.literal("Загрузка персонажей...");

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;isLevelReady()Z"
            )
    )
    private boolean engine$isLevelReady(LevelLoadTracker instance) {
        return instance.isLevelReady() && ClientMixin.INSTANCE.canCloseLevelLoadingScreen();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void engine$drawStateString(GuiGraphics instance, Font font, Component component, int i, int j, int k) {
        GameSessionJoinFlow.State state = ClientMixin.INSTANCE.multiplayerConnectionState();
        if (!loadTracker.isLevelReady() || state == null) {
            instance.drawCenteredString(font, component, i, j, k);
        } else {
            Component stateText;
            if (state == GameSessionJoinFlow.State.AUTHORIZATION) {
                stateText = AUTHORIZATION_STATE_TEXT;
            } else if (state == GameSessionJoinFlow.State.CHARACTER_LOAD) {
                stateText = CHARACTER_LOAD_TEXT;
            } else {
                stateText = COMPILATION_STATE_TEXT;
            }
            instance.drawCenteredString(font, stateText, i, j, k);
        }
    }
}
