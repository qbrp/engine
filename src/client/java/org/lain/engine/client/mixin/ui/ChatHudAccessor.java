package org.lain.engine.client.mixin.ui;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> engine$getVisibleMessages();

    @Accessor("messages")
    List<ChatHudLine> engine$getMessages();

    @Invoker("getLineHeight")
    int engine$getLineHeight();

    @Invoker("getMessageLineIndex")
    int engine$getMessageLineIndex(double chatLineX, double chatLineY);

    @Invoker("toChatLineX")
    double engine$toChatLineX(double x);

    @Invoker("toChatLineY")
    double engine$toChatLineY(double y);
}

