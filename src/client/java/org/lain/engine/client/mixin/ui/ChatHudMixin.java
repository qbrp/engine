package org.lain.engine.client.mixin.ui;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.lain.engine.client.mc.ChatHudRenderKt;
import org.lain.engine.client.mc.MinecraftChat;
import org.lain.engine.client.render.ColorsKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow protected abstract int getLineHeight();

    @Shadow @Final private MinecraftClient client;

    @Shadow public abstract double getChatScale();

    @Shadow public abstract int getVisibleLineCount();

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow private int scrolledLines;

    @Shadow public abstract int getWidth();

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
            method = "isXInsideIndicatorIcon",
            at=@At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getIndicatorAt(double x, ChatHudLine.Visible line, MessageIndicator indicator, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @ModifyArg(
            method = "forEachVisibleLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHud$LineConsumer;accept(IIILnet/minecraft/client/gui/hud/ChatHudLine$Visible;IF)V"
            ),
            index = 0
    )
    private int replaceX(int original) {
        return ChatHudRenderKt.LINE_INDENT;
    }
    @Inject(
            method = "render",
            at = @At(
                    value = "TAIL"
            )
    )
    private void engine$render(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ChatHudRenderKt.forEachVisibleLine(
                getVisibleLineCount(),
                currentTick,
                focused,
                MathHelper.floor((float)((float)(context.getScaledWindowHeight() - 40) / getChatScale())),
                getLineHeight(),
                visibleMessages,
                scrolledLines,
                (age -> (float)getMessageOpacityMultiplier(age)),
                (indentedX1, y1, y2, visible, messageIndex, backgroundOpacity) -> {
                    //float f = (float)this.getChatScale();
                    //int k = MathHelper.ceil(((float)this.getWidth() / f));
                    double d = this.client.options.getChatLineSpacing().getValue();
                    float h = this.client.options.getTextBackgroundOpacity().getValue().floatValue();
                    float g = this.client.options.getChatOpacity().getValue().floatValue() * 0.9f + 0.1f;
                    float bgAlpha = backgroundOpacity * h;
                    float contentAlpha = backgroundOpacity * g;
                    int o = (int)Math.round(-8.0 * (d + 1.0) + 4.0 * d);
                    int j = y2 + o;

                    int x1 = 4;
                    MinecraftChat chat = MinecraftChat.INSTANCE;
                    context.fill(0, y1, x1 + ChatHudRenderKt.LINE_INDENT - 4, y2, ColorHelper.withAlpha(bgAlpha, Colors.BLACK));
                    if (visible != null) {
                        MinecraftChat.ChatHudLineData data = MinecraftChat.INSTANCE.getChatHudLineData(visible);
                        if (data != null && data.isFirst()) {
                            PlayerListEntry playerListEntry = data.getMessage().getAuthor();
                            if (playerListEntry != null) {
                                PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), x1, y1, 8, ColorHelper.withAlpha(contentAlpha, Colors.WHITE));
                            }
                        }

                        if (data != null && data.isLast() && chat.shouldRenderDebugInfo()) {
                            Text debugText = data.getMessage().getDebugText();
                            context.drawTextWithShadow(this.client.textRenderer, debugText, indentedX1 + client.textRenderer.getWidth(visible.content()) + 4, j, ColorHelper.withAlpha(contentAlpha, Colors.GRAY));
                        }
                    }
                }
        );
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

        ChatHudLine.Visible visibleLine =
                new ChatHudLine.Visible(message.creationTick(), text, message.indicator(), isEnd);

        boolean cancel = MinecraftChat.INSTANCE.storeChatHudLine(
                message,
                visibleLine,
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
    private Object egnine$redirectVisibleMessageRemove(
            List<ChatHudLine.Visible> instance, int i
    ) {
        ChatHudLine.Visible line = instance.get(i);
        MinecraftChat.INSTANCE.deleteChatHudLine(line);
        return instance.remove(i);
    }

    @Inject(
            method = "logChatMessage",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private void engine$logChatMessage(ChatHudLine message, CallbackInfo ci) {
        boolean isEngineMessage = MinecraftChat.INSTANCE.isEngineMessage(message);
        if (isEngineMessage) {
            ci.cancel();
        }
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
