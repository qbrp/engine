package org.lain.engine.client.mixin.render;

import net.minecraft.block.SkullBlock;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.lain.engine.client.mc.render.EquipmentRenderState;
import org.lain.engine.client.mc.render.PlayerRenderStateKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(HeadFeatureRenderer.class)
public class HeadFeatureRendererMixin {
    @Shadow
    @Final
    private HeadFeatureRenderer.HeadTransformation headTransformation;

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V",
            at = @At(value = "RETURN")
    )
    public void engine$renderEquipment(MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, int i, LivingEntityRenderState livingEntityRenderState, float f, float g, CallbackInfo ci) {
        if (livingEntityRenderState instanceof PlayerEntityRenderState state) {
            matrixStack.push();
            matrixStack.scale(1f, 1.0f, 1f);
            Model<?> entityModel = ((FeatureRenderer<?, ?>)(Object)this).getContextModel();
            entityModel.getRootPart().applyTransform(matrixStack);
            List<EquipmentRenderState> items = PlayerRenderStateKt.getEquipment(state);
            assert items != null;
            for (EquipmentRenderState equip : items) {
                matrixStack.push();
                if (equip.getModelPart() == ((ModelWithHead)entityModel).getHead()) {
                    ((ModelWithHead)entityModel).applyTransform(matrixStack);
                    HeadFeatureRenderer.translate(matrixStack, headTransformation);
                } else {
                    equip.getModelPart().applyTransform(matrixStack);
                }
                equip.getItemRenderState().render(matrixStack, orderedRenderCommandQueue, i, OverlayTexture.DEFAULT_UV, livingEntityRenderState.outlineColor);
                matrixStack.pop();
            }
            matrixStack.pop();
        }
    }
}
