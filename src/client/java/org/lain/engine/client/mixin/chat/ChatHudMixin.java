package org.lain.engine.client.mixin.chat;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import org.lain.engine.client.chat.AcceptedMessage;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.MinecraftChat;
import org.lain.engine.client.mc.render.ChatHudRenderKt;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Unique
    String MAXIMUM_REPEATS_TEXT = "x999+";

    @Shadow protected abstract int getLineHeight();

    @Shadow @Final private MinecraftClient client;

    @Shadow public abstract double getChatScale();

    @Shadow public abstract int getVisibleLineCount();

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow private int scrolledLines;

    @Shadow public abstract int getWidth();

    @Shadow protected abstract int getMessageIndex(double chatLineX, double chatLineY);

    @Shadow protected abstract double toChatLineX(double x);

    @Shadow protected abstract double toChatLineY(double y);

    @Shadow public abstract boolean isChatFocused();

    @Shadow protected abstract boolean isChatHidden();

    @Shadow @Final private List<ChatHudLine> messages;

    @Shadow
    protected abstract int getIndicatorX(ChatHudLine.Visible line);

    @Shadow
    protected abstract void drawIndicatorIcon(DrawContext context, int x, int y, MessageIndicator.Icon icon);

    @Shadow
    private boolean hasUnreadNewMessages;

    @ModifyConstant(
            method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            constant = @Constant(intValue = 100)
    )
    private int engine$increaseChatLimit1(int original) {
        return ClientMixinAccess.INSTANCE.getChatSize();
    }

    @ModifyConstant(
            method = "addVisibleMessage",
            constant = @Constant(intValue = 100)
    )
    private int engine$increaseChatLimit2(int original) {
        return ClientMixinAccess.INSTANCE.getChatSize();
    }

    // Нам не нужны индикаторы, показывающие, что сообщение изменено, оно небезопасно и т.д.
    @Inject(
            method = "getIndicatorAt",
            at=@At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getIndicatorAt(double mouseX, double mouseY, CallbackInfoReturnable<MessageIndicator> cir) {
        cir.setReturnValue(null);
    }

    @Inject(
            method = "getWidth()I",
            at = @At("RETURN"),
            cancellable = true
    )
    public void engine$indentWidth(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + ChatHudRenderKt.LINE_INDENT);
    }

    @Inject(
            method = "isXInsideIndicatorIcon",
            at=@At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getIndicatorAt(double x, ChatHudLine.Visible line, MessageIndicator indicator, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
            )
    )
    private void engine$redirectScrollbarFill(
            DrawContext context,
            int x1, int y1, int x2, int y2, int color
    ) {
        context.fill(x1 + ChatHudRenderKt.LINE_INDENT, y1, x2 + ChatHudRenderKt.LINE_INDENT, y2, color);
    }

    /**
     * @author Lain1wakura
     * @reason Engine полностью меняет рендеринг чата, так что смысла мелочиться нет
     */
    @Overwrite
    public void render(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused) {
        MinecraftChat chat = MinecraftChat.INSTANCE;
        int lineHeightPx;
        int alpha;
        if (this.isChatHidden()) {
            return;
        }

        int visibleLineCount = this.getVisibleLineCount();
        int totalMessages = this.visibleMessages.size();
        if (totalMessages <= 0) {
            return;
        }

        Profiler profiler = Profilers.get();
        profiler.push("chat");

        float chatScale = (float)this.getChatScale();
        int chatWidth = MathHelper.ceil((float)this.getWidth() / chatScale);
        int windowHeight = context.getScaledWindowHeight();

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(chatScale, chatScale);
        context.getMatrices().translate(4.0f, 0.0f);

        int bottomY = MathHelper.floor((float)(windowHeight - 40) / chatScale);

        int hoveredMessageIndex = this.getMessageIndex(
                this.toChatLineX(mouseX),
                this.toChatLineY(mouseY)
        );

        MinecraftChat.ChatLineData selectedMessage;
        if (hoveredMessageIndex >= 0 && hoveredMessageIndex <= this.visibleMessages.size()) {
            selectedMessage = chat.getChatHudLineData(this.visibleMessages.get(hoveredMessageIndex));
        } else {
            selectedMessage = null;
        }

        float chatOpacity = this.client.options.getChatOpacity().getValue().floatValue() * 0.9f + 0.1f;
        float backgroundOpacitySetting = this.client.options.getTextBackgroundOpacity().getValue().floatValue();
        double lineSpacing = this.client.options.getChatLineSpacing().getValue();

        List<PlayerListEntry> entities = ClientMixinAccess.INSTANCE.getTypingPlayers().stream().toList();

        if (!entities.isEmpty()) {
            context.fill(
                    0, bottomY,
                    MathHelper.floor(getWidth() / getChatScale()), bottomY + getLineHeight(),
                    ColorHelper.withAlpha(backgroundOpacitySetting, Colors.BLACK)
            );

            int textX = 0;
            for (PlayerListEntry player : entities) {
                Text name = ClientMixinAccess.INSTANCE.getDisplayName(player);
                PlayerSkinDrawer.draw(context, player.getSkinTextures(), textX + 1, bottomY + 1, 8, ColorHelper.withAlpha(Colors.WHITE, ColorHelper.getArgb(80, 80, 80)));
                PlayerSkinDrawer.draw(context, player.getSkinTextures(), textX, bottomY, 8, Colors.WHITE);
                textX += 10;
                context.drawTextWithShadow(client.textRenderer, name, textX, bottomY + 1, Colors.WHITE);
                textX += client.textRenderer.getWidth(name);
                if (player != entities.getLast()) {
                    textX += 1;
                    context.drawTextWithShadow(client.textRenderer, ",", textX, bottomY + 1, Colors.LIGHT_GRAY);
                }
                textX += 4;
            }
            context.drawTextWithShadow(client.textRenderer, "печатает...", textX, bottomY + 1, Colors.LIGHT_GRAY);
        }

        int lineOffsetY = (int)Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);

        ChatHudRenderKt.forEachVisibleLine(
                getVisibleLineCount(),
                currentTick,
                focused,
                bottomY,
                getLineHeight(),
                visibleMessages,
                scrolledLines,
                (age -> (float)getMessageOpacityMultiplier(age)),
                (x1, y1, y2, line, messageIndex, backgroundOpacity) -> {
                    int color = Colors.BLACK;
                    if (line != null) {
                        MinecraftChat.ChatLineData chatLineData = MinecraftChat.INSTANCE.getChatHudLineData(line);
                        if (chatLineData != null) {
                            Integer color2 = chatLineData.getMessage().getEngineMessage().getBackgroundColorInt();
                            if (color2 != null) {
                                color = color2;
                            }
                        }
                    }
                    context.fill(x1 - 2, y1, x1 + chatWidth + 4 + 4, y2, ColorHelper.withAlpha(backgroundOpacity * backgroundOpacitySetting, color));
                });

        int renderedLineCount = ChatHudRenderKt.forEachVisibleLine(
                getVisibleLineCount(),
                currentTick,
                focused,
                bottomY,
                getLineHeight(),
                visibleMessages,
                scrolledLines,
                (age -> (float)getMessageOpacityMultiplier(age)),
                (x1, y1, y2, line, messageIndex, backgroundOpacity) -> {
                    int textY = y2 + lineOffsetY;
                    assert line != null;
                    context.drawTextWithShadow(
                            this.client.textRenderer,
                            line.content(),
                            x1 + 10,
                            textY,
                            ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.WHITE)
                    );
                    int j = y2 + lineOffsetY;

                    MinecraftChat.ChatLineData data = chat.getChatHudLineData(line);
                    if (data != null && data.isFirst()) {
                        MinecraftChat.ChatMessageData message = data.getMessage();
                        AcceptedMessage engineMessage = message.getEngineMessage();
                        PlayerListEntry playerListEntry = message.getAuthor();
                        if (playerListEntry != null && engineMessage.getShowHead()) {
                            PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), 1, y1 + 1, 8, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, ColorHelper.getArgb(80, 80, 80)));
                            PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), 0, y1, 8, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.WHITE));
                        }
                        int repeats = engineMessage.getRepeat();
                        if (repeats > 1) {
                            String string = "x" + repeats;
                            if (repeats > 999) { string = MAXIMUM_REPEATS_TEXT; }
                            Text text = Text.literal(string).formatted(Formatting.GOLD);
                            int width = client.textRenderer.getWidth(text);
                            int x0 = chatWidth +  - width;
                            context.drawText(client.textRenderer, text, x0, y1, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.BLACK), true);
                        }
                    }

                    if (data != null && data.isLast() && chat.shouldRenderDebugInfo()) {
                        Text debugText = data.getMessage().getDebugText();
                        context.drawTextWithShadow(this.client.textRenderer, debugText, x1 + 8 + client.textRenderer.getWidth(line.content()) + 4, j, ColorHelper.withAlpha(backgroundOpacity * chatOpacity, Colors.GRAY));
                    }

                    int selectionAlpha = 0;
                    if (data != null) {
                        if (selectedMessage != null) {
                            if (data.getMessage().equals(selectedMessage.getMessage())) {
                                selectionAlpha += 30;
                            }
                        }

                        MinecraftChat.ChatMessageData msg = chat.getSelectedMessage();
                        if (msg != null && data.getMessage().equals(msg)){
                            selectionAlpha += 30;
                            if (ClientMixinAccess.INSTANCE.getChatClipboardCopyTicksElapsed() <= 2) {
                                selectionAlpha += 30;
                            }
                        }

                        if (selectionAlpha > 0) {
                            context.fill(-2, y1, x1 + chatWidth + 4 + 4, y2, ColorHelper.withAlpha(selectionAlpha, Colors.WHITE));
                        }
                    }
                });

        long queuedMessages = this.client.getMessageHandler().getUnprocessedMessageCount();
        if (queuedMessages > 0L) {
            alpha = (int)(128.0f * chatOpacity);
            lineHeightPx = (int)(255.0f * backgroundOpacitySetting);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0.0f, bottomY);

            context.fill(-2, 0, chatWidth + 4, 9, lineHeightPx << 24);
            context.drawTextWithShadow(
                    this.client.textRenderer,
                    Text.translatable("chat.queue", queuedMessages),
                    0,
                    1,
                    ColorHelper.withAlpha(alpha, Colors.WHITE)
            );

            context.getMatrices().popMatrix();
        }

        if (focused) {
            lineHeightPx = this.getLineHeight();
            int totalHeight = totalMessages * lineHeightPx;
            int visibleHeight = renderedLineCount * lineHeightPx;

            int scrollY = this.scrolledLines * visibleHeight / totalMessages - bottomY;
            int scrollbarHeight = visibleHeight * visibleHeight / totalHeight;

            if (totalHeight != visibleHeight) {
                int scrollbarAlpha = scrollY > 0 ? 170 : 96;
                int scrollbarColor = this.hasUnreadNewMessages ? 0xCC3333 : 0x3333AA;
                int scrollbarX = chatWidth;

                context.fill(
                        scrollbarX,
                        -scrollY,
                        scrollbarX + 2,
                        -scrollY - scrollbarHeight,
                        ColorHelper.withAlpha(scrollbarAlpha, scrollbarColor)
                );
                context.fill(
                        scrollbarX + 2,
                        -scrollY,
                        scrollbarX + 1,
                        -scrollY - scrollbarHeight,
                        ColorHelper.withAlpha(scrollbarAlpha, 0xCCCCCC)
                );
            }
        }

        context.getMatrices().popMatrix();
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
        return MathHelper.floor(widthOption * i + j);
    }

    @Redirect(
            method = "addVisibleMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(ILjava/lang/Object;)V"
            )
    )
    private void engine$redirectVisibleMessagesAdd(
            List<ChatHudLine.Visible> list,
            int index,
            Object element,
            ChatHudLine message,
            @Local(ordinal = 1) int line,
            @Local(ordinal = 1) boolean isEnd,
            @Local OrderedText text
    ) {
        if (!(element instanceof ChatHudLine.Visible)) {
            return;
        }

        boolean cancel = MinecraftChat.INSTANCE.storeChatHudLine(
                message,
                (ChatHudLine.Visible)element,
                line == 0,
                isEnd,
                index
        );

        if (!cancel) {
            list.add(index, (ChatHudLine.Visible)element);
        }
    }

    @Redirect(
            method = "addVisibleMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;remove(I)Ljava/lang/Object;"
            )
    )
    private Object engine$redirectVisibleMessageRemove(
            List<ChatHudLine.Visible> instance, int i
    ) {
        ChatHudLine.Visible line = instance.get(i);
        MinecraftChat.INSTANCE.deleteChatHudLine(line);
        return instance.remove(i);
    }

    @Redirect(
            method = "addVisibleMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/ChatMessages;breakRenderedChatMessageLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/client/font/TextRenderer;)Ljava/util/List;"
            )
    )
    private List<OrderedText> engine$breakRenderedChatMessageLines(StringVisitable message, int width, TextRenderer textRenderer) {
        return ChatMessages.breakRenderedChatMessageLines(message, width - textRenderer.getWidth(MAXIMUM_REPEATS_TEXT), textRenderer);
    }

    @Inject(
            method = "logChatMessage",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private void engine$logChatMessage(ChatHudLine message, CallbackInfo ci) {
        boolean isEngineMessage = MinecraftChat.INSTANCE.isMessageStored(message);
        if (isEngineMessage) {
            ci.cancel();
        }
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "HEAD"
            )
    )
    public void engine$mouseClicked(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        int n = this.getMessageIndex(this.toChatLineX((double)mouseX), this.toChatLineY((double)mouseY));
        ChatHudLine.Visible toSet = null;
        if (n >= 0 && n <= visibleMessages.size()) {
            ChatHudLine.Visible message = visibleMessages.get(n);
            if (message != null && isChatFocused() && !client.options.hudHidden && !isChatHidden()) {
                toSet = message;
            }
        }
        MinecraftChat.INSTANCE.updateSelectedMessage(toSet);
    }

    @Unique
    private static double getMessageOpacityMultiplier(int age) {
        double d = (double)age / 200.0;
        d = 1.0 - d;
        d *= 10.0;
        d = MathHelper.clamp((double)d, (double)0.0, (double)1.0);
        d *= d;
        return d;
    }
}
