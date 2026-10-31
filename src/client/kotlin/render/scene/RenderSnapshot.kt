package org.lain.engine.client.render.scene

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.util.ecs.EntityId
import kotlin.collections.component1
import kotlin.collections.component2

@Serializable
data class RenderStateSnapshot(val components: List<Component>)

data class MutableRenderStateSnapshot(val components: MutableList<Component> = mutableListOf()) {
    fun freeze() = RenderStateSnapshot(components.toList())
}

@Serializable
data class RenderSceneSnapshot(val entities: Map<EntityId, RenderStateSnapshot>) {
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
        val EMPTY = RenderSceneSnapshot(emptyMap())
    }
}

internal data class RestoredRenderScene(val scene: RenderScene, val entities: Map<EntityId, EntityId>)

private fun copyRenderComponent(
    component: Component,
    remap: (EntityId) -> EntityId = { it },
): Component = when (component) {
    is RenderEntityReferences -> component.remapEntityReferences(remap)
    is LightSample -> component.copy()
    else -> component
}

fun RenderScene.snapshot(initialEntities: List<EntityId>): RenderSceneSnapshot {
    val snapshots = mutableMapOf<EntityId, MutableRenderStateSnapshot>()
    initialEntities.forEach { it.appendRenderSnapshot(snapshots) }
    return RenderSceneSnapshot(snapshots.mapValues { it.value.freeze() })
}

context(scene: RenderScene)
fun EntityId.appendRenderSnapshot(entities: MutableMap<EntityId, MutableRenderStateSnapshot>): MutableRenderStateSnapshot {
    entities[this]?.let { return it }
    val snapshot = MutableRenderStateSnapshot()
    scene.componentWorld.getSavableComponents(this).forEach { (_, component) ->
        snapshot.components += copyRenderComponent(component)
        val references = component as? RenderEntityReferences
        references?.let {
            it.entityReferences().forEach { entity -> entity.appendRenderSnapshot(entities) }
        }
    }
    entities[this] = snapshot
    return snapshot
}