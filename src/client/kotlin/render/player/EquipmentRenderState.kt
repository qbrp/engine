package org.lain.engine.client.render.player

import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.PlayerModel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAssets
import org.lain.engine.mc.ecs.ITEM_STACK_MATERIAL
import org.lain.engine.mc.ecs.setPreviewItemModel
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Outfit
import org.lain.engine.player.PlayerPart
import org.lain.engine.player.getOrSet
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.world.World

data class EquipmentRenderState(
    val itemStack: ItemStack,
    val displayContext: EngineItemDisplayContext,
    val dependsEyeY: Boolean,
    val playerPart: PlayerPart,
    var playerModelPart: ModelPart? = null,
)

data class PlayerEquipmentItemStacks(val stacks: MutableMap<PersistentId, ItemStack>) : Component

context(world: World)
fun createModelPartEquipmentRenderStates(
    items: List<EngineItem>,
    entity: Player,
    player: EnginePlayer
): List<EquipmentRenderState> {
    return items
        .map {
            val outfit = it.requireComponent<Outfit>()
            val assets = it.requireComponent<ItemAssets>()
            val part = outfit.parts.first()
            val equipmentStacks = player.getOrSet { PlayerEquipmentItemStacks(mutableMapOf()) }.stacks
            val itemStack = equipmentStacks.computeIfAbsent(it.requireComponent<PersistentIdComponent>().id) {
                val stack = ITEM_STACK_MATERIAL.copy()
                stack.setPreviewItemModel(assets)
                stack
            }
            EquipmentRenderState(
                itemStack,
                if (part == PlayerPart.HEAD) EngineItemDisplayContext.HEAD else EngineItemDisplayContext.OUTFIT,
                outfit.dependsEyeY,
                part
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
