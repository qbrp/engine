package org.lain.engine.client.render.player

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.EnginePlayerRenderStateHolder
import org.lain.engine.client.handler.LowDetail
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.item.EngineItem
import org.lain.engine.item.FireMode
import org.lain.engine.item.GunFireState
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.ArmPose
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World

data class PlayerRenderState(
    var mainArmPose: ArmPose = ArmPose.NEUTRAL,
    var minorArmPose: ArmPose = ArmPose.NEUTRAL,
    var detachedEquipment: List<DetachedEquipmentNode> = emptyList(),
    var eyeLevel: Int = 2
)

data class PlayerExtraction(val node: EntityId) : Component

data class PlayerRenderNode(val node: EntityId, val scene: RenderScene) {
    fun withSceneContext(statement: RenderScene.(node: EntityId) -> Unit) {
        with(scene) { statement(node) }
    }
}

fun Player.getRenderNode(): PlayerRenderNode? =
    (this as? EnginePlayerRenderStateHolder)?.`engine$getRenderNode`()

fun Player.setRenderNode(node: PlayerRenderNode?) =
    (this as? EnginePlayerRenderStateHolder)?.`engine$setRenderNode`(node)

@Serializable
data class EyeLevel(val value: Int) : Component

context(world: World)
fun EntityId.clearPlayerRenderState(scene: RenderScene) {
    val extraction = removeComponent<PlayerExtraction>() ?: return
    getComponent<MinecraftPlayer>()?.entity?.setRenderNode(null)
    with(scene) {
        extraction.node.getComponent<DetachedEquipment>()?.nodes?.forEach { equipment ->
            equipment.node.destroy()
        }
        extraction.node.destroy()
    }
}

fun World.clearPlayerRenderState(scene: RenderScene) {
    iterate<PlayerExtraction> { player, _ -> player.clearPlayerRenderState(scene) }
}

fun World.extractPlayerRenderState(scene: RenderScene) {
    clearPlayerRenderState(scene)

    iterate<LowDetail, MinecraftPlayer> { player, (isLowDetailed), _ ->
        if (!isLowDetailed) {
            val node = scene.addEntity()
            val extraction = PlayerExtraction(node)
            player.setComponent(extraction)
        }
    }

    extractDetachedEquipmentRenderStates(scene)
    extractPlayerArmPoses(scene)

    iterate<PlayerExtraction, EnginePlayerModel>() { _, (node), model ->
        val eyeLevel = model.eyeLevel
        with(scene) {
            node.setComponent(EyeLevel(eyeLevel))
        }
    }

    iterate<PlayerExtraction, MinecraftPlayer> { _, (node), (entity) ->
        entity.setRenderNode(PlayerRenderNode(node, scene))
    }
}

context(world: World)
fun EngineItem?.isGunWithoutSafety(): Boolean {
    return (this?.getComponent<GunFireState>() ?: return false).mode != FireMode.SAFETY
}
