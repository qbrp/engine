package org.lain.engine.client.mixin.render;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import org.lain.engine.client.mc.render.TransformationsEditorScreenKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.handItem.ItemRenderState$LayerRenderState")
public class LayerItemRenderStateMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/model/json/Transformation;apply(ZLnet/minecraft/client/util/math/MatrixStack$Entry;)V"
            )
    )
    public void engine$applyAdditionalTransformations(Transformation instance, boolean leftHanded, MatrixStack.Entry entry) {
        Transformation transformations = TransformationsEditorScreenKt.getAdditionalTransformations((ItemRenderState.LayerRenderState) ((Object)this));
        if (transformations != null) {
            transformations.apply(leftHanded, entry);
        } else {
            instance.apply(leftHanded, entry);
        }
    }
}
