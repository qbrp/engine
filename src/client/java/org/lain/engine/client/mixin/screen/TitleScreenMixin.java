package org.lain.engine.client.mixin.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lain.engine.client.AuthorizationStatus;
import org.lain.engine.client.account.ConnectionState;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Unique
    private static final int ENGINE_AUTHORIZATION_STATUS_PADDING = 4;
    @Shadow
    private SplashRenderer splash;
    @Unique
    private Button.Plain singleplayerButton;
    @Unique
    private Button.Plain multiplayerButton;

    @Inject(method = "tick", at = @At("HEAD"))
    private void engine$tick(CallbackInfo ci) {
        if (singleplayerButton != null) {
            singleplayerButton.active = ClientMixinAccess.INSTANCE.canPlaySingleplayer();
        }
        if (multiplayerButton != null) {
            multiplayerButton.active = ClientMixinAccess.INSTANCE.canPlayMultiplayer();
        }
    }

    @Inject(
            method = "createNormalMenuOptions(II)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private void engine$afterCreateNormalMenuOptions(
            int i,
            int j,
            CallbackInfoReturnable<Integer> cir
    ) {
        for (GuiEventListener child : ((Screen)(Object)this).children().stream().toList()) {
            if (child instanceof Button.Plain button) {
                Component message = button.getMessage();
                if (message.equals(Component.translatable("menu.singleplayer"))) {
                    singleplayerButton = button;
                }
                if (message.equals(Component.translatable("menu.multiplayer"))) {
                    multiplayerButton = button;
                }
                if (message.equals(Component.translatable("menu.online"))) {
                    ((ScreenAccessor)this).engine$removeWidget(button);
                }
            }
        }
        cir.setReturnValue(cir.getReturnValue() - j);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void engine$removeSplash(CallbackInfo ci) {
        this.splash = null;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void engine$renderAuthorizationStatus(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AuthorizationStatus status = engine$getAuthorizationStatus();
        Font font = Minecraft.getInstance().font;
        int x = engine$getAuthorizationStatusX(guiGraphics.guiWidth(), font, status.text());
        int y = ENGINE_AUTHORIZATION_STATUS_PADDING;

        guiGraphics.drawString(font, status.text(), x, y, status.color());

        if (engine$isAuthorizationStatusHovered(guiGraphics.guiWidth(), mouseX, mouseY, font, status.text())) {
            guiGraphics.fill(x, y + font.lineHeight, x + font.width(status.text()), y + font.lineHeight + 1, status.color());
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void engine$onAuthorizationStatusClicked(MouseButtonEvent mouseButtonEvent, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (mouseButtonEvent.button() != GLFW.GLFW_MOUSE_BUTTON_1) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int screenWidth = ((Screen) (Object) this).width;
        String text = engine$getAuthorizationStatus().text();
        if (!engine$isAuthorizationStatusHovered(screenWidth, mouseButtonEvent.x(), mouseButtonEvent.y(), font, text)) {
            return;
        }

        ClientMixinAccess.INSTANCE.setDiscordAuthorizationScreen();
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Unique
    private static AuthorizationStatus engine$getAuthorizationStatus() {
        ConnectionState connectionState = ClientMixinAccess.INSTANCE.getConnectionState();
        if (connectionState instanceof ConnectionState.Authorizing) {
            return new AuthorizationStatus("Авторизация", 0xFFFFFF55);
        } else if (connectionState instanceof ConnectionState.Authorized) {
            return new AuthorizationStatus("Авторизован", 0xFF55FF55);
        } else {
            return new AuthorizationStatus("Не авторизован. Игра на серверах Engine недоступна", 0xFFFF5555);
        }
    }

    @Unique
    private static int engine$getAuthorizationStatusX(int screenWidth, Font font, String text) {
        return screenWidth - font.width(text) - ENGINE_AUTHORIZATION_STATUS_PADDING;
    }

    @Unique
    private static boolean engine$isAuthorizationStatusHovered(int screenWidth, double mouseX, double mouseY, Font font, String text) {
        int x = engine$getAuthorizationStatusX(screenWidth, font, text);
        int y = ENGINE_AUTHORIZATION_STATUS_PADDING;
        int width = font.width(text);
        int height = font.lineHeight;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
