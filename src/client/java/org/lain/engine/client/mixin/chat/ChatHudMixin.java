package org.lain.engine.client.mixin.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NonNull;
import org.lain.engine.client.chat.AcceptedMessage;
import org.lain.engine.client.chat.ClientMessagesKt;
import org.lain.engine.client.mc.*;
import org.lain.engine.client.mc.chat.*;
import org.lain.engine.client.render.ui.ChatHudRenderKt;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements EngineChatHudAccess {
    @Unique
    String MAXIMUM_REPEATS_TEXT = "x999+";

    @Unique
    GuiGraphics guiGraphics = null;

    @Shadow protected abstract int getLineHeight();

    @Shadow @Final private Minecraft minecraft;

    @Shadow
    protected abstract boolean isChatHidden();

    @Unique
    private List<EngineChatHudLine> visibleMessages = MinecraftChat.INSTANCE.getVisibleMessages();

    @Shadow
    protected abstract double getScale();

    @Shadow
    protected abstract int getWidth();

    @Unique
    private int forEachEngineLine(EngineAlphaCalculator alphaCalculator, EngineLineConsumer lineConsumer) {
        int i = this.getLinesPerPage();
        int j = 0;

        for (int k = Math.min(this.visibleMessages.size() - this.chatScrollbarPos, i) - 1; k >= 0; k--) {
            int l = k + this.chatScrollbarPos;
            EngineChatHudLine line = this.visibleMessages.get(l);
            float f = alphaCalculator.calculate(line);
            if (f > 1.0E-5F) {
                j++;
                lineConsumer.accept(line, k, f);
            }
        }

        return j;
    }

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    protected abstract void addMessageToDisplayQueue(GuiMessage guiMessage);

    @Shadow
    public abstract boolean isChatFocused();

    @Shadow
    public abstract void scrollChat(int i);

    @Shadow
    public abstract int getLinesPerPage();

    @Inject(
            method = "getWidth()I",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$indentWidth(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + ChatHudRenderKt.CHAT_HEAD_SIZE);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD")
    )
    public void engine$captureGuiGraphics(GuiGraphics guiGraphics, Font font, int i, int j, int k, boolean bl, boolean bl2, CallbackInfo ci) {
        this.guiGraphics = guiGraphics;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("TAIL")
    )
    public void engine$flushGuiGraphics(CallbackInfo ci) {
        this.guiGraphics = null;
    }

    /**
     * @author Lain1wakura
     * @reason Engine полностью меняет рендеринг чата, так что смысла мелочиться нет
     */
    @Overwrite
    private void render(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int currentTick, boolean chatFocused) {
        if (this.isChatHidden()) {
            return;
        }

        int totalLines = this.visibleMessages.size();
        if (totalLines <= 0) {
            return;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("chat");

        float chatScale = (float)this.getScale();
        int chatWidth = Mth.ceil(this.getWidth() / chatScale);
        int chatBottomY = Mth.floor((screenHeight - 40) / chatScale);

        float textOpacity = this.minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
        float backgroundOpacity = this.minecraft.options.textBackgroundOpacity().get().floatValue();
        final int fontHeight = 9;

        double lineSpacingOption = this.minecraft.options.chatLineSpacing().get();
        final int lineHeight = (int)(fontHeight * (lineSpacingOption + 1.0));
        final int textYOffset = (int)Math.round(8.0 * (lineSpacingOption + 1.0) - 4.0 * lineSpacingOption);

        EngineAlphaCalculator alphaCalculator =
                chatFocused
                        ? EngineAlphaCalculator.Companion.getFULLY_VISIBLE()
                        : EngineAlphaCalculator.Companion.timeBased(currentTick);

        graphics.updatePose(matrix -> {
            matrix.scale(chatScale, chatScale);
            matrix.translate(4.0F, 0.0F);
        });

        /*
         * Background rectangles
         */
        this.forEachEngineLine(alphaCalculator, (line, lineIndex, alpha) -> {
            int lineBottomY = chatBottomY - lineIndex * lineHeight;
            int lineTopY = lineBottomY - lineHeight;

            graphics.fill(
                    -4 ,
                    lineTopY,
                    chatWidth + 8,
                    lineBottomY,
                    ARGB.black(alpha * backgroundOpacity)
            );
        });

        if (guiGraphics != null) {;
            int windowHeight = guiGraphics.guiHeight();
            int bottomY = Mth.floor((float)(windowHeight - 40) / chatScale);
            List<MinecraftChat.TypingPlayer> entities = MinecraftChat.INSTANCE.getTypingPlayers().stream().toList();
            if (!entities.isEmpty()) {
                guiGraphics.fill(
                        -4,
                        bottomY,
                        chatWidth + 8,
                        bottomY + getLineHeight(),
                        ARGB.color(backgroundOpacity, CommonColors.BLACK)
                );

                int textX = 0;
                for (MinecraftChat.TypingPlayer player : entities) {
                    Component name = player.getName();
                    PlayerFaceRenderer.draw(guiGraphics, player.getSkinTextures(), textX + 1, bottomY + 1, 8, ARGB.color(CommonColors.WHITE, ARGB.color(80, 80, 80)));
                    PlayerFaceRenderer.draw(guiGraphics, player.getSkinTextures(), textX, bottomY, 8, CommonColors.WHITE);
                    textX += 10;
                    guiGraphics.drawString(minecraft.font, name, textX, bottomY + 1, CommonColors.WHITE);
                    textX += minecraft.font.width(name);
                    if (player != entities.getLast()) {
                        textX += 1;
                        guiGraphics.drawString(minecraft.font, ",", textX, bottomY + 1, CommonColors.LIGHT_GRAY);
                    }
                    textX += 4;
                }
                guiGraphics.drawString(minecraft.font, "печатает...", textX, bottomY + 1, CommonColors.LIGHT_GRAY);
            }
        }

        int visibleLineCount = this.forEachEngineLine(alphaCalculator, (engineLine, lineIndex, alpha) -> {
            int lineBottomY = chatBottomY - lineIndex * lineHeight;
            int textY = lineBottomY - textYOffset;
            if (guiGraphics != null) {
                PlayerInfo author = engineLine.getMessage().getAuthor();
                AcceptedMessage engineMessage = engineLine.getMessage().getEngineMessage();
                int darkenColor = ARGB.color(CommonColors.WHITE, ARGB.color(80, 80, 80));
                if (author != null && engineLine.isFirst() && engineMessage.getShowHead()) {
                    PlayerFaceRenderer.draw(guiGraphics, author.getSkin(), 1, textY + 1, 8, ARGB.color(alpha * textOpacity, darkenColor));
                    PlayerFaceRenderer.draw(guiGraphics, author.getSkin(), 0, textY, 8, ARGB.color(alpha * textOpacity, CommonColors.WHITE));
                }
                if (engineLine.isLast()) {
                    int repeats = engineMessage.getRepeat();
                    if (repeats > 1) {
                        String string = "x" + repeats;
                        if (repeats > 999) { string = MAXIMUM_REPEATS_TEXT; }
                        Component text = Component.literal(string).withStyle(ChatFormatting.GOLD);
                        int width = minecraft.font.width(text);
                        int x0 = chatWidth - width;
                        guiGraphics.drawString(minecraft.font, text, x0, textY, ARGB.color(alpha * textOpacity, CommonColors.BLACK), true);
                    }
                    Component debugText = engineLine.getMessage().getDebugText();
                    if (debugText != null && MinecraftChat.INSTANCE.shouldRenderDebugInfo()) {
                        guiGraphics.drawString(
                                minecraft.font,
                                debugText,
                                8 + 4,
                                textY,
                                ARGB.color(alpha * textOpacity, CommonColors.GRAY)
                        );
                    }
                }
            }
            graphics.updatePose(matrix -> {
                matrix.translate(ChatHudRenderKt.CHAT_HEAD_SIZE + 2, 0.0F);
            });
            graphics.handleMessage(
                    textY,
                    alpha * textOpacity,
                    engineLine.getText()
            );
            graphics.updatePose(matrix -> {
                matrix.translate(-ChatHudRenderKt.CHAT_HEAD_SIZE - 2, 0.0F);
            });
        });

        if (chatFocused) {
            int totalPixelHeight = totalLines * lineHeight;
            int visiblePixelHeight = visibleLineCount * lineHeight;
            int scrollbarY = this.chatScrollbarPos * visiblePixelHeight / totalLines - chatBottomY;

            int scrollbarHeight =
                    visiblePixelHeight * visiblePixelHeight / totalPixelHeight;

            if (totalPixelHeight != visiblePixelHeight) {
                int scrollbarAlpha = scrollbarY > 0 ? 170 : 96;
                int scrollbarColor = this.newMessageSinceScroll ? 13382451 : 3355562;
                int scrollbarX = chatWidth + 4;

                graphics.fill(
                        scrollbarX,
                        -scrollbarY,
                        scrollbarX + 2,
                        -scrollbarY - scrollbarHeight,
                        ARGB.color(scrollbarAlpha, scrollbarColor)
                );
                graphics.fill(
                        scrollbarX + 2,
                        -scrollbarY,
                        scrollbarX + 1,
                        -scrollbarY - scrollbarHeight,
                        ARGB.color(scrollbarAlpha, 13421772)
                );
            }
        }

        profiler.pop();
    }

    /**
     * @author lain1wakura
     * @reason Не трогается практически ни кем, проще переписать
     */
    @Overwrite
    public static int getWidth(double widthOption) {
        int i = ClientMixinAccess.INSTANCE.getChatWidth();
        int j = 40;
        return Mth.floor(widthOption * i + j);
    }

    @Override
    public void engine$addMessage(@NonNull EngineChatHudMessage message, boolean isVisible) {
        addEngineMessageToDisplayQueue(message, isVisible);
    }

    @Unique
    private void addEngineMessageToDisplayQueue(EngineChatHudMessage message, boolean isVisible) {
        int chatWidthPx = Mth.floor(this.getWidth() / this.getScale());
        List<EngineChatHudLine> lines = message.resolveLines(chatWidthPx);
        boolean focused = this.isChatFocused();

        for (EngineChatHudLine line : lines) {
            if (focused && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(1);
            }
            this.visibleMessages.addFirst(line);
        }

        while (this.visibleMessages.size() > ClientMixinAccess.INSTANCE.getChatSize()) {
            this.visibleMessages.removeLast();
        }
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$addMessage(Component component, MessageSignature messageSignature, GuiMessageTag guiMessageTag, CallbackInfo ci) {
        MinecraftChat.INSTANCE.storeVanillaGuiMessage(new GuiMessage(this.minecraft.gui.getGuiTicks(), component, messageSignature, guiMessageTag));
        ci.cancel();
    }

    @Inject(
            method = "deleteMessage",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$deleteMessage(MessageSignature messageSignature, CallbackInfo ci) {
        ci.cancel();
    }

    @Redirect(
            method = "scrollChat",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
    )
    public int engine$deleteMessage(List instance) {
        return visibleMessages.size();
    }

//    @Inject(
//            method = "mouseClicked",
//            at = @At(
//                    value = "HEAD"
//            )
//    )
//    public void engine$mouseClicked(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
//        int n = this.getMessageIndex(this.toChatLineX((double)mouseX), this.toChatLineY((double)mouseY));
//        ChatHudLine.Visible toSet = null;
//        if (n >= 0 && n <= visibleMessages.size()) {
//            ChatHudLine.Visible message = visibleMessages.get(n);
//            if (message != null && isChatFocused() && !client.options.hudHidden && !isChatHidden()) {
//                toSet = message;
//            }
//        }
//        MinecraftChat.INSTANCE.updateSelectedMessage(toSet);
//    }
}
