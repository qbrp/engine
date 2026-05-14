package org.lain.engine.client.mixin.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> engine$getVisibleMessages();

    @Accessor("chatScrollbarPos")
    int engine$getScrolledLines();

    @Accessor("allMessages")
    List<GuiMessage> engine$getMessages();

    @Invoker("getLineHeight")
    int engine$getLineHeight();
}

