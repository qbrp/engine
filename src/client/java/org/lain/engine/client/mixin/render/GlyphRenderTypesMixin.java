package org.lain.engine.client.mixin.render;

import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.resources.ResourceLocation;
import org.lain.engine.client.render.world.LabelTextRenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderTypes.class)
public abstract class GlyphRenderTypesMixin {
    @Inject(method = "createForColorTexture", at = @At("RETURN"))
    private static void engine$registerColorTexture(
            ResourceLocation texture,
            CallbackInfoReturnable<GlyphRenderTypes> cir
    ) {
        LabelTextRenderTypes.registerColorTexture(cir.getReturnValue().seeThrough(), texture);
    }

    @Inject(method = "createForIntensityTexture", at = @At("RETURN"))
    private static void engine$registerIntensityTexture(
            ResourceLocation texture,
            CallbackInfoReturnable<GlyphRenderTypes> cir
    ) {
        LabelTextRenderTypes.registerIntensityTexture(cir.getReturnValue().seeThrough(), texture);
    }
}
