package org.lain.engine.client.mixin.ui;

import com.llamalad7.mixinextras.sugar.Local;
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
import net.minecraft.util.math.MathHelper;
import org.lain.engine.client.mc.MinecraftChat;
import org.lain.engine.client.render.ColorsKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow protected abstract int getLineHeight();

    @Shadow @Final private MinecraftClient client;

    @Shadow public abstract double getChatScale();

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

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
                    shift = At.Shift.AFTER,
                    ordinal = 1
            )
    )
    private void engine$drawChatHeadsAfterTranslate(
            DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci, @Local ChatHudLine.Visible visible, @Local(ordinal = 18) int y
    ) {
        MinecraftChat.ChatHudLineData data = MinecraftChat.INSTANCE.getChatHudLineData(visible);
        if (data == null || !data.isFirst()) return;

        PlayerListEntry playerListEntry = data.getMessage().getAuthor();
        if (playerListEntry != null) {
            int opacity = (int)(255.0
                    * (focused ? 1.0 : getMessageOpacityMultiplier(currentTick - visible.addedTime()))
                    * (this.client.options.getChatOpacity().getValue() * 0.9 + 0.1));

            int skinY0 = y - this.getLineHeight();
            PlayerSkinDrawer.draw(context, playerListEntry.getSkinTextures(), 0, skinY0, 8, ColorsKt.whiteWithAlpha(opacity));
            context.getMatrices().translate(11f, 0f, 0f);
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"
            )
    )
    private int engine$drawMessageDebugInfoRedirect(DrawContext context, TextRenderer textRenderer, OrderedText text, int x, int y, int color, @Local ChatHudLine.Visible visible) {
        int result = context.drawTextWithShadow(textRenderer, text, x, y, color);

        MinecraftChat chat = MinecraftChat.INSTANCE;
        MinecraftChat.ChatHudLineData data = chat.getChatHudLineData(visible);
        if (data != null && data.isLast() && chat.shouldRenderDebugInfo()) {
            Text debugText = data.getMessage().getDebugText();
            context.drawTextWithShadow(textRenderer, debugText, result + 2, y, 0xAAAAAA);
        }

        // вернуть результат дальше
        return result;
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
