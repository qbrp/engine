package org.lain.engine.client.render.player

import kotlinx.serialization.Serializable
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.scene.RenderEntityIdSerializer
import org.lain.engine.client.render.scene.RenderSceneSnapshot
import org.lain.engine.client.render.scene.snapshot
import org.lain.engine.player.get
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.util.ecs.EntityId

@Serializable
data class CharacterSceneSnapshot(
    @Serializable(with = RenderEntityIdSerializer::class)
    val character: EntityId,
    val scene: RenderSceneSnapshot,
) {
    fun revive(storage: NamespacedStorageAccess): PlayerRenderNode {
        require(character in scene.entities) { "Character render entity $character is missing from the snapshot" }
        val restored = scene.restore(storage)
        return PlayerRenderNode(restored.entities.getValue(character), restored.scene)
    }
}

fun GameSession.snapshotCharacterRenderState(): CharacterSceneSnapshot? {
    val node = mainPlayer.get<PlayerExtraction>()?.node ?: return null
    return CharacterSceneSnapshot(
        node,
        renderScene.snapshot(listOf(node))
    )
}
