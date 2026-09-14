package org.lain.engine.client.mixin.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lain.engine.client.chat.AcceptedMessage;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.mc.chat.EngineAlphaCalculator;
import org.lain.engine.client.mc.chat.EngineChatHudAccess;
import org.lain.engine.client.mc.chat.EngineChatHudLine;
import org.lain.engine.client.mc.chat.EngineChatHudMessage;
import org.lain.engine.client.mc.chat.MinecraftChat;
import org.lain.engine.client.render.ui.hud.ChatHudRenderKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
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
    private static final String ENGINE_MAXIMUM_REPEATS_TEXT = "x999+";

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    public abstract boolean isChatFocused();

    @Shadow
    public abstract void scrollChat(int amount);

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract double getScale();

    @Unique
    private final List<EngineChatHudLine> engine$visibleMessages = MinecraftChat.INSTANCE.getVisibleMessages();

    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void engine$indentWidth(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + ChatHudRenderKt.CHAT_HEAD_SIZE);
    }

    /**
     * @author Lain1wakura
     * @reason Engine owns chat layout, selection, avatars, and message metadata.
     */
    @Overwrite
    public void render(GuiGraphics guiGraphics, int currentTick, int mouseX, int mouseY, boolean focused) {
        if (this.engine$isChatHidden()) {
            return;
        }

        focused = focused || ClientMixin.INSTANCE.shouldFocusChatIfNot();
        List<MinecraftChat.TypingPlayer> typingPlayers = MinecraftChat.INSTANCE.getTypingPlayers().stream().toList();
        long queuedMessages = this.minecraft.getChatListener().queueSize();
        int totalLines = this.engine$visibleMessages.size();
        if (totalLines == 0 && typingPlayers.isEmpty() && queuedMessages == 0L) {
            return;
        }

        this.minecraft.getProfiler().push("chat");

        float chatScale = (float)this.getScale();
        int chatWidth = Mth.ceil(this.getWidth() / chatScale);
        int chatBottomY = Mth.floor((guiGraphics.guiHeight() - 40) / chatScale);
        float textOpacity = this.minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
        float backgroundOpacity = this.minecraft.options.textBackgroundOpacity().get().floatValue();
        double lineSpacing = this.minecraft.options.chatLineSpacing().get();
        int lineHeight = this.engine$getLineHeight();
        int textOffsetY = (int)Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
        EngineAlphaCalculator alphaCalculator = focused
                ? EngineAlphaCalculator.Companion.getFULLY_VISIBLE()
                : EngineAlphaCalculator.Companion.timeBased(currentTick);
        EngineChatHudMessage hoveredMessage = this.engine$getMessageAt(mouseX, mouseY, focused);
        EngineChatHudMessage selectedMessage = MinecraftChat.INSTANCE.getSelectedMessage();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(chatScale, chatScale, 1.0F);
        guiGraphics.pose().translate(4.0F, 0.0F, 0.0F);

        if (!typingPlayers.isEmpty()) {
            guiGraphics.fill(
                    -4,
                    chatBottomY,
                    chatWidth + 8,
                    chatBottomY + lineHeight,
                    engine$withAlpha(backgroundOpacity, CommonColors.BLACK)
            );

            int textX = 0;
            for (int index = 0; index < typingPlayers.size(); index++) {
                MinecraftChat.TypingPlayer player = typingPlayers.get(index);
                Component name = player.getName();
                engine$drawFace(guiGraphics, player.getSkinTextures(), textX + 1, chatBottomY + 1, 0xFF505050);
                engine$drawFace(guiGraphics, player.getSkinTextures(), textX, chatBottomY, CommonColors.WHITE);
                textX += 10;
                guiGraphics.drawString(this.minecraft.font, name, textX, chatBottomY + 1, CommonColors.WHITE);
                textX += this.minecraft.font.width(name);
                if (index < typingPlayers.size() - 1) {
                    guiGraphics.drawString(this.minecraft.font, ",", ++textX, chatBottomY + 1, CommonColors.LIGHT_GRAY);
                }
                textX += 4;
            }
            guiGraphics.drawString(this.minecraft.font, "печатает...", textX, chatBottomY + 1, CommonColors.LIGHT_GRAY);
        }

        int renderedLineCount = 0;
        int pageLineCount = Math.min(this.getLinesPerPage(), Math.max(0, totalLines - this.chatScrollbarPos));
        for (int lineIndex = 0; lineIndex < pageLineCount; lineIndex++) {
            EngineChatHudLine engineLine = this.engine$visibleMessages.get(lineIndex + this.chatScrollbarPos);
            float alpha = alphaCalculator.calculate(engineLine);
            if (alpha <= 1.0E-5F) {
                continue;
            }
            renderedLineCount++;

            int lineBottomY = chatBottomY - lineIndex * lineHeight;
            int lineTopY = lineBottomY - lineHeight;
            int textY = lineBottomY + textOffsetY;
            AcceptedMessage engineMessage = engineLine.getMessage().getEngineMessage();
            Integer configuredBackground = engineMessage.getBackgroundColorInt();
            int backgroundColor = configuredBackground != null ? configuredBackground : CommonColors.BLACK;

            guiGraphics.fill(
                    -4,
                    lineTopY,
                    chatWidth + 8,
                    lineBottomY,
                    engine$withAlpha(alpha * backgroundOpacity, backgroundColor)
            );

            int textColor = engine$withAlpha(alpha * textOpacity, CommonColors.WHITE);
            guiGraphics.drawString(
                    this.minecraft.font,
                    engineLine.getText(),
                    ChatHudRenderKt.CHAT_HEAD_SIZE + 2,
                    textY,
                    textColor
            );

            PlayerInfo author = engineLine.getMessage().getAuthor();
            if (author != null && engineLine.isFirst() && engineMessage.getShowHead()) {
                int shadowColor = engine$withAlpha(alpha * textOpacity, 0xFF505050);
                engine$drawFace(guiGraphics, author.getSkin(), 1, textY + 1, shadowColor);
                engine$drawFace(guiGraphics, author.getSkin(), 0, textY, textColor);
            }

            if (engineLine.isLast()) {
                int repeats = engineMessage.getRepeat();
                if (repeats > 1) {
                    String repeatsString = repeats > 999 ? ENGINE_MAXIMUM_REPEATS_TEXT : "x" + repeats;
                    Component repeatsText = Component.literal(repeatsString).withStyle(ChatFormatting.GOLD);
                    int repeatsX = chatWidth - this.minecraft.font.width(repeatsText);
                    guiGraphics.drawString(this.minecraft.font, repeatsText, repeatsX, textY, textColor, true);
                }

                Component debugText = engineLine.getMessage().getDebugText();
                if (debugText != null && MinecraftChat.INSTANCE.shouldRenderDebugInfo()) {
                    int debugX = ChatHudRenderKt.CHAT_HEAD_SIZE + 6 + this.minecraft.font.width(engineLine.getText());
                    guiGraphics.drawString(
                            this.minecraft.font,
                            debugText,
                            debugX,
                            textY,
                            engine$withAlpha(alpha * textOpacity, CommonColors.GRAY)
                    );
                }
            }

            int selectionAlpha = 0;
            EngineChatHudMessage message = engineLine.getMessage();
            if (message.equals(hoveredMessage)) {
                selectionAlpha += 30;
            }
            if (message.equals(selectedMessage)) {
                selectionAlpha += 30;
                if (ClientMixin.INSTANCE.getChatClipboardCopyTicksElapsed() <= 2) {
                    selectionAlpha += 30;
                }
            }
            if (selectionAlpha > 0) {
                guiGraphics.fill(-4, lineTopY, chatWidth + 8, lineBottomY, engine$withAlpha(selectionAlpha / 255.0F, CommonColors.WHITE));
            }
        }

        if (queuedMessages > 0L) {
            int queueTextAlpha = (int)(128.0F * textOpacity);
            int queueBackgroundAlpha = (int)(255.0F * backgroundOpacity);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, chatBottomY, 0.0F);
            guiGraphics.fill(-2, 0, chatWidth + 4, 9, queueBackgroundAlpha << 24);
            guiGraphics.drawString(
                    this.minecraft.font,
                    Component.translatable("chat.queue", queuedMessages),
                    0,
                    1,
                    engine$withAlpha(queueTextAlpha / 255.0F, CommonColors.WHITE)
            );
            guiGraphics.pose().popPose();
        }

        if (focused && totalLines > 0 && renderedLineCount > 0) {
            int totalPixelHeight = totalLines * lineHeight;
            int visiblePixelHeight = renderedLineCount * lineHeight;
            int scrollbarY = this.chatScrollbarPos * visiblePixelHeight / totalLines - chatBottomY;
            int scrollbarHeight = visiblePixelHeight * visiblePixelHeight / totalPixelHeight;
            if (totalPixelHeight != visiblePixelHeight) {
                int scrollbarAlpha = scrollbarY > 0 ? 170 : 96;
                int scrollbarColor = this.newMessageSinceScroll ? 0xCC3333 : 0x3333AA;
                int scrollbarX = chatWidth + 4;
                guiGraphics.fill(
                        scrollbarX,
                        -scrollbarY,
                        scrollbarX + 2,
                        -scrollbarY - scrollbarHeight,
                        engine$withAlpha(scrollbarAlpha / 255.0F, scrollbarColor)
                );
                guiGraphics.fill(
                        scrollbarX + 2,
                        -scrollbarY,
                        scrollbarX + 1,
                        -scrollbarY - scrollbarHeight,
                        engine$withAlpha(scrollbarAlpha / 255.0F, 0xCCCCCC)
                );
            }
        }

        guiGraphics.pose().popPose();
        this.minecraft.getProfiler().pop();
    }

    /**
     * @author lain1wakura
     * @reason Engine exposes a configurable chat width.
     */
    @Overwrite
    public static int getWidth(double widthOption) {
        return Mth.floor(widthOption * ClientMixin.INSTANCE.getChatWidth() + 40);
    }

    /**
     * @author lain1wakura
     * @reason Engine-rendered lines are the source for component hit testing.
     */
    @Overwrite
    @Nullable
    public Style getClickedComponentStyleAt(double mouseX, double mouseY) {
        int lineIndex = this.engine$getMessageLineIndexAt(mouseX, mouseY, true);
        if (lineIndex < 0) {
            return null;
        }

        double chatX = mouseX / this.getScale() - 4.0;
        int textX = Mth.floor(chatX) - ChatHudRenderKt.CHAT_HEAD_SIZE - 2;
        if (textX < 0) {
            return null;
        }
        FormattedCharSequence text = this.engine$visibleMessages.get(lineIndex).getText();
        return this.minecraft.font.getSplitter().componentStyleAtWidth(text, textX);
    }

    @Override
    public void engine$addMessage(@NonNull EngineChatHudMessage message, boolean isVisible) {
        int chatWidth = Mth.floor(this.getWidth() / this.getScale());
        List<EngineChatHudLine> lines = message.resolveLines(chatWidth);
        boolean focused = this.isChatFocused();

        for (EngineChatHudLine line : lines) {
            if (focused && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(1);
            }
            this.engine$visibleMessages.add(0, line);
        }

        while (this.engine$visibleMessages.size() > ClientMixin.INSTANCE.getChatSize()) {
            this.engine$visibleMessages.remove(this.engine$visibleMessages.size() - 1);
        }
    }

    @Override
    public boolean engine$selectMessage(double mouseX, double mouseY) {
        EngineChatHudMessage clickedMessage = this.engine$getMessageAt(mouseX, mouseY, true);
        EngineChatHudMessage selectedMessage = MinecraftChat.INSTANCE.getSelectedMessage();
        MinecraftChat.INSTANCE.setSelectedMessage(clickedMessage != null && clickedMessage.equals(selectedMessage) ? null : clickedMessage);
        return clickedMessage != null;
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
        MinecraftChat.INSTANCE.storeVanillaGuiMessage(new GuiMessage(this.minecraft.gui.getGuiTicks(), component, signature, tag));
        ci.cancel();
    }

    @Inject(method = "deleteMessage", at = @At("HEAD"), cancellable = true)
    private void engine$disableVanillaDeletion(MessageSignature signature, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void engine$clearMessages(boolean clearHistory, CallbackInfo ci) {
        MinecraftChat.INSTANCE.clearChatData();
    }

    @Inject(method = "rescaleChat", at = @At("TAIL"))
    private void engine$rescaleChat(CallbackInfo ci) {
        MinecraftChat.INSTANCE.invalidateChatEntries();
    }

    @Redirect(method = "scrollChat", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int engine$getVisibleMessageCount(List<?> ignored) {
        return this.engine$visibleMessages.size();
    }

    @Unique
    private EngineChatHudMessage engine$getMessageAt(double mouseX, double mouseY, boolean focused) {
        int index = this.engine$getMessageLineIndexAt(mouseX, mouseY, focused);
        return index >= 0 ? this.engine$visibleMessages.get(index).getMessage() : null;
    }

    @Unique
    private int engine$getMessageLineIndexAt(double mouseX, double mouseY, boolean focused) {
        if (!focused || !this.isChatFocused() || this.minecraft.options.hideGui || this.engine$isChatHidden()) {
            return -1;
        }

        double chatX = mouseX / this.getScale() - 4.0;
        if (chatX < -4.0 || chatX > Mth.floor(this.getWidth() / this.getScale())) {
            return -1;
        }

        double chatY = (this.minecraft.getWindow().getGuiScaledHeight() - mouseY - 40.0)
                / (this.getScale() * this.engine$getLineHeight());
        int visibleCount = Math.min(this.getLinesPerPage(), this.engine$visibleMessages.size());
        if (chatY < 0.0 || chatY >= visibleCount) {
            return -1;
        }

        int index = Mth.floor(chatY + this.chatScrollbarPos);
        return index >= 0 && index < this.engine$visibleMessages.size() ? index : -1;
    }

    @Unique
    private boolean engine$isChatHidden() {
        return this.minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN;
    }

    @Unique
    private int engine$getLineHeight() {
        return (int)(9.0 * (this.minecraft.options.chatLineSpacing().get() + 1.0));
    }

    @Unique
    private static int engine$withAlpha(float alpha, int color) {
        int alphaByte = Mth.clamp((int)(alpha * 255.0F), 0, 255);
        return alphaByte << 24 | color & 0xFFFFFF;
    }

    @Unique
    private static void engine$drawFace(GuiGraphics guiGraphics, net.minecraft.client.resources.PlayerSkin skin, int x, int y, int color) {
        guiGraphics.setColor(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                (color >>> 24) / 255.0F
        );
        PlayerFaceRenderer.draw(guiGraphics, skin, x, y, 8);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
