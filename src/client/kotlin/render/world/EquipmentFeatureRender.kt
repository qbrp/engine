package org.lain.engine.client.render.world

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import org.lain.engine.client.render.item.renderEngineItem
import org.lain.engine.client.render.player.getEngineRenderState

class EquipmentFeatureRenderer(
    context: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(context) {
    override fun render(
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        light: Int,
        entity: AbstractClientPlayer,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTick: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        val items = entity.getEngineRenderState()?.detachedEquipment ?: return
        for (equipment in items) {
            if (equipment.playerModelPart === parentModel.head) continue
            poseStack.pushPose()
            equipment.playerModelPart?.translateAndRotate(poseStack)
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f))
            renderEngineItem(
                equipment.itemStack,
                equipment.displayContext,
                poseStack,
                buffers,
                light,
                OverlayTexture.NO_OVERLAY,
                entity.level(),
                entity,
                entity.id
            )
            poseStack.popPose()
        }
    }
}

class HeadEquipmentFeatureRenderer(
    context: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(context) {
    private val headTransformation: HeadTransformation = HeadTransformation.DEFAULT

    override fun render(
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        light: Int,
        entity: AbstractClientPlayer,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTick: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        val renderState = entity.getEngineRenderState() ?: return
        for (equipment in renderState.detachedEquipment) {
            if (equipment.playerModelPart !== parentModel.head) continue
            poseStack.pushPose()
            parentModel.head.translateAndRotate(poseStack)
            if (equipment.dependsEyeY) {
                poseStack.translate(0f, -renderState.skinEyeY, 0f)
            }
            translate(poseStack, headTransformation)
            renderEngineItem(
                equipment.itemStack,
                equipment.displayContext,
                poseStack,
                buffers,
                light,
                OverlayTexture.NO_OVERLAY,
                entity.level(),
                entity,
                entity.id
            )
            poseStack.popPose()
        }
    }

    data class HeadTransformation(val yOffset: Float, val skullYOffset: Float, val horizontalScale: Float) {
        companion object {
            val DEFAULT: HeadTransformation = HeadTransformation(0.0f, 0.0f, 1.0f)
        }
    }

    companion object {
        fun translate(poseStack: PoseStack, transformation: HeadTransformation) {
            poseStack.translate(0.0f, -0.25f + transformation.yOffset, 0.0f)
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f))
            poseStack.scale(0.625f, -0.625f, -0.625f)
        }
    }
}
