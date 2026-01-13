package org.lain.engine.client.mixin.chat;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import org.lain.engine.client.mc.MinecraftChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatHudLine.class)
public class ChatHudLineMixin {
    @Inject(
            method = "indicator",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$indicator(CallbackInfoReturnable<MessageIndicator> cir) {
        cir.setReturnValue(null);
    }

    @Inject(
            method = "getIcon",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$icon(CallbackInfoReturnable<MessageIndicator> cir) {
        cir.setReturnValue(null);
    }
}
