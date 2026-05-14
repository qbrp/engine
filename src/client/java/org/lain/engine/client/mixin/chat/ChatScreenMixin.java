package org.lain.engine.client.mixin.chat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.MinecraftChat;
import org.lain.engine.client.mc.MinecraftKeybindKt;
import org.lain.engine.client.render.ui.ChatChannelsBar;
import org.lain.engine.client.render.ui.HandStatusButtonWidget;
import org.lain.engine.client.render.ui.ShakingTextFieldWidget;
import org.lain.engine.client.mixin.render.ScreenAccessor;
import org.lain.engine.mc.CommonUtilKt;
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
    @Shadow protected EditBox input;
    @Shadow
    CommandSuggestions commandSuggestions;

    @Shadow protected String initial;

    @Shadow protected abstract @Nullable FormattedCharSequence formatChat(String string, int firstCharacterIndex);

    @Shadow public abstract String normalizeChatMessage(String chatText);

    @Shadow
    protected abstract void onEdited(String string);

    @Shadow
    private int historyPos;

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/ChatScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    public GuiEventListener engine$init(GuiEventListener par1) {
        Screen screen = (Screen)((Object)this);
        ((ScreenAccessor) screen).engine$addDrawableChild(
                new HandStatusButtonWidget(
                        ClientMixinAccess.INSTANCE.getEngineClient(),
                        screen.width - 32 - 2,
                        screen.height - 32 - 14 - 2,
                        32,
                        32,
                        CommonUtilKt.literalText("Выставить руку")
                )
        );
        CommandSuggestions chatInputSuggestor = this.commandSuggestions;
        this.input = new ShakingTextFieldWidget(
                Minecraft.getInstance().fontFilterFishy,
                4,
                screen.height - 12,
                screen.width - 4,
                12,
                Component.translatable("chat.editBox")
        ){
            @Override
            protected @NonNull MutableComponent createNarrationMessage() {
                return super.createNarrationMessage().append(chatInputSuggestor.getNarrationMessage());
            }
        };
        this.input.setMaxLength(256);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        this.input.setResponder(this::onEdited);
        this.input.addFormatter(this::formatChat);
        this.input.setCanLoseFocus(false);

        return this.input;
    }

    @Inject(
            method = "handleChatInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"
            ),
            cancellable = true)
    public void engine$sendChatMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (ClientMixinAccess.INSTANCE.isEngineLoaded()) {
            ci.cancel();
            ClientMixinAccess.INSTANCE.sendChatMessage(chatText);
        }
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$onMouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            MinecraftChat.INSTANCE.getChannelsBar().onClick((float) mouseButtonEvent.x(), (float) mouseButtonEvent.y());
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftChat.INSTANCE.updateChatShaking(delta);
        float height = ((Screen)(Object)this).height;
        context.pose().pushMatrix();
        ChatChannelsBar chatChannelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetY = height - 16f - chatChannelsBar.getHeight();
        float offsetX = 2f;
        context.pose().translate(offsetX, offsetY);
        chatChannelsBar.renderChatChannelsBar(context, mouseX - offsetX, mouseY - offsetY);
        context.pose().popMatrix();
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        float height = ((Screen)(Object)this).height;
        ChatChannelsBar chatChannelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetY = height - 16f - chatChannelsBar.getHeight();
        float offsetX = 2f;
        chatChannelsBar.onClick((float)mouseButtonEvent.x() - offsetX, (float)mouseButtonEvent.y() - offsetY);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"
            )
    )
    public void engine$colorizeChatInputBackground(GuiGraphics instance, int x1, int y1, int x2, int y2, int color) {
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
    public void engine$onKeyPress(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        MinecraftChat chat = MinecraftChat.INSTANCE;
        chat.updateChatInput(input.getValue());
        MinecraftChat.ChatMessageData selectedMessage = chat.getSelectedMessage();
        if (selectedMessage != null) {
            if (keyEvent.key() == GLFW.GLFW_KEY_DELETE) {
                ClientMixinAccess.INSTANCE.deleteChatMessage(selectedMessage.getEngineMessage());
            } else if (MinecraftKeybindKt.isControlDown() && keyEvent.key() == InputConstants.KEY_C) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selectedMessage.getNode().content().getString());
                ClientMixinAccess.INSTANCE.setChatClipboardCopyTicksElapsed(0);
            }
        }
        if (keyEvent.isConfirmation()) {
            chat.setSelectedMessage(null);
        }
    }

    @Redirect(
            method = "keyPressed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"
            )
    )
    public void engine$setScreen(Minecraft instance, Screen screen) {
        if (ClientMixinAccess.INSTANCE.sendingMessageClosesChat() || MinecraftKeybindKt.isControlDown()) {
            instance.setScreen(screen);
        } else {
            if (!normalizeChatMessage(input.getValue()).isEmpty()) {
                input.setValue("");
                historyPos = instance.gui.getChat().getRecentChat().size();
                MinecraftChat.INSTANCE.onCloseChatInput();
            }
        }
    }

    @Inject(
            method = "moveInHistory",
            at = @At("HEAD")
    )
    public void engine$focus(int offset, CallbackInfo ci) {
        this.input.setFocused(true);
    }

    @Inject(
            method = "onClose",
            at = @At("TAIL")
    )
    public void engine$close(CallbackInfo ci) {
        MinecraftChat.INSTANCE.onCloseChatInput();
        MinecraftChat.INSTANCE.setSelectedMessage(null);
    }
}
