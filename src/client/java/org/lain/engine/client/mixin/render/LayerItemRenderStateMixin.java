package org.lain.engine.client.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.lain.engine.client.mc.render.TransformationsEditorScreenKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public class LayerItemRenderStateMixin {
    @Redirect(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/model/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"
            )
    )
    public void engine$applyAdditionalTransformations(ItemTransform instance, boolean bl, PoseStack.Pose pose) {
        ItemTransform transformations = TransformationsEditorScreenKt.getAdditionalTransformations(
                (ItemStackRenderState.LayerRenderState) ((Object)this)
        );
        if (transformations != null) {
            transformations.apply(bl, pose);
        } else {
            instance.apply(bl, pose);
        }
    }
}
