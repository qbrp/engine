package org.lain.engine.client.mixin.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessage.Line.class)
public class GuiMessageLineMixin {
    @Inject(
            method = "tag",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$indicator(CallbackInfoReturnable<GuiMessageTag> cir) {
        cir.setReturnValue(null);
    }
}
