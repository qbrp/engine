package org.lain.engine.client.render.player

import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.PlayerModel
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAssets
import org.lain.engine.mc.ecs.ITEM_STACK_MATERIAL
import org.lain.engine.mc.ecs.setPreviewItemModel
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPart
import org.lain.engine.player.getOrSet
import org.lain.engine.data.PersistentId
import org.lain.engine.data.persistentId
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.Equippable
import org.lain.engine.world.World

data class DetachedEquipmentRenderState(
    val itemStack: ItemStack,
    val displayContext: EngineItemDisplayContext,
    val dependsEyeY: Boolean,
    val playerPart: PlayerPart,
    var playerModelPart: ModelPart? = null,
)

data class PlayerEquipmentItemStacks(val stacks: MutableMap<PersistentId, ItemStack>) : Component

context(world: World)
fun createDetachedEquipmentRenderStates(
    items: Map<EquipmentSlotId, EngineItem>,
    player: EnginePlayer
): List<DetachedEquipmentRenderState> {
    return items
        .toList()
        .mapNotNull { (slotId, item) ->
            val assets = item.requireComponent<ItemAssets>()
            val slot = world.simulation.namespacedStorage.equipmentSlots[slotId]
                ?: return@mapNotNull null
            val equipmentStacks = player.getOrSet { PlayerEquipmentItemStacks(mutableMapOf()) }.stacks
            val itemStack = equipmentStacks.computeIfAbsent(item.persistentId()) {
                val stack = ITEM_STACK_MATERIAL.copy()
                stack.setPreviewItemModel(assets)
                stack
            }
            DetachedEquipmentRenderState(
                itemStack,
                if (slot.part == PlayerPart.HEAD) EngineItemDisplayContext.HEAD else EngineItemDisplayContext.OUTFIT,
                false,
                slot.part
            )
        }
}

fun modelPartOf(part: PlayerPart, model: PlayerModel<*>): ModelPart = when (part) {
    PlayerPart.HEAD -> model.head
    PlayerPart.LEFT_ARM -> model.leftArm
    PlayerPart.RIGHT_ARM -> model.rightArm
    PlayerPart.LEFT_PALM -> model.leftArm
    PlayerPart.RIGHT_PALM -> model.rightArm
    PlayerPart.BODY -> model.body
    PlayerPart.LEFT_LEG -> model.leftLeg
    PlayerPart.RIGHT_LEG -> model.rightLeg
    PlayerPart.LEFT_FEET -> model.leftLeg
    PlayerPart.RIGHT_FEET -> model.rightLeg
}
