package org.lain.engine.client.mc.render

import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.world.ClientWorld
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.HeldItemContext
import net.minecraft.util.Identifier
import net.minecraft.world.World
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.resources.EngineItemModel

private val BAKED_MODEL_MANAGER = MinecraftClient.bakedModelManager

fun updateForLivingEntity(
    renderState: ItemRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    entity: LivingEntity
) {
    clearAndUpdate(
        renderState,
        stack,
        displayContext,
        entity.entityWorld,
        entity as HeldItemContext,
        entity.id + displayContext.ordinal
    )
}

fun updateForNonLivingEntity(
    renderState: ItemRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    entity: Entity
) {
    clearAndUpdate(renderState, stack, displayContext, entity.entityWorld, null, entity.id)
}

fun clearAndUpdate(
    renderState: ItemRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    world: World?,
    heldItemContext: HeldItemContext?,
    seed: Int
) {
    renderState.clear()
    if (!stack.isEmpty) {
        update(renderState, stack, displayContext, world, heldItemContext, seed)
    }
}

fun update(
    renderState: ItemRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    world: World?,
    heldItemContext: HeldItemContext?,
    seed: Int
) {
    val clientWorld = world as? ClientWorld
    val identifier = stack.get(DataComponentTypes.ITEM_MODEL) ?: return
    renderState.isOversizedInGui = BAKED_MODEL_MANAGER.getItemProperties(identifier).oversizedInGui()
    renderState.setupAdditionalTransformationsEngine(stack, displayContext)
    (BAKED_MODEL_MANAGER.getItemModel(identifier) as? EngineItemModel)?.updateEngine(
        renderState,
        stack,
        displayContext,
        clientWorld,
        heldItemContext,
        seed
    )
}

fun updateItemRenderState(
    renderState: ItemRenderState,
    itemModel: Identifier,
    oversizedInGui: Boolean,
    displayContext: EngineItemDisplayContext,
    world: World?,
    heldItemContext: HeldItemContext?,
    seed: Int
) {
    val clientWorld = world as? ClientWorld
    renderState.isOversizedInGui = oversizedInGui
    (BAKED_MODEL_MANAGER.getItemModel(itemModel) as? EngineItemModel)?.updateEngine(
        renderState,
        null,
        displayContext,
        clientWorld,
        heldItemContext,
        seed
    )
}