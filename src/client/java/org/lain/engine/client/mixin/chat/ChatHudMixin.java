package org.lain.engine.client.mixin.chat;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.MinecraftChat;
import org.lain.engine.client.render.ui.ChatHudRenderKt;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    @Unique
    String MAXIMUM_REPEATS_TEXT = "x999+";

    @Shadow protected abstract int getLineHeight();

    @Shadow @Final private Minecraft minecraft;

    @Shadow
    protected abstract boolean isChatHidden();

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    protected abstract double getScale();

    @Shadow
    protected abstract int getWidth();

    @Shadow
    protected abstract int forEachLine(ChatComponent.AlphaCalculator alphaCalculator, ChatComponent.LineConsumer lineConsumer);

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @ModifyConstant(
            method = "addMessageToDisplayQueue",
            constant = @Constant(intValue = 100)
    )
    private int engine$increaseChatLimit1(int original) {
        return ClientMixinAccess.INSTANCE.getChatSize();
    }

    @ModifyConstant(
            method = "addMessageToQueue",
            constant = @Constant(intValue = 100)
    )
    private int engine$increaseChatLimit2(int original) {
        return ClientMixinAccess.INSTANCE.getChatSize();
    }

    @Inject(
            method = "getWidth()I",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$indentWidth(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + ChatHudRenderKt.LINE_INDENT);
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

        int totalLines = this.trimmedMessages.size();
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

        ChatComponent.AlphaCalculator alphaCalculator =
                chatFocused
                        ? ChatComponent.AlphaCalculator.FULLY_VISIBLE
                        : ChatComponent.AlphaCalculator.timeBased(currentTick);

        graphics.updatePose(matrix -> {
            matrix.scale(chatScale, chatScale);
            matrix.translate(4.0F, 0.0F);
        });

        /*
         * Background rectangles
         */
        this.forEachLine(alphaCalculator, (line, lineIndex, alpha) -> {
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

        /*
         * Message rendering
         */
        int visibleLineCount = this.forEachLine(alphaCalculator, new ChatComponent.LineConsumer() {
            @Override
            public void accept(GuiMessage.Line line, int lineIndex, float alpha) {
                int lineBottomY = chatBottomY - lineIndex * lineHeight;
                int textY = lineBottomY - textYOffset;
                graphics.handleMessage(
                        textY,
                        alpha * textOpacity,
                        line.content()
                );
            }
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

//    @Redirect(
//            method = "render",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
//            )
//    )
//    private void engine$redirectScrollbarFill(
//            DrawContext context,
//            int x1, int y1, int x2, int y2, int color
//    ) {
//        context.fill(x1 + ChatHudRenderKt.LINE_INDENT, y1, x2 + ChatHudRenderKt.LINE_INDENT, y2, color);
//    }

//    /**
//     * @author Lain1wakura
//     * @reason Engine полностью меняет рендеринг чата, так что смысла мелочиться нет
//     */
//    @Overwrite
//    public void render(final ChatComponent.ChatGraphicsAccess chatGraphicsAccess, int i, int j, boolean bl) {
//        MinecraftChat chat = MinecraftChat.INSTANCE;
//        int lineHeightPx;
//        int alpha;
//
//        if (this.isChatHidden()) {
//            return;
//        }
//
//        int visibleLineCount = this.getLinesPerPage();
//        int totalMessages = this.trimmedMessages.size();
//
////        if (totalMessages <= 0) {
////            return;
////        }
//
//        ProfilerFiller profiler = Profiler.get();
//        profiler.push("chat");
//
//        float chatScale = (float)this.getScale();
//        int chatWidth = Mth.ceil(this.getWidth() / chatScale);
//        int windowHeight = context.getScaledWindowHeight();
//
//        context.getMatrices().pushMatrix();
//        context.getMatrices().scale(chatScale, chatScale);
//        context.getMatrices().translate(4.0f, 0.0f);
//
//        int bottomY = MathHelper.floor((float)(windowHeight - 40) / chatScale);
//
//        int hoveredMessageIndex = this.getMessageIndex(
//                this.toChatLineX(mouseX),
//                this.toChatLineY(mouseY)
//        );
//
//        MinecraftChat.ChatLineData selectedMessage;
//        if (hoveredMessageIndex >= 0 && hoveredMessageIndex <= this.visibleMessages.size()) {
//            selectedMessage = chat.getChatHudLineData(this.visibleMessages.get(hoveredMessageIndex));
//        } else {
//            selectedMessage = null;
//        }
//
//        float chatOpacity = this.client.options.getChatOpacity().getValue().floatValue() * 0.9f + 0.1f;
//        float backgroundOpacitySetting = this.client.options.getTextBackgroundOpacity().getValue().floatValue();
//        double lineSpacing = this.client.options.getChatLineSpacing().getValue();
//
//        List<MinecraftChat.TypingPlayer> entities = MinecraftChat.INSTANCE.getTypingPlayers().stream().toList();
//
//        if (!entities.isEmpty()) {
//            context.fill(
//                    0, bottomY,
//                    MathHelper.floor(getWidth() / getChatScale()), bottomY + getLineHeight(),
//                    ColorHelper.withAlpha(backgroundOpacitySetting, Colors.BLACK)
//            );
//
//            int textX = 0;
//            for (MinecraftChat.TypingPlayer player : entities) {
//                Text name = player.getName();
//                PlayerSkinDrawer.draw(context, player.getSkinTextures(), textX + 1, bottomY + 1, 8, ColorHelper.withAlpha(Colors.WHITE, ColorHelper.getArgb(80, 80, 80)));
//                PlayerSkinDrawer.draw(context, player.getSkinTextures(), textX, bottomY, 8, Colors.WHITE);
//                textX += 10;
//                context.drawTextWithShadow(client.textRenderer, name, textX, bottomY + 1, Colors.WHITE);
//                textX += client.textRenderer.getWidth(name);
//                if (player != entities.getLast()) {
//                    textX += 1;
//                    context.drawTextWithShadow(client.textRenderer, ",", textX, bottomY + 1, Colors.LIGHT_GRAY);
//                }
//                textX += 4;
//            }
//            context.drawTextWithShadow(client.textRenderer, "печатает...", textX, bottomY + 1, Colors.LIGHT_GRAY);
//        }
//
//        int lineOffsetY = (int)Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
//
//        ChatHudRenderKt.forEachVisibleLine(
//                getVisibleLineCount(),
//                currentTick,
//                focused,
//                bottomY,
//                getLineHeight(),
//                visibleMessages,
//                scrolledLines,
//                (age -> (float)getMessageOpacityMultiplier(age)),
//                (x1, y1, y2, line, messageIndex, backgroundOpacity) -> {
//                    int color = Colors.BLACK;
//                    if (line != null) {
//                        MinecraftChat.ChatLineData chatLineData = MinecraftChat.INSTANCE.getChatHudLineData(line);
//                        if (chatLineData != null) {
//                            Integer color2 = chatLineData.getMessage().getEngineMessage().getBackgroundColorInt();
//                            if (color2 != null) {
//                                color = color2;
//                            }
//                        }
//                    }
//                    context.fill(x1 - 2, y1, x1 + chatWidth + 4 + 4, y2, ColorHelper.withAlpha(backgroundOpacity * backgroundOpacitySetting, color));
//                });
//
//        int renderedLineCount = ChatHudRenderKt.forEachVisibleLine(
//                getVisibleLineCount(),
//                currentTick,
//                focused,
//                bottomY,
//                getLineHeight(),
//                visibleMessages,
//                scrolledLines,
//                (age -> (float)getMessageOpacityMultiplier(age)),
//                (x1, y1, y2, line, messageIndex, backgroundOpacity) -> {
//                    int textY = y2 + lineOffsetY;
//                    assert line != null;
//                    context.drawTextWithShadow(
//                            this.client.textRenderer,
//                            line.content(),
//                            x1 + 10,
//                            textY,
//                            ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.WHITE)
//                    );
//                    int j = y2 + lineOffsetY;
//
//                    MinecraftChat.ChatLineData data = chat.getChatHudLineData(line);
//                    if (data != null && data.isFirst()) {
//                        MinecraftChat.ChatMessageData message = data.getMessage();
//                        AcceptedMessage engineMessage = message.getEngineMessage();
//                        PlayerListEntry playerListEntry = message.getAuthor();
//                        if (playerListEntry != null && engineMessage.getShowHead()) {
//                            PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), 1, y1 + 1, 8, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, ColorHelper.getArgb(80, 80, 80)));
//                            PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), 0, y1, 8, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.WHITE));
//                        }
//                        int repeats = engineMessage.getRepeat();
//                        if (repeats > 1) {
//                            String string = "x" + repeats;
//                            if (repeats > 999) { string = MAXIMUM_REPEATS_TEXT; }
//                            Text text = Text.literal(string).formatted(Formatting.GOLD);
//                            int width = client.textRenderer.getWidth(text);
//                            int x0 = chatWidth +  - width;
//                            context.drawText(client.textRenderer, text, x0, y1, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.BLACK), true);
//                        }
//                    }
//
//                    if (data != null && data.isLast() && chat.shouldRenderDebugInfo()) {
//                        Text debugText = data.getMessage().getDebugText();
//                        if (debugText != null) {
//                            context.drawTextWithShadow(
//                                    this.client.textRenderer,
//                                    debugText,
//                                    x1 + 8 + client.textRenderer.getWidth(line.content()) + 4,
//                                    j,
//                                    ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.GRAY)
//                            );
//                        }
//                    }
//
//                    int selectionAlpha = 0;
//                    if (data != null) {
//                        if (selectedMessage != null) {
//                            if (data.getMessage().equals(selectedMessage.getMessage())) {
//                                selectionAlpha += 30;
//                            }
//                        }
//
//                        MinecraftChat.ChatMessageData msg = chat.getSelectedMessage();
//                        if (msg != null && data.getMessage().equals(msg)){
//                            selectionAlpha += 30;
//                            if (ClientMixinAccess.INSTANCE.getChatClipboardCopyTicksElapsed() <= 2) {
//                                selectionAlpha += 30;
//                            }
//                        }
//
//                        if (selectionAlpha > 0) {
//                            context.fill(-2, y1, x1 + chatWidth + 4 + 4, y2, ColorHelper.withAlpha(selectionAlpha, Colors.WHITE));
//                        }
//                    }
//                });
//
//        long queuedMessages = this.client.getMessageHandler().getUnprocessedMessageCount();
//        if (queuedMessages > 0L) {
//            alpha = (int)(128.0f * chatOpacity);
//            lineHeightPx = (int)(255.0f * backgroundOpacitySetting);
//
//            context.getMatrices().pushMatrix();
//            context.getMatrices().translate(0.0f, bottomY);
//
//            context.fill(-2, 0, chatWidth + 4, 9, lineHeightPx << 24);
//            context.drawTextWithShadow(
//                    this.client.textRenderer,
//                    Text.translatable("chat.queue", queuedMessages),
//                    0,
//                    1,
//                    ColorHelper.withAlpha(alpha, Colors.WHITE)
//            );
//
//            context.getMatrices().popMatrix();
//        }
//
//        if (focused) {
//            lineHeightPx = this.getLineHeight();
//            int totalHeight = totalMessages * lineHeightPx;
//            int visibleHeight = renderedLineCount * lineHeightPx;
//
//            int scrollY = this.scrolledLines * visibleHeight / totalMessages - bottomY;
//            int scrollbarHeight = visibleHeight * visibleHeight / totalHeight;
//
//            if (totalHeight != visibleHeight) {
//                int scrollbarAlpha = scrollY > 0 ? 170 : 96;
//                int scrollbarColor = this.hasUnreadNewMessages ? 0xCC3333 : 0x3333AA;
//                int scrollbarX = chatWidth;
//
//                context.fill(
//                        scrollbarX,
//                        -scrollY,
//                        scrollbarX + 2,
//                        -scrollY - scrollbarHeight,
//                        ColorHelper.withAlpha(scrollbarAlpha, scrollbarColor)
//                );
//                context.fill(
//                        scrollbarX + 2,
//                        -scrollY,
//                        scrollbarX + 1,
//                        -scrollY - scrollbarHeight,
//                        ColorHelper.withAlpha(scrollbarAlpha, 0xCCCCCC)
//                );
//            }
//        }
//
//        context.getMatrices().popMatrix();
//        profiler.pop();
//    }


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

    @Redirect(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V"
            )
    )
    private void engine$redirectVisibleMessagesAdd(
            List<GuiMessage.Line> list,
            Object element,
            GuiMessage message,
            @Local(ordinal = 1) int line,
            @Local(ordinal = 1) boolean isEnd,
            @Local FormattedCharSequence text
    ) {
        if (!(element instanceof GuiMessage.Line)) {
            return;
        }

        boolean cancel = MinecraftChat.INSTANCE.storeGuiMessage(
                message,
                (GuiMessage.Line)element,
                line == 0,
                isEnd,
                0
        );

        if (!cancel) {
            list.addFirst((GuiMessage.Line)element);
        }
    }

    @Redirect(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;removeLast()Ljava/lang/Object;"
            )
    )
    private Object engine$redirectVisibleMessageRemove(List<GuiMessage.Line> instance) {
        int index =  instance.size() - 1;
        GuiMessage.Line line = instance.get(index);
        MinecraftChat.INSTANCE.deleteGuiMessage(line);
        return instance.removeLast();
    }

    @Redirect(
            method = "addMessageToDisplayQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"
            )
    )
    private List<FormattedCharSequence> engine$breakRenderedChatMessageLines(GuiMessage instance, Font font, int i) {
        return ComponentRenderUtils.wrapComponents(instance.content(), i - font.width(MAXIMUM_REPEATS_TEXT), font);
    }

    @Inject(
            method = "logChatMessage",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private void engine$logChatMessage(GuiMessage guiMessage, CallbackInfo ci) {
        boolean isEngineMessage = MinecraftChat.INSTANCE.isMessageStored(guiMessage);
        if (isEngineMessage) {
            ci.cancel();
        }
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

    @Unique
    private static double getMessageOpacityMultiplier(int age) {
        double d = (double)age / 200.0;
        d = 1.0 - d;
        d *= 10.0;
        d = Mth.clamp((double)d, (double)0.0, (double)1.0);
        d *= d;
        return d;
    }
}
