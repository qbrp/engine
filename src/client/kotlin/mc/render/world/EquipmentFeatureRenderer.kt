package org.lain.engine.client.mc.render.world

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.HeadedModel
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.texture.OverlayTexture

class EquipmentFeatureRenderer(
    context: RenderLayerParent<AvatarRenderState, PlayerModel>,
) : RenderLayer<AvatarRenderState, PlayerModel>(context) {
    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        i: Int,
        entityRenderState: AvatarRenderState,
        f: Float,
        g: Float
    ) {
        poseStack.pushPose()
        parentModel.root().translateAndRotate(poseStack)
        val items = entityRenderState.getEngineState()?.detachedEquipment
        if (items != null) {
            for (equip in items) {
                poseStack.pushPose()
                if (equip.playerModelPart !== (parentModel as HeadedModel).head) {
                    equip.playerModelPart?.translateAndRotate(poseStack)
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f))
                    equip.itemRenderState.submit(
                        poseStack,
                        submitNodeCollector,
                        i,
                        OverlayTexture.NO_OVERLAY,
                        entityRenderState.outlineColor
                    )
                }
                poseStack.popPose()
            }
        }
        poseStack.popPose()
    }
}

class HeadEquipmentFeatureRenderer(
    context: RenderLayerParent<AvatarRenderState, PlayerModel>,
) : RenderLayer<AvatarRenderState, PlayerModel>(context) {
    private val headTransformation: HeadTransformation = HeadTransformation.DEFAULT

    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        i: Int,
        entityRenderState: AvatarRenderState,
        f: Float,
        g: Float
    ) {
        poseStack.pushPose()
        parentModel.root().translateAndRotate(poseStack)
        val renderState = entityRenderState.getEngineState()
        val items = renderState?.detachedEquipment
        if (items != null) {
            for (equip in items) {
                poseStack.pushPose()
                if (equip.playerModelPart == (parentModel as HeadedModel).head) {
                    equip.playerModelPart?.translateAndRotate(poseStack)
                    (parentModel as HeadedModel).translateToHead(poseStack)
                    val skinEyeY = renderState.skinEyeY
                    if (equip.dependsEyeY) {
                        poseStack.translate(0f, -skinEyeY, 0f)
                    }
                    translate(poseStack, headTransformation)

                    equip.itemRenderState.submit(
                        poseStack,
                        submitNodeCollector,
                        i,
                        OverlayTexture.NO_OVERLAY,
                        entityRenderState.outlineColor
                    )
                }
                poseStack.popPose()
            }
        }
        poseStack.popPose()
    }

    data class HeadTransformation(val yOffset: Float, val skullYOffset: Float, val horizontalScale: Float) {
        companion object {
            val DEFAULT: HeadTransformation = HeadTransformation(0.0f, 0.0f, 1.0f)
        }
    }

    companion object {
        fun translate(matrices: PoseStack, transformation: HeadTransformation) {
            matrices.translate(0.0f, -0.25f + transformation.yOffset, 0.0f)
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f))
            matrices.scale(0.625f, -0.625f, -0.625f)
        }
    }
}

