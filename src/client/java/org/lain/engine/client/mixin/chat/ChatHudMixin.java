package org.lain.engine.client.mixin.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lain.engine.client.mc.chat.EngineChatHudAccess;
import org.lain.engine.client.mc.chat.EngineChatHudMessage;
import org.lain.engine.client.render.ui.ChatHudWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements EngineChatHudAccess {
    @Unique
    private final ChatHudWrapper engine$wrapper = new ChatHudWrapper((ChatComponent)(Object)this);

    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void engine$indentWidth(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.engine$wrapper.indentWidth(cir.getReturnValue()));
    }

    /**
     * @author Lain1wakura
     * @reason Engine owns chat layout, selection, avatars, and message metadata.
     */
    @Overwrite
    public void render(GuiGraphics guiGraphics, int currentTick, int mouseX, int mouseY, boolean focused) {
        this.engine$wrapper.render(guiGraphics, currentTick, mouseX, mouseY, focused);
    }

    /**
     * @author lain1wakura
     * @reason Engine exposes a configurable chat width.
     */
    @Overwrite
    public static int getWidth(double widthOption) {
        return ChatHudWrapper.calculateWidth(widthOption);
    }

    /**
     * @author lain1wakura
     * @reason Engine-rendered lines are the source for component hit testing.
     */
    @Overwrite
    @Nullable
    public Style getClickedComponentStyleAt(double mouseX, double mouseY) {
        return this.engine$wrapper.getClickedComponentStyleAt(mouseX, mouseY);
    }

    @Override
    public void engine$addMessage(@NonNull EngineChatHudMessage message, boolean isVisible) {
        this.engine$wrapper.addMessage(message);
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
        this.engine$wrapper.clearMessages();
    }

    @Inject(method = "rescaleChat", at = @At("TAIL"))
    private void engine$rescaleChat(CallbackInfo ci) {
        this.engine$wrapper.rescaleChat();
    }

    @Redirect(method = "scrollChat", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int engine$getVisibleMessageCount(List<?> ignored) {
        return this.engine$wrapper.getVisibleMessageCount();
    }
}
