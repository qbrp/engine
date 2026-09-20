package org.lain.engine.client.mixin.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
    @Accessor("minecraft")
    Minecraft engine$getMinecraft();

    @Accessor("chatScrollbarPos")
    int engine$getChatScrollbarPos();

    @Accessor("newMessageSinceScroll")
    boolean engine$getNewMessageSinceScroll();

    @Accessor("newMessageSinceScroll")
    void engine$setNewMessageSinceScroll(boolean newMessageSinceScroll);

    @Invoker("isChatFocused")
    boolean engine$isChatFocused();

    @Invoker("scrollChat")
    void engine$scrollChat(int amount);

    @Invoker("getLinesPerPage")
    int engine$getLinesPerPage();

    @Invoker("getWidth")
    int engine$getWidth();

    @Invoker("getScale")
    double engine$getScale();
}

