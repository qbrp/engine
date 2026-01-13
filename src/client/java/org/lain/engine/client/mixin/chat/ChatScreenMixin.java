package org.lain.engine.client.mixin.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.render.ChatChannelsBar;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.MinecraftChat;
import org.lain.engine.client.mc.render.ShakingTextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected TextFieldWidget chatField;
    @Shadow
    ChatInputSuggestor chatInputSuggestor;

    @Shadow protected String originalChatText;

    @Shadow protected abstract void onChatFieldUpdate(String chatText);

    @Shadow protected abstract @Nullable OrderedText format(String string, int firstCharacterIndex);

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ChatScreen;addDrawableChild(Lnet/minecraft/client/gui/Element;)Lnet/minecraft/client/gui/Element;"
            )
    )
    public Element engine$init(Element par1) {
        Screen screen = (Screen)((Object)this);
        ChatInputSuggestor chatInputSuggestor = this.chatInputSuggestor;
        this.chatField = new ShakingTextFieldWidget(
                MinecraftClient.getInstance().advanceValidatingTextRenderer,
                4,
                screen.height - 12,
                screen.width - 4,
                12,
                Text.translatable("chat.editBox")
        ){
            @Override
            protected MutableText getNarrationMessage() {
                return super.getNarrationMessage().append(chatInputSuggestor.getNarration());
            }
        };
        this.chatField.setMaxLength(256);
        this.chatField.setDrawsBackground(false);
        this.chatField.setText(this.originalChatText);
        this.chatField.setChangedListener(this::onChatFieldUpdate);
        this.chatField.addFormatter(this::format);
        this.chatField.setFocusUnlocked(false);
        return this.chatField;
    }

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
    public void engine$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            MinecraftChat.INSTANCE.getChannelsBar().onClick((float) click.x(), (float) click.y());
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
        context.getMatrices().pushMatrix();
        ChatChannelsBar chatChannelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetY = height - 16f - chatChannelsBar.getHeight();
        float offsetX = 2f;
        context.getMatrices().translate(offsetX, offsetY);
        chatChannelsBar.renderChatChannelsBar(context, mouseX - offsetX, mouseY - offsetY);
        context.getMatrices().popMatrix();
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$mouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        float height = ((Screen)(Object)this).height;
        ChatChannelsBar chatChannelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetY = height - 16f - chatChannelsBar.getHeight();
        float offsetX = 2f;
        chatChannelsBar.onClick((float)click.x() - offsetX, (float)click.y() - offsetY);
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
        instance.fill(x1, y1, x2, y2, chat.getChatFieldColor(color));
    }

    @Inject(
            method = "keyPressed",
            at = @At(
                    value = "RETURN"
            )
    )
    public void engine$onKeyPress(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        MinecraftChat chat = MinecraftChat.INSTANCE;
        chat.updateChatInput(chatField.getText());
        MinecraftChat.ChatMessageData selectedMessage = chat.getSelectedMessage();
        if (selectedMessage != null && input.key() == GLFW.GLFW_KEY_DELETE) {
            ClientMixinAccess.INSTANCE.deleteChatMessage(selectedMessage.getEngineMessage());
        }
        if (input.isEnter()) {
            MinecraftChat.INSTANCE.setSelectedMessage(null);
        }
    }

    @Inject(
            method = "close",
            at = @At("TAIL")
    )
    public void engine$close(CallbackInfo ci) {
        MinecraftChat.INSTANCE.setSelectedMessage(null);
    }
}
