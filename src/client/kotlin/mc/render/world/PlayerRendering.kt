package org.lain.engine.client.mc.render.world

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.isLowDetailed
import org.lain.engine.client.mc.render.EngineItemDisplayContext
import org.lain.engine.client.mc.render.updateForLivingEntity
import org.lain.engine.client.resources.OutfitTag
import org.lain.engine.container.Entries
import org.lain.engine.item.*
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.player.*
import org.lain.engine.util.EngineId
import org.lain.engine.util.component.*

data class EnginePlayerRenderState(
    val player: EnginePlayer,
    val entity: PlayerEntity,
    var mainArmPose: ArmPose = ArmPose.NEUTRAL,
    var minorArmPose: ArmPose = ArmPose.NEUTRAL,
    var detachedEquipment: List<EquipmentRenderState> = emptyList(),
    var skinEyeY: Float = 0f
)

private val ENGINE_PLAYER_RENDER_STATE_KEY = RenderStateDataKey<EnginePlayerRenderState>.create<EnginePlayerRenderState> { "Engine render state" }

data class RenderStateComponent(val renderState: EnginePlayerRenderState) : Component

fun PlayerEntityRenderState.setEngineState(state: EnginePlayerRenderState) {
    setData(ENGINE_PLAYER_RENDER_STATE_KEY, state)
}

fun PlayerEntityRenderState.getEngineState() = getData(ENGINE_PLAYER_RENDER_STATE_KEY)

data class EquipmentRenderState(
    val itemRenderState: ItemRenderState,
    val dependsEyeY: Boolean,
    val playerPart: PlayerPart,
    var playerModelPart: ModelPart? = null,
)

fun GameSession.updatePlayerEntityRenderStates(playerTable: EntityTable) {
    val renderStates = playerStorage
        .filter { player -> !player.isLowDetailed }
        .associateWith { player ->
            player.replaceOrSet(
                RenderStateComponent(
                    EnginePlayerRenderState(player, playerTable.client.getEntity(player.id)!!))
            ).renderState
        }

    world.iterate<PlayerEquipment, Entries>() { _, (player), (entries) ->
        val renderState = renderStates[player]!!
        val items = entries
            .filter { it.require<Outfit>().display == OutfitDisplay.Separated }
        renderState.detachedEquipment = createModelPartEquipmentRenderStates(items, renderState.entity, player)
    }

    renderStates.forEach { (enginePlayer, renderState) ->
        val inventory = enginePlayer.require<PlayerInventory>()
        val extends = enginePlayer.require<ArmStatus>().extend

        renderState.mainArmPose = armPoseOf(extends, inventory.mainHandItem != null, true, isGun(inventory.mainHandItem), isGunWithoutSelector(inventory.mainHandItem), isGun(inventory.offHandItem))
        renderState.minorArmPose = armPoseOf(extends, inventory.offHandItem != null, false, isGun(inventory.offHandItem), isGunWithoutSelector(inventory.offHandItem), isGun(inventory.mainHandItem))
        renderState.skinEyeY = enginePlayer.skinEyeY
    }
}

private fun isGun(item: EngineItem?) = item?.has<Gun>() == true

private fun isGunWithoutSelector(item: EngineItem?): Boolean {
    return (item?.get<Gun>() ?: return false).mode != FireMode.SELECTOR
}

data class PlayerEquipmentItemStacks(val stacks: MutableMap<ItemUuid, ItemStack>) : Component

fun createModelPartEquipmentRenderStates(items: List<EngineItem>, entity: PlayerEntity, player: EnginePlayer): List<EquipmentRenderState> {
    return items
        .map {
            val outfit = it.require<Outfit>()
            val model = it.require<ItemAssets>()
            val part = outfit.parts.first()
            val state = ItemRenderState()
            val equipmentStacks = player.getOrSet { PlayerEquipmentItemStacks(mutableMapOf()) }.stacks
            val itemStack = equipmentStacks.computeIfAbsent(it.uuid) {
                val stack = ITEM_STACK_MATERIAL.copy()
                stack.set(
                    DataComponentTypes.ITEM_MODEL,
                    EngineId(model.assets["default"] ?: "missingno")
                )
                stack.set(
                    OutfitTag.TYPE,
                    OutfitTag(outfit)
                )
                stack
            }
            updateForLivingEntity(
                state,
                itemStack,
                if (part == PlayerPart.HEAD) EngineItemDisplayContext.HEAD else EngineItemDisplayContext.OUTFIT,
                entity,
            )
            EquipmentRenderState(state, outfit.dependsEyeY, part)
        }
}

fun modelPartOf(part: PlayerPart, model: PlayerEntityModel) = when(part) {
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