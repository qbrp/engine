package org.lain.engine.client.render.world

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.*
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.isLowDetailed
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.client.render.item.engineOutfit
import org.lain.engine.client.render.item.updateForLivingEntity
import org.lain.engine.container.Entries
import org.lain.engine.item.EngineItem
import org.lain.engine.item.FireMode
import org.lain.engine.item.Gun
import org.lain.engine.item.ItemAssets
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.mc.engineId
import org.lain.engine.player.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.world.World

data class EnginePlayerRenderState(
    val player: EnginePlayer,
    val entity: Player,
    var mainArmPose: ArmPose = ArmPose.NEUTRAL,
    var minorArmPose: ArmPose = ArmPose.NEUTRAL,
    var detachedEquipment: List<EquipmentRenderState> = emptyList(),
    var skinEyeY: Float = 0f
)

private val ENGINE_PLAYER_RENDER_STATE_KEY = RenderStateDataKey<EnginePlayerRenderState>.create<EnginePlayerRenderState> { "Engine render state" }

data class RenderStateComponent(val renderState: EnginePlayerRenderState) : Component

fun AvatarRenderState.setEngineState(state: EnginePlayerRenderState) {
    setData(ENGINE_PLAYER_RENDER_STATE_KEY, state)
}

fun AvatarRenderState.getEngineState() = getData(ENGINE_PLAYER_RENDER_STATE_KEY)

data class EquipmentRenderState(
    val itemRenderState: ItemStackRenderState,
    val dependsEyeY: Boolean,
    val playerPart: PlayerPart,
    var playerModelPart: ModelPart? = null,
)

fun GameSession.updatePlayerEntityRenderStates(playerTable: EntityTable) = with(world) {
    val renderStates = (MinecraftClient.level?.players() ?: return)
        .mapNotNull { it to (playerTable.client.getPlayer(it) ?: return@mapNotNull null) }
        .filter { (entity, player) -> !player.isLowDetailed }
        .associate { (entity, player) ->
            player to player.replaceOrSet(RenderStateComponent(EnginePlayerRenderState(player, entity))).renderState
        }

    iterate<PlayerEquipment, Entries>() { _, (player), (entries) ->
        val renderState = renderStates[player] ?: return@iterate
        val items = entries
            .filter { it.requireComponent<Outfit>().display == OutfitDisplay.Separated }
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

context(world: World)
private fun isGun(item: EngineItem?) = item?.hasComponent<Gun>() == true

context(world: World)
private fun isGunWithoutSelector(item: EngineItem?): Boolean {
    return (item?.getComponent<Gun>() ?: return false).mode != FireMode.SELECTOR
}

data class PlayerEquipmentItemStacks(val stacks: MutableMap<PersistentId, ItemStack>) : Component

context(world: World)
fun createModelPartEquipmentRenderStates(items: List<EngineItem>, entity: Player, player: EnginePlayer): List<EquipmentRenderState> {
    return items
        .map {
            val outfit = it.requireComponent<Outfit>()
            val model = it.requireComponent<ItemAssets>()
            val part = outfit.parts.first()
            val state = ItemStackRenderState()
            val equipmentStacks = player.getOrSet { PlayerEquipmentItemStacks(mutableMapOf()) }.stacks
            val itemStack = equipmentStacks.computeIfAbsent(it.requireComponent<PersistentIdComponent>().id) {
                val stack = ITEM_STACK_MATERIAL.copy()
                stack.set(
                    DataComponents.ITEM_MODEL,
                    engineId(model.assets["default"] ?: "missingno")
                )
                stack
            }
            state.engineOutfit = outfit
            updateForLivingEntity(
                state,
                itemStack,
                if (part == PlayerPart.HEAD) EngineItemDisplayContext.HEAD else EngineItemDisplayContext.OUTFIT,
                entity,
            )
            EquipmentRenderState(state, outfit.dependsEyeY, part)
        }
}

fun modelPartOf(part: PlayerPart, model: PlayerModel) = when(part) {
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