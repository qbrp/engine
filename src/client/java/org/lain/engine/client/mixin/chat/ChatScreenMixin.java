package org.lain.engine.client.mixin.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NonNull;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.mc.MinecraftKeybindKt;
import org.lain.engine.client.mc.chat.EngineChatHudAccess;
import org.lain.engine.client.mc.chat.EngineChatHudMessage;
import org.lain.engine.client.mc.chat.MinecraftChat;
import org.lain.engine.client.mixin.render.ScreenAccessor;
import org.lain.engine.client.render.ui.hud.ChatChannelsBar;
import org.lain.engine.client.render.ui.hud.HandStatusButtonWidget;
import org.lain.engine.client.render.ui.hud.ShakingTextFieldWidget;
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
    @Shadow
    protected EditBox input;

    @Shadow
    CommandSuggestions commandSuggestions;

    @Shadow
    private String initial;

    @Shadow
    private int historyPos;

    @Shadow
    protected abstract void onEdited(String text);

    @Shadow
    public abstract String normalizeChatMessage(String chatText);

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/ChatScreen;addWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    private GuiEventListener engine$replaceChatInput(GuiEventListener originalInput) {
        Screen screen = (Screen)(Object)this;
        ((ScreenAccessor)screen).engine$addDrawableChild(
                new HandStatusButtonWidget(
                        ClientMixin.INSTANCE.getEngineClient(),
                        screen.width - 34,
                        screen.height - 48,
                        32,
                        32,
                        Component.literal("Выставить руку")
                )
        );

        this.input = new ShakingTextFieldWidget(
                Minecraft.getInstance().fontFilterFishy,
                4,
                screen.height - 12,
                screen.width - 4,
                12,
                Component.translatable("chat.editBox")
        ) {
            @Override
            protected @NonNull MutableComponent createNarrationMessage() {
                MutableComponent narration = super.createNarrationMessage();
                return ChatScreenMixin.this.commandSuggestions == null
                        ? narration
                        : narration.append(ChatScreenMixin.this.commandSuggestions.getNarrationMessage());
            }
        };
        this.input.setMaxLength(256);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        this.input.setResponder(this::onEdited);
        this.input.setCanLoseFocus(false);
        return this.input;
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void engine$sendChatMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isEngineLoaded()) {
            return;
        }

        String normalized = this.normalizeChatMessage(chatText);
        if (!normalized.isEmpty()) {Minecraft minecraft = Minecraft.getInstance();
            if (addToHistory) {
                minecraft.gui.getChat().addRecentChat(normalized);
            }
            if (!normalized.startsWith("/")) {
                ClientMixin.INSTANCE.sendChatMessage(normalized);
            } else if (minecraft.player != null) {
                minecraft.player.connection.sendCommand(normalized.substring(1));
            }
        }
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void engine$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_1) {
            return;
        }

        Screen screen = (Screen)(Object)this;
        ChatChannelsBar channelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetX = 2.0F;
        float offsetY = screen.height - 16.0F - channelsBar.getHeight();
        float localX = (float)mouseX - offsetX;
        float localY = (float)mouseY - offsetY;
        boolean overChannels = localX >= 0.0F
                && localX <= channelsBar.getWidth()
                && localY >= 0.0F
                && localY <= channelsBar.getHeight();
        if (overChannels) {
            channelsBar.onClick(localX, localY);
        } else {
            ((EngineChatHudAccess)Minecraft.getInstance().gui.getChat()).engine$selectMessage(mouseX, mouseY);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void engine$renderChannels(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftChat.INSTANCE.updateChatShaking(delta);
        Screen screen = (Screen)(Object)this;
        ChatChannelsBar channelsBar = MinecraftChat.INSTANCE.getChannelsBar();
        float offsetX = 2.0F;
        float offsetY = screen.height - 16.0F - channelsBar.getHeight();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(offsetX, offsetY, 0.0F);
        channelsBar.renderChatChannelsBar(guiGraphics, mouseX - offsetX, mouseY - offsetY);
        guiGraphics.pose().popPose();
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void engine$colorizeChatInputBackground(
            GuiGraphics guiGraphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {
        guiGraphics.fill(x1, y1, x2, y2, color);
        guiGraphics.fill(x1, y1, x2, y2, MinecraftChat.INSTANCE.getChatFieldColor(color));
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void engine$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        MinecraftChat chat = MinecraftChat.INSTANCE;
        chat.updateChatInput(this.input.getValue());
        EngineChatHudMessage selectedMessage = chat.getSelectedMessage();
        if (selectedMessage != null) {
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                ClientMixin.INSTANCE.deleteChatMessage(selectedMessage.getEngineMessage());
            } else if (keyCode == GLFW.GLFW_KEY_C && MinecraftKeybindKt.isControlDown()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selectedMessage.getClipboardText());
                ClientMixin.INSTANCE.setChatClipboardCopyTicksElapsed(0);
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
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
    private void engine$setScreen(
            Minecraft minecraft,
            Screen screen,
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        boolean submitted = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (!submitted || ClientMixin.INSTANCE.sendingMessageClosesChat() || MinecraftKeybindKt.isControlDown()) {
            minecraft.setScreen(screen);
            return;
        }

        this.input.setValue("");
        this.historyPos = minecraft.gui.getChat().getRecentChat().size();
        MinecraftChat.INSTANCE.onCloseChatInput();
    }

    @Inject(method = "moveInHistory", at = @At("HEAD"))
    private void engine$focusInput(int offset, CallbackInfo ci) {
        this.input.setFocused(true);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void engine$close(CallbackInfo ci) {
        MinecraftChat.INSTANCE.onCloseChatInput();
        MinecraftChat.INSTANCE.setSelectedMessage(null);
    }
}
