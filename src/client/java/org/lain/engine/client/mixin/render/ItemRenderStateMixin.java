package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mc.render.TransformationsEditorScreenKt;
import org.lain.engine.client.resources.PropertiesKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.class)
public class ItemRenderStateMixin {
    @Shadow
    private int layerCount;

    @Shadow
    private ItemRenderState.LayerRenderState[] layers;

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    public void engine$transform(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, int overlay, int i, CallbackInfo ci) {
        Transformation engineTransformation = PropertiesKt.getEngineTransformation((ItemRenderState) (Object)this);
        if (engineTransformation != null) {
            for (int j = 0; j < this.layerCount; ++j) {
                ItemRenderState.LayerRenderState renderState = this.layers[j];
                renderState.setTransform(engineTransformation);
            }
        }

        if (ClientMixinAccess.INSTANCE.getEngineClient().getDeveloperMode()) {
            for (int j = 0; j < this.layerCount; ++j) {
                ItemRenderState.LayerRenderState renderState = this.layers[j];
                Transformation transformations = TransformationsEditorScreenKt.getAdditionalTransformations(renderState);
                if (transformations != null) {
                    renderState.setTransform(transformations);
                }
            }
        }
    }
}
