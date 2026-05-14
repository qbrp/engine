package org.lain.engine.client.render.item

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.resources.EngineItemModel

private val BAKED_MODEL_MANAGER = MinecraftClient.modelManager

fun updateForLivingEntity(
    renderState: ItemStackRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    entity: LivingEntity
) {
    clearAndUpdate(
        renderState,
        stack,
        displayContext,
        entity.level(),
        entity as ItemOwner,
        entity.id + displayContext.ordinal
    )
}

fun updateForNonLivingEntity(
    renderState: ItemStackRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    entity: Entity
) {
    clearAndUpdate(renderState, stack, displayContext, entity.level(), null, entity.id)
}

fun clearAndUpdate(
    renderState: ItemStackRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    world: Level?,
    heldItemContext: ItemOwner?,
    seed: Int
) {
    renderState.clear()
    if (!stack.isEmpty) {
        update(renderState, stack, displayContext, world, heldItemContext, seed)
    }
}

fun update(
    renderState: ItemStackRenderState,
    stack: ItemStack,
    displayContext: EngineItemDisplayContext,
    world: Level?,
    heldItemContext: ItemOwner?,
    seed: Int
) {
    val clientWorld = world as? ClientLevel
    val identifier = stack.get(DataComponents.ITEM_MODEL) ?: return
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
    renderState: ItemStackRenderState,
    itemModel: Identifier,
    oversizedInGui: Boolean,
    displayContext: EngineItemDisplayContext,
    world: Level?,
    heldItemContext: ItemOwner?,
    seed: Int
) {
    val clientWorld = world as? ClientLevel
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