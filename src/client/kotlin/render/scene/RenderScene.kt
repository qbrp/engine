package org.lain.engine.client.render.scene

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import kotlinx.serialization.Serializable
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.IterationComponentAccess
import org.lain.cyberia.ecs.MutableComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.item.AdditionalTransformationsBank
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.client.render.item.TransformationRenderInstance
import org.lain.engine.client.render.item.minecraft
import org.lain.engine.item.ItemStorage
import org.lain.engine.mc.ecs.ITEM_STACK_MATERIAL
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.util.ecs.ComponentWorld
import org.lain.engine.util.ecs.EntityId
import java.util.concurrent.ConcurrentHashMap

class RenderScene(
    val namespacedStorage: NamespacedStorageAccess,
    val componentWorld: ComponentWorld = ComponentWorld(
        Thread.currentThread(),
        ConcurrentHashMap(),
        ItemStorage()
    ),
) : MutableComponentAccess by componentWorld, IterationComponentAccess by componentWorld {

    companion object {
        val EMPTY = RenderScene(ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage()))
    }
}

data class TransformationEditorTarget(val instance: TransformationRenderInstance) : Component

@Serializable
data class PropModel(
    val model: PropModelReference,
    val context: EngineItemDisplayContext
) : Component

@Serializable
data class LightSample(var block: Int) : Component

context(scene: RenderScene)
fun EntityId.render(
    poseStack: PoseStack,
    buffers: MultiBufferSource,
    envLight: Int,
    mirrorX: Boolean = false,
) {
    val light = getComponent<LightSample>()?.block?.takeIf { it > envLight } ?: envLight
    val propModel = getComponent<PropModel>() ?: return
    val meshModel = propModel.model.resolve()
    val modelId = meshModel.properties.model
    val transformationTarget = getComponent<TransformationEditorTarget>()?.instance
    poseStack.pushPose()
    transformationTarget?.let(ClientMixin::pushTransformationRenderInstance)
    try {
        if (propModel.context === EngineItemDisplayContext.OUTFIT) {
            val transformation = modelId
                .let(AdditionalTransformationsBank::get)
                ?.outfit?.minecraft()
                ?: meshModel.outfitTransform
            transformationTarget?.let {
                ClientMixin.onBeforeRenderOutfit(
                    modelId,
                    RenderSystem.getProjectionMatrix(),
                    RenderSystem.getModelViewMatrix(),
                    poseStack.last().pose(),
                    it,
                    mirrorX,
                )
            }
            transformation.apply(mirrorX, poseStack)
            transformationTarget?.let {
                ClientMixin.onAfterRenderOutfit(
                    modelId,
                    poseStack.last().pose(),
                    it,
                    mirrorX,
                )
            }
        }

        MinecraftClient.itemRenderer.render(
            ITEM_STACK_MATERIAL,
            propModel.context.minecraft,
            false,
            poseStack,
            buffers,
            light,
            OverlayTexture.NO_OVERLAY,
            meshModel
        )
    } finally {
        if (transformationTarget != null) {
            ClientMixin.popTransformationRenderInstance()
        }
        poseStack.popPose()
    }
}

