package org.lain.engine.client.mixin.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.lain.engine.client.mc.chat.EngineChatHudAccess;
import org.lain.engine.client.mc.chat.MinecraftChat;
import org.lain.engine.client.render.ui.chat.ChatHudWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements EngineChatHudAccess {
    @Unique
    private final ChatHudWrapper engine$wrapper = new ChatHudWrapper((ChatComponent)(Object)this);

    /**
     * @author Lain1wakura
     * @reason Engine owns chat layout, selection, avatars, and message metadata.
     */
    @Inject(
            at = @At(value = "HEAD"),
            method = "render",
            cancellable = true
    )
    public void render(GuiGraphics guiGraphics, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        this.engine$wrapper.render(guiGraphics, mouseX, mouseY, focused);
        ci.cancel();
    }

    /**
     * @author lain1wakura
     * @reason Engine-rendered messages are the source for component hit testing.
     */
    @Overwrite
    @Nullable
    public Style getClickedComponentStyleAt(double mouseX, double mouseY) {
        return this.engine$wrapper.getClickedComponentStyleAt(mouseX, mouseY);
    }

    @Override
    public boolean engine$selectMessage(double mouseX, double mouseY) {
        return this.engine$wrapper.selectMessage(mouseX, mouseY);
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V"
            ),
            cancellable = true
    )
    private void engine$storeVanillaMessage(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        this.engine$wrapper.storeVanillaMessage(component, signature, tag);
        ci.cancel();
    }

    @Inject(method = "deleteMessage", at = @At("HEAD"), cancellable = true)
    private void engine$disableVanillaDeletion(MessageSignature signature, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void engine$clearMessages(boolean clearHistory, CallbackInfo ci) {
        MinecraftChat.INSTANCE.clearMessages();
    }

    @Inject(method = "rescaleChat", at = @At("TAIL"))
    private void engine$rescaleChat(CallbackInfo ci) {}

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void engine$scrollChat(int amount, CallbackInfo ci) {
        if (this.engine$wrapper.scrollChat(amount)) {
            ci.cancel();
        }
    }

    @Inject(method = "resetChatScroll", at = @At("TAIL"))
    private void engine$resetScroll(CallbackInfo ci) {
        this.engine$wrapper.resetScroll();
    }
}
