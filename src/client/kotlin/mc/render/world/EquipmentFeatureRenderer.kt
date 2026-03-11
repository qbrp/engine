package org.lain.engine.client.mc.render.world

import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.entity.feature.FeatureRenderer
import net.minecraft.client.render.entity.feature.FeatureRendererContext
import net.minecraft.client.render.entity.model.ModelWithHead
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis

/**
 * @see net.minecraft.client.render.entity.feature.HeadFeatureRenderer
 */
class EquipmentFeatureRenderer(
    context: FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>,
) : FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel>(context) {
    override fun render(
        matrixStack: MatrixStack,
        orderedRenderCommandQueue: OrderedRenderCommandQueue,
        i: Int,
        livingEntityRenderState: PlayerEntityRenderState,
        f: Float,
        g: Float
    ) {
        matrixStack.push()
        contextModel.rootPart.applyTransform(matrixStack)
        val items = livingEntityRenderState.getEquipment()
        if (items != null) {
            for (equip in items) {
                matrixStack.push()
                if (equip.modelPart !== (contextModel as ModelWithHead).head) {
                    equip.modelPart.applyTransform(matrixStack)
                    matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f))
                    equip.itemRenderState.render(matrixStack, orderedRenderCommandQueue, i, OverlayTexture.DEFAULT_UV, livingEntityRenderState.outlineColor)
                }
                matrixStack.pop()
            }
        }
        matrixStack.pop()
    }
}

class HeadEquipmentFeatureRenderer(
    context: FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>,
) : FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel>(context) {
    private val headTransformation: HeadTransformation = HeadTransformation.DEFAULT

    override fun render(
        matrixStack: MatrixStack,
        orderedRenderCommandQueue: OrderedRenderCommandQueue,
        i: Int,
        livingEntityRenderState: PlayerEntityRenderState,
        f: Float,
        g: Float
    ) {
        matrixStack.push()
        contextModel.rootPart.applyTransform(matrixStack)
        val items = livingEntityRenderState.getEquipment()
        if (items != null) {
            for (equip in items) {
                matrixStack.push()
                if (equip.modelPart == (contextModel as ModelWithHead).head) {
                    (contextModel as ModelWithHead).applyTransform(matrixStack)
                    translate(matrixStack, headTransformation)
                    equip.itemRenderState.render(matrixStack, orderedRenderCommandQueue, i, OverlayTexture.DEFAULT_UV, livingEntityRenderState.outlineColor)
                }
                matrixStack.pop()
            }
        }
        matrixStack.pop()
    }

    data class HeadTransformation(val yOffset: Float, val skullYOffset: Float, val horizontalScale: Float) {
        companion object {
            val DEFAULT: HeadTransformation = HeadTransformation(0.0f, 0.0f, 1.0f)
        }
    }

    companion object {
        fun translate(matrices: MatrixStack, transformation: HeadTransformation) {
            matrices.translate(0.0f, -0.25f + transformation.yOffset, 0.0f)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f))
            matrices.scale(0.625f, -0.625f, -0.625f)
        }
    }
}

