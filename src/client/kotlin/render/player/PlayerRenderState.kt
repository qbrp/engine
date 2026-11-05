package org.lain.engine.client.render.player

import kotlinx.serialization.Serializable
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.world.entity.player.Player
import org.lain.cyberia.ecs.*
import org.lain.engine.EnginePlayerRenderStateHolder
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.handler.LowDetail
import org.lain.engine.client.render.scene.RenderEntityReferences
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.item.EngineItem
import org.lain.engine.item.FireMode
import org.lain.engine.item.GunFireState
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.ArmPose
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.CharacterModelType
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.model
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World

data class PlayerExtraction(val node: EntityId) : Component

@Serializable
data class PlayerRenderNode(
    var mainArmPose: ArmPose = ArmPose.NEUTRAL,
    var offArmPose: ArmPose = ArmPose.NEUTRAL,
    var detachedEquipment: List<DetachedEquipmentNode> = emptyList(),
    var eyeLevel: Int = 2,
    var model: CharacterModelType = CharacterModelType.DEFAULT,
    var skinTexture: String = DefaultPlayerSkin.getDefaultTexture().toString(),
    var scale: Float = 0.7f
) : RenderEntityReferences {
    override fun entityReferences(): List<EntityId> = detachedEquipment.map { it.node }

    override fun remapEntityReferences(remap: (EntityId) -> EntityId): PlayerRenderNode =
        copy(detachedEquipment = detachedEquipment.map { it.copy(node = remap(it.node)) })
}

data class PlayerRenderNodeReference(val node: EntityId, val scene: RenderScene) {
    fun resolve(): PlayerRenderNode? = with(scene) { node.getComponent() }

    fun <R> withSceneContext(statement: RenderScene.(renderNode: PlayerRenderNode) -> R) {
        val renderNode = resolve() ?: return
        with(scene) { statement(renderNode) }
    }
}

fun Player.getRenderNode(): PlayerRenderNodeReference? =
    (this as? EnginePlayerRenderStateHolder)?.`engine$getRenderNode`()

fun Player.setRenderNode(node: PlayerRenderNodeReference?) =
    (this as? EnginePlayerRenderStateHolder)?.`engine$setRenderNode`(node)

context(world: World)
fun EntityId.clearPlayerRenderState(scene: RenderScene) {
    val extraction = removeComponent<PlayerExtraction>() ?: return
    getComponent<MinecraftPlayer>()?.entity?.setRenderNode(null)
    with(scene) {
        extraction.node.getComponent<PlayerRenderNode>()?.detachedEquipment?.forEach { equipment ->
            equipment.node.destroy()
        }
        extraction.node.destroy()
    }
}

fun World.clearPlayerRenderState(scene: RenderScene) {
    iterate<PlayerExtraction> { player, _ -> player.clearPlayerRenderState(scene) }
}

fun World.extractPlayerRenderState(scene: RenderScene, skinTextureManager: SkinTextureManager) {
    clearPlayerRenderState(scene)

    iterate<LowDetail, MinecraftPlayer> { player, (isLowDetailed), _ ->
        if (!isLowDetailed) {
            val node = scene.addEntity { setComponent(PlayerRenderNode()) }
            val extraction = PlayerExtraction(node)
            player.setComponent(extraction)
        }
    }

    extractDetachedEquipmentRenderStates(scene)
    extractPlayerArmPoses(scene)

    iterate<PlayerExtraction>() { player, (node) ->
        val character = player.getComponent<AppliedCharacter>()?.character?.data ?: return@iterate
        val look = player.getComponent<SelectedLook>()?.look ?: return@iterate
        val cachedPlayerSkin = player.getComponent<EnginePlayerSkin>()?.skin
        val profile = character.profile
        val node = with(scene) { node.requireComponent<PlayerRenderNode>() }
        node.eyeLevel = profile.appearance.eyeLevel
        node.model = profile.bodyType.model
        val skinTexture = skinTextureManager.getOrDownloadTextureNullable(look) ?: cachedPlayerSkin?.texture
        skinTexture?.path?.let { node.skinTexture = it }
    }

    iterate<PlayerExtraction, MinecraftPlayer> { _, (node), (entity) ->
        entity.setRenderNode(PlayerRenderNodeReference(node, scene))
    }
}

context(world: World)
fun EngineItem?.isGunWithoutSafety(): Boolean {
    return (this?.getComponent<GunFireState>() ?: return false).mode != FireMode.SAFETY
}
