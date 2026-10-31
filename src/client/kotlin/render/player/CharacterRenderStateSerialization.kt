package org.lain.engine.client.render.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.scene.LightSample
import org.lain.engine.client.render.scene.RenderEntityIdSerializer
import org.lain.engine.client.render.scene.RenderEntityReferences
import org.lain.engine.client.render.scene.RenderScene
import org.lain.engine.player.get
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.util.ecs.EntityId

@Serializable
data class RenderStateSnapshot(val components: List<Component>)

@Serializable
data class MutableRenderStateSnapshot(val components: MutableList<Component> = mutableListOf()) {
    fun freeze() = RenderStateSnapshot(components.toList())
}

@Serializable
data class SceneSnapshot(val entities: Map<EntityId, RenderStateSnapshot>) {
    fun revive(storage: NamespacedStorageAccess): RenderScene = restore(storage).scene

    internal fun restore(storage: NamespacedStorageAccess): RestoredRenderScene {
        val scene = RenderScene(storage)
        val restoredIds = entities.keys.associateWith { scene.addEntity() }
        try {
            val restoredComponents = entities.mapValues { (sourceId, snapshot) ->
                snapshot.components.map { component ->
                    copyRenderComponent(component) { target ->
                        requireNotNull(restoredIds[target]) {
                            "Render entity $sourceId references missing entity $target (${component::class.simpleName})"
                        }
                    }
                }
            }

            with(scene) {
                restoredComponents.forEach { (sourceId, components) ->
                    val entity = restoredIds.getValue(sourceId)
                    components.forEach { component ->
                        @Suppress("UNCHECKED_CAST")
                        entity.setComponent(component, componentTypeOf(component) as ComponentType<Component>)
                    }
                }
            }
            return RestoredRenderScene(scene, restoredIds)
        } catch (failure: Throwable) {
            restoredIds.values.forEach { entity ->
                try {
                    scene.destroy(entity)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
            throw failure
        }
    }

    companion object {
        val EMPTY = SceneSnapshot(emptyMap())
    }
}

@Serializable
data class CharacterSceneSnapshot(
    @Serializable(with = RenderEntityIdSerializer::class)
    val character: EntityId,
    val scene: SceneSnapshot,
) {
    fun revive(storage: NamespacedStorageAccess): PlayerRenderNode {
        require(character in scene.entities) { "Character render entity $character is missing from the snapshot" }
        val restored = scene.restore(storage)
        return PlayerRenderNode(restored.entities.getValue(character), restored.scene)
    }
}

internal data class RestoredRenderScene(val scene: RenderScene, val entities: Map<EntityId, EntityId>)

private fun copyRenderComponent(
    component: Component,
    remap: (EntityId) -> EntityId = { it },
): Component = when (component) {
    is RenderEntityReferences -> component.remapEntityReferences(remap)
    is LightSample -> component.copy()
    // Immutable components without entity references can be shared with the snapshot.
    else -> component
}

fun GameSession.snapshotCharacterRenderState(): CharacterSceneSnapshot? {
    val node = mainPlayer.get<PlayerExtraction>()?.node ?: return null
    return with(renderScene) {
        val entities = mutableMapOf<EntityId, MutableRenderStateSnapshot>()
        val equipment = node.getComponent<DetachedEquipment>() ?: return null
        equipment.nodes.forEach {
            val renderState = entities.computeIfAbsent(it.node) { MutableRenderStateSnapshot() }
            renderScene.componentWorld.getSavableComponents(it.node).forEach { (_, component) ->
                renderState.components += copyRenderComponent(component)
            }
        }

        val frozenEntities = entities.mapValuesTo(mutableMapOf()) { (_, state) -> state.freeze() }
        frozenEntities[node] = RenderStateSnapshot(
            listOfNotNull(
                node.getComponent<EyeLevel>(),
                equipment
            ).map { copyRenderComponent(it) }
        )

        CharacterSceneSnapshot(
            node,
            SceneSnapshot(frozenEntities)
        )
    }
}
