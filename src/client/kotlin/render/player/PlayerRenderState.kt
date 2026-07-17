package org.lain.engine.client.render.player

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.entity.player.Player
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.client.GameSession
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.handler.isLowDetailed
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.getSkin
import org.lain.engine.container.Entries
import org.lain.engine.item.EngineItem
import org.lain.engine.item.FireMode
import org.lain.engine.item.Gun
import org.lain.engine.item.isGun
import org.lain.engine.mc.EntityTable
import org.lain.engine.player.ArmPose
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.Outfit
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.player.PlayerEquipment
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.armPoseOf
import org.lain.engine.player.set
import org.lain.engine.world.World
import kotlin.to

data class EnginePlayerRenderState(
    val player: EnginePlayer,
    val entity: Player,
    var mainArmPose: ArmPose = ArmPose.NEUTRAL,
    var minorArmPose: ArmPose = ArmPose.NEUTRAL,
    var detachedEquipment: List<EquipmentRenderState> = emptyList(),
    var skinEyeY: Float = 0f
)

private val ENGINE_PLAYER_RENDER_STATE_KEY =
    RenderStateDataKey<EnginePlayerRenderState>.create<EnginePlayerRenderState> { "Engine render state" }

data class RenderStateComponent(val renderState: EnginePlayerRenderState) : Component

fun AvatarRenderState.setEngineState(state: EnginePlayerRenderState) {
    setData(ENGINE_PLAYER_RENDER_STATE_KEY, state)
}

fun AvatarRenderState.getEngineState() = getData(ENGINE_PLAYER_RENDER_STATE_KEY)

fun AvatarRenderState.update(player: EnginePlayer) {
    skin = player.getSkin()
}

fun GameSession.updatePlayerEntityRenderStates(playerTable: EntityTable) = with(world) {
    MinecraftClient.level?.players()
        ?.mapNotNull { it to (playerTable.client.getPlayer(it) ?: return@mapNotNull null) }
        ?.filter { (entity, player) -> !player.isLowDetailed }
        ?.forEach { (entity, player) -> player.set(RenderStateComponent(EnginePlayerRenderState(player, entity))) }

    iterate<RenderStateComponent, PlayerEquipment, Entries>() { _, (renderState), (player), (entries) ->
        val items = entries
            .filter { it.requireComponent<Outfit>().display == OutfitDisplay.Separated }
        renderState.detachedEquipment = createModelPartEquipmentRenderStates(items, renderState.entity, player)
    }

    iterate<RenderStateComponent, PlayerInventory, ArmStatus>() { _, (renderState), inventory, armStatus ->
        val extendsArm = armStatus.extend
        renderState.mainArmPose = armPoseOf(true, extendsArm, inventory)
        renderState.minorArmPose = armPoseOf(false, extendsArm, inventory)
    }

    iterate<RenderStateComponent, EnginePlayerModel>() { _, (renderState), model ->
        renderState.skinEyeY = model.skinEyeY
    }
}

context(world: World)
fun armPoseOf(main: Boolean, extendsArm: Boolean, inventory: PlayerInventory): ArmPose {
    val rHand = if (main) inventory.mainHandItem else inventory.offHandItem
    val lHand = if (main) inventory.offHandItem else inventory.offHandItem
    return armPoseOf(
        extendsArm,
        rHand != null,
        main,
        rHand?.isGun() == true,
        rHand.isGunWithoutSelector(),
        lHand?.isGun() == true
    )
}

context(world: World)
private fun EngineItem?.isGunWithoutSelector(): Boolean {
    return (this?.getComponent<Gun>() ?: return false).mode != FireMode.SELECTOR
}