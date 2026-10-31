package org.lain.engine.client.render.player

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.kosmx.playerAnim.api.TransformType
import dev.kosmx.playerAnim.core.util.Vec3f
import dev.kosmx.playerAnim.impl.Helper
import dev.kosmx.playerAnim.impl.IAnimatedPlayer
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.resources.PlayerSkin
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.render.scene.render
import org.lain.engine.player.PlayerPart
import kotlin.math.cos
import kotlin.math.sin

private const val ARM_BEND_PIVOT_Y = 4f / 16f
private const val LEG_BEND_PIVOT_Y = 6f / 16f

@Suppress("UnstableApiUsage")
class EquipmentFeatureRenderer(
    context: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
    private val client: EngineClient,
    private val upperPart: Boolean,
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(context) {
    private val renderScene
        get() = client.gameSession?.renderScene

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
    ): Unit {
        entity.getRenderNode()?.withSceneContext { renderNode ->
            if (entity.isSpectator) return@withSceneContext
            val items = renderNode.getComponent<DetachedEquipment>()?.nodes ?: return@withSceneContext
            for (equipment in items) {
                val playerPart = equipment.slot.part
                val modelPart = modelPartOf(playerPart, parentModel)
                if (playerPart == PlayerPart.HEAD || playerPart.upper != upperPart) continue
                poseStack.pushPose()

                val translationX = modelPart.x / 16f
                val translationY = modelPart.y / 16f
                val translationZ = modelPart.z / 16f
                poseStack.translate(translationX, translationY, translationZ)
                poseStack.mulPose(Quaternionf().rotationZYX(modelPart.zRot, modelPart.yRot, modelPart.xRot))
                poseStack.scale(modelPart.xScale, modelPart.yScale, modelPart.zScale)

                if (Helper.isBendEnabled() && entity is IAnimatedPlayer && playerPart.bend) {
                    val anim = entity.playerAnimator_getAnimation()
                    if (anim.isActive) {
                        val data = anim.get3DTransform(
                            playerPart.modelName,
                            TransformType.BEND,
                            Vec3f(0f, 0f, 0f)
                        )

                        val bend: Float = data.getY()
                        val axisf: Float = -data.getX()
                        val axis = Vector3f(cos(axisf), 0f, sin(axisf))

                        val bendPivotY = when (playerPart) {
                            PlayerPart.LEFT_PALM, PlayerPart.RIGHT_PALM -> ARM_BEND_PIVOT_Y
                            PlayerPart.LEFT_FEET, PlayerPart.RIGHT_FEET -> LEG_BEND_PIVOT_Y
                            else -> error("Bend pivot is not defined for $playerPart")
                        }
                        poseStack.translate(0f, bendPivotY, 0f)
                        poseStack.mulPose(Quaternionf().rotateAxis(bend, axis))
                        poseStack.translate(0f, -bendPivotY, 0f)
                    }
                }

                if (playerPart.left && equipment.rotateLeftSide) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                }

                val isLeg = playerPart == PlayerPart.LEFT_LEG || playerPart == PlayerPart.LEFT_FEET || playerPart == PlayerPart.RIGHT_LEG || playerPart == PlayerPart.RIGHT_FEET
                if (ClientMixin.getPlayerSkinThreadSafe(entity.uuid)?.model == PlayerSkin.Model.SLIM && !isLeg && playerPart != PlayerPart.BODY) {
                    // сдвигаем на один пиксель
                    val mirrorXMultiplier = if (equipment.mirrorX && playerPart.left) -1 else 1
                    poseStack.translate(0.0625f * mirrorXMultiplier, 0f, 0f)
                }

                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f))
                equipment.node.render(poseStack, buffers, light, mirrorX = equipment.mirrorX && playerPart.left)
                poseStack.popPose()
            }
        }
    }
}

class HeadEquipmentFeatureRenderer(
    context: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>,
    private val client: EngineClient,
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
    ): Unit {
        entity.getRenderNode()?.withSceneContext { renderNode ->
            if (entity.isSpectator) return@withSceneContext
            val eyeLevel = renderNode.getComponent<EyeLevel>()?.value ?: 2
            val items = renderNode.getComponent<DetachedEquipment>()?.nodes ?: return@withSceneContext
            for (equipment in items) {
                val playerModelPart = modelPartOf(equipment.slot.part, parentModel)
                if (playerModelPart !== parentModel.head) continue
                poseStack.pushPose()
                parentModel.head.translateAndRotate(poseStack)
                if (equipment.slot.followsEyeHeight) {
                    // базовое значение - 2 пикселя
                    poseStack.translate(0f, -(eyeLevel * 0.0625f - 0.125f), 0f)
                }
                translate(poseStack, headTransformation)
                equipment.node.render(poseStack, buffers, light)
                poseStack.popPose()
            }
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
