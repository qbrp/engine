package org.lain.engine.client.render.player

import net.minecraft.world.entity.player.Player
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.LowDetail
import org.lain.engine.item.EngineItem
import org.lain.engine.item.FireMode
import org.lain.engine.item.GunFireState
import org.lain.engine.item.isGun
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.mc.getEngineState
import org.lain.engine.player.ArmPose
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.Equipment
import org.lain.engine.player.Equippable
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.armPoseOf
import org.lain.engine.player.get
import org.lain.engine.world.World

data class EnginePlayerRenderState(
    val entity: Player,
    var mainArmPose: ArmPose = ArmPose.NEUTRAL,
    var minorArmPose: ArmPose = ArmPose.NEUTRAL,
    var detachedEquipment: List<DetachedEquipmentRenderState> = emptyList(),
    var skinEyeY: Float = 0f
)

data class RenderStateComponent(val renderState: EnginePlayerRenderState) : Component

fun Player.getEngineRenderState(): EnginePlayerRenderState? =
    getEngineState()?.get<RenderStateComponent>()?.renderState

fun GameSession.updatePlayerEntityRenderStates() = with(world) {
    iterate<LowDetail, MinecraftPlayer> { player, (isLowDetailed), (entity) ->
        if (!isLowDetailed) {
            player.setComponent(RenderStateComponent(EnginePlayerRenderState(entity)))
        }
    }

    iterate<RenderStateComponent, PlayerComponent, Equipment> { _, (renderState), (player), (slots) ->
        renderState.detachedEquipment = createDetachedEquipmentRenderStates(
            slots
                .filterValues { item -> item.requireComponent<Equippable>().display == OutfitDisplay.Separated },
            player
        )
    }

    iterate<RenderStateComponent, PlayerInventory, ArmStatus>() { _, (renderState), inventory, armStatus ->
        val extendsArm = armStatus.extend
        val mainHandItem = inventory.mainHandItem
        val offHandItem = inventory.offHandItem
        val holdsMultipleItems = mainHandItem != null && offHandItem != null
        renderState.mainArmPose =
            armPoseOf(
                holdsMultipleItems,
                mainHandItem.isGunWithoutSafety(),
                extendsArm,
                true,
                mainHandItem != null,
                offHandItem != null
            )
        renderState.minorArmPose =
            armPoseOf(
                holdsMultipleItems,
                offHandItem.isGunWithoutSafety(),
                extendsArm,
                false,
                offHandItem != null,
                mainHandItem != null
            )
    }

    iterate<RenderStateComponent, EnginePlayerModel>() { _, (renderState), model ->
        renderState.skinEyeY = model.skinEyeY
    }
}

context(world: World)
fun EngineItem?.isGunWithoutSafety(): Boolean {
    return (this?.getComponent<GunFireState>() ?: return false).mode != FireMode.SAFETY
}
