package org.lain.engine.client.mixin.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import org.lain.engine.client.mc.ChatChannelsBar;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.MinecraftChat;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Shadow protected TextFieldWidget chatField;

    @Inject(
            method = "sendMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatMessage(Ljava/lang/String;)V"
            ),
            cancellable = true)
    public void engine$sendChatMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        ci.cancel();
        ClientMixinAccess.INSTANCE.sendChatMessage(chatText);
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            MinecraftChat.INSTANCE.getChannelsBar().onClick((float) mouseX, (float) mouseY);
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftChat.INSTANCE.updateChatShaking(delta);
        float height = ((Screen)(Object)this).height;
        context.getMatrices().push();
        ChatChannelsBar chatChannelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetY = height - 16f - chatChannelsBar.getHeight();
        float offsetX = 2f;
        context.getMatrices().translate(offsetX, offsetY, 0f);
        chatChannelsBar.renderChatChannelsBar(context, mouseX - offsetX, mouseY - offsetY);
        context.getMatrices().pop();
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        float height = ((Screen)(Object)this).height;
        ChatChannelsBar chatChannelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetY = height - 16f - chatChannelsBar.getHeight();
        float offsetX = 2f;
        chatChannelsBar.onClick((float)mouseX - offsetX, (float)mouseY - offsetY);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"
            )
    )
    public void engine$renderTextShake(TextFieldWidget instance, DrawContext drawContext, int mouseX, int mouseY, float delta) {
        MinecraftChat chat = MinecraftChat.INSTANCE;
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(chat.getRandomShakeTranslation(), chat.getRandomShakeTranslation(), 0f);
        instance.render(drawContext, mouseX, mouseY, delta);
        drawContext.getMatrices().pop();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
            )
    )
    public void engine$colorizeChatInputBackground(DrawContext instance, int x1, int y1, int x2, int y2, int color) {
        MinecraftChat chat = MinecraftChat.INSTANCE;
        instance.fill(x1, y1, x2, y2, color);
        instance.fill(RenderLayer.getGuiOverlay(), x1, y1, x2, y2, 50, chat.getChatFieldColor(color));
    }

    @Inject(
            method = "keyPressed",
            at = @At(
                    value = "RETURN"
            )
    )
    public void engine$onKeyPress(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        MinecraftChat.INSTANCE.updateChatInput(chatField.getText());
    }
}
