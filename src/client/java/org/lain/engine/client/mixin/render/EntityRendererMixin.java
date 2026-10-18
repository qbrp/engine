package org.lain.engine.client.mixin.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.lain.engine.client.render.item.ModelPipelineKt;
import org.lain.engine.client.resources.MeshModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void engine$disableItemFrameCulling(
            Entity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof ItemFrame itemFrame) {
            BakedModel model = ModelPipelineKt.getEngineItemModel(itemFrame.getItem());
            if (model instanceof MeshModel metadata && metadata.getProperties().getDisableCulling()) {
                cir.setReturnValue(true);
            }
        }
        if (entity instanceof AbstractClientPlayer) {

        }
    }
}
