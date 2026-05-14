package org.lain.engine.client.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.render.item.RenderStateKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public class ItemRenderStateMixin {
    @Shadow
    private int activeLayerCount;

    @Shadow
    private ItemStackRenderState.LayerRenderState[] layers;

    @Inject(
            method = "submit",
            at = @At("HEAD")
    )
    public void engine$transform(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, int k, CallbackInfo ci) {
        ItemTransform engineTransformation = RenderStateKt.getEngineTransformation((ItemStackRenderState) (Object)this);
        if (engineTransformation != null) {
            for (int l = 0; l < this.activeLayerCount; ++l) {
                ItemStackRenderState.LayerRenderState renderState = this.layers[l];
                renderState.setTransform(engineTransformation);
            }
        }

        if (ClientMixinAccess.INSTANCE.getEngineClient().getDeveloperMode()) {
            for (int l = 0; l < this.activeLayerCount; ++l) {
                ItemStackRenderState.LayerRenderState renderState = this.layers[l];
                ItemTransform transformations = RenderStateKt.getAdditionalTransformations(renderState);
                if (transformations != null) {
                    renderState.setTransform(transformations);
                }
            }
        }
    }
}
