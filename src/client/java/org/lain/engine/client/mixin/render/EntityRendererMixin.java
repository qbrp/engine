package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.Entity;
import org.lain.engine.client.resources.MeshModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Shadow @Final private EntityRenderState state;

    @Inject(
            method = "shouldRender",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    public void engine$disableCulling(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if ((((Object)this) instanceof ItemFrameEntityRenderer<?>)) {
            ItemFrameEntityRenderState renderState = (ItemFrameEntityRenderState)state;
            ItemRenderState.LayerRenderState layer = ((ItemRenderStateAccessor)renderState.itemRenderState).engine$getFirstLayer();
            BakedModel model = ((ItemLayerRenderStateAccessor) layer).engine$getBakedModel();
            if (model instanceof MeshModel && ((MeshModel)model).getDisableCulling()) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}
