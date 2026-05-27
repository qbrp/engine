package org.lain.engine.client.mixin.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GuiMessage.class)
public class GuiMessageMixin {
    @Shadow
    @Final
    private Component content;

    @Inject(
            method = "tag",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$tag(CallbackInfoReturnable<GuiMessageTag> cir) {
        cir.setReturnValue(null);
    }
}
