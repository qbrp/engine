package org.lain.engine.client.render.item

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.resources.MeshModel
import org.lain.engine.mc.ecs.ENGINE_ITEM_MODEL_COMPONENT

fun getEngineItemModel(stack: ItemStack): BakedModel? {
    val id = stack.get(ENGINE_ITEM_MODEL_COMPONENT) ?: return null
    return MinecraftClient.modelManager.getModel(id)
}

fun renderEngineItem(
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    poseStack: PoseStack,
    buffers: MultiBufferSource,
    light: Int,
    overlay: Int,
    level: Level? = null,
    entity: LivingEntity? = null,
    seed: Int = 0
) {
    val model = getEngineItemModel(stack) ?: return
    poseStack.pushPose()
    if (displayContext == EngineItemDisplayContext.OUTFIT) {
        val engineModel = model as? MeshModel
        val modelId = stack.get(ENGINE_ITEM_MODEL_COMPONENT)
        val transformation = modelId
            ?.let(AdditionalTransformationsBank::get)
            ?.outfit
            ?: engineModel?.engineTransform?.outfit
        ClientMixin.onBeforeRenderOutfit(
            stack,
            RenderSystem.getProjectionMatrix(),
            RenderSystem.getModelViewMatrix(),
            poseStack.last().pose(),
        )
        transformation?.minecraft()?.apply(false, poseStack)
        ClientMixin.onAfterRenderOutfit(stack, poseStack.last().pose())
    }
    MinecraftClient.itemRenderer.render(
        stack,
        displayContext.minecraft,
        false,
        poseStack,
        buffers,
        light,
        overlay,
        model
    )
    poseStack.popPose()
}
