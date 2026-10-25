package org.lain.engine.client.render.player

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.PlayerSkin
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.render.item.renderEngineItem
import org.lain.engine.client.render.item.TransformationRenderInstance
import org.lain.engine.player.PlayerPart

class EquipmentFeatureRenderer(
    context: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
    private val upperPart: Boolean,
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(context) {
    // совместимость с EmoteCraft
    @Suppress("unused")
    fun isUpperPart(): Boolean = upperPart

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
            val playerPart = equipment.slot.part
            val equipmentIsUpperPart = when (playerPart) {
                PlayerPart.HEAD -> continue
                PlayerPart.LEFT_ARM, PlayerPart.RIGHT_ARM,
                PlayerPart.LEFT_PALM, PlayerPart.RIGHT_PALM -> true
                PlayerPart.BODY, PlayerPart.LEFT_LEG, PlayerPart.RIGHT_LEG,
                PlayerPart.LEFT_FEET, PlayerPart.RIGHT_FEET -> false
            }
            if (equipmentIsUpperPart != upperPart) continue
            poseStack.pushPose()

            equipment.playerModelPart?.translateAndRotate(poseStack)

            if (equipment.rotateLeftSide && playerPart.left) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
            }

            if (ClientMixin.getPlayerSkinThreadSafe(entity.uuid)?.model == PlayerSkin.Model.SLIM) {
                // сдвигаем на один пиксель
                poseStack.translate(0.0625f, 0f, 0f)
            }

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
                entity.id,
                TransformationRenderInstance.Equipment(entity.uuid, equipment.slot.id),
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
        if (entity.isSpectator) return
        val renderState = entity.getEngineRenderState() ?: return
        for (equipment in renderState.detachedEquipment) {
            if (equipment.playerModelPart !== parentModel.head) continue
            poseStack.pushPose()
            parentModel.head.translateAndRotate(poseStack)
            if (equipment.slot.followsEyeHeight) {
                // базовое значение - 2 пикселя
                poseStack.translate(0f, -(renderState.eyeLevel * 0.0625f - 0.125f), 0f)
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
                entity.id,
                TransformationRenderInstance.Equipment(entity.uuid, equipment.slot.id),
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
