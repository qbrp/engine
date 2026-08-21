package org.lain.engine.client.mixin.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.lain.engine.client.mc.ClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBarRenderer.class)
public class ExperienceBarRendererMixin {
    @Inject(
            method = "renderBackground",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void engine$hideExperienceBar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientMixin.INSTANCE.isHotbarIndicatorsVisible()) {
            ci.cancel();
        }
    }
}
