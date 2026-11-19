package org.lain.engine.script

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.exists
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.PersistentId
import org.lain.engine.data.UnloadComponent
import org.lain.engine.mc.ecs.MinecraftEntity
import org.lain.engine.mc.ecs.MinecraftItem
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.PlayerComponent
import org.lain.engine.script.lua.library.LuaEntityComponent
import org.lain.engine.script.lua.library.LuaCachedComponentBinding
import org.lain.engine.server.ServerHandler
import org.lain.engine.server.replication.Changes
import org.lain.engine.server.replication.PlayerReplicationState
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.property.PropertyEdit
import org.lain.engine.util.property.PropertyEditResult
import org.lain.engine.util.property.Property
import org.lain.engine.util.property.PropertyTreeBuilder
import org.lain.engine.util.property.PropertyLocation
import org.lain.engine.util.property.PropertyPath
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyTree
import org.lain.engine.util.property.ServerPropertyTree
import org.lain.engine.util.property.edit
import org.lain.engine.util.property.jvmProperty
import org.lain.engine.util.property.inspectScriptValue
import org.lain.engine.util.property.propertyEditNode
import org.lain.engine.world.World
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.reflect.full.starProjectedType

data class EntityInspectionSnapshot(
    val data: EntityInspectionSnapshotDto,
    val livePaths: Map<String, Map<PropertyPathDto, PropertyPath>>,
)

@Serializable
data class EntityInspectionSnapshotDto(
    val id: EntityId,
    val clientSide: Boolean,
    val snapshotId: String,
    val components: List<ComponentInspectionSnapshot>,
)

@Serializable
data class ComponentInspectionSnapshot(val type: String, val data: ComponentInspectionResult)

@Serializable
sealed class ComponentInspectionResult {
    @Serializable
    @SerialName("value")
    data class Value(val root: Property, val tree: PropertyTree) : ComponentInspectionResult()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ComponentInspectionResult()
}

class EntityInspection(
    val entity: EntityId,
    var rate: Int,
    var lastSnapshotTime: Int = 0
) {
    private val snapshotPaths = linkedMapOf<String, Map<String, Map<PropertyPathDto, PropertyPath>>>()

    fun remember(snapshot: EntityInspectionSnapshot) {
        snapshotPaths[snapshot.data.snapshotId] = snapshot.livePaths
        if (snapshotPaths.size > MAX_RETAINED_SNAPSHOTS) {
            snapshotPaths.remove(snapshotPaths.keys.first())
        }
    }

    fun livePath(snapshotId: String, componentType: String, path: PropertyPathDto): PropertyPath? =
        snapshotPaths[snapshotId]?.get(componentType)?.get(path)

    companion object {
        private const val MAX_RETAINED_SNAPSHOTS = 32
    }
}

data class EntityInspectionComponent(
    val entities: MutableMap<PersistentId, EntityInspection>
) : Component

fun World.tickEntityInspection(handler: ServerHandler) {
    iterate<PlayerComponent, EntityInspectionComponent>() { _, (player), (inspections) ->
        inspections.keys.removeIf { persistentId ->
            val inspection = inspections[persistentId] ?: return@removeIf true
            (!inspection.entity.exists())
                .also { removed ->
                    if (removed) handler.onEntityInspectionAbort(
                        player,
                        persistentId,
                        "Сущность была уничтожена"
                    )
                }
        }
        inspections.forEach { (persistentId, inspection) ->
            if (inspection.lastSnapshotTime-- <= 0) {
                inspection.lastSnapshotTime = inspection.rate
                val snapshot = inspection.entity.inspectionSnapshot()
                inspection.remember(snapshot)
                handler.onEntityDebugSnapshot(player, persistentId, snapshot.data)
            }
        }
    }
}

context(world: World)
fun EntityId.inspectionSnapshot(): EntityInspectionSnapshot {
    val components = world.componentManager.getComponentsMap(this)
    val debugData = mutableListOf<ComponentInspectionSnapshot>()
    val livePaths = mutableMapOf<String, Map<PropertyPathDto, PropertyPath>>()
    components.forEach { (type, component) ->
        if (!component.shouldSkip(type)) {
            val (data, tree) = component.inspectionSnapshot(world, type)
            debugData += data
            if (tree != null) livePaths[type.id] = tree.livePaths
        }
    }
    return EntityInspectionSnapshot(
        EntityInspectionSnapshotDto(this, world.isClient, UUID.randomUUID().toString(), debugData),
        livePaths,
    )
}

private fun Component.shouldSkip(type: ComponentType<*>): Boolean {
    return type == CoreScriptComponents.PLAYER
            || this is LuaEntityComponent
            || this is LuaCachedComponentBinding
            || this is PlayerReplicationState
            || this is Changes
            || this is EntityInspectionComponent
            || this is MinecraftEntity
            || this is MinecraftItem
            || this is MinecraftPlayer
            || this is UnloadComponent
}

private fun Component.inspectionSnapshot(
    world: World,
    type: ComponentType<out Component>,
): Pair<ComponentInspectionSnapshot, ServerPropertyTree?> {
    val component = this@inspectionSnapshot
    val builder = PropertyTreeBuilder(world.componentManager)
    val location = PropertyLocation()
    return try {
        val root = context(builder) {
            when (type) {
                is ScriptComponentType -> {
                    component as ScriptComponent
                    component.inspectionValueNode.inspectScriptValue(location)
                }

                else -> component.jvmProperty(component::class.starProjectedType, location)
            }
        }
        val serverTree = builder.build()
        ComponentInspectionSnapshot(type.id, ComponentInspectionResult.Value(root, serverTree.tree)) to serverTree
    } catch (e: Exception) {
        ScriptEngine.LOGGER.error("Невозможно получить данные для отладки компонента: $type", e)
        ComponentInspectionSnapshot(type.id, ComponentInspectionResult.Error(e.message ?: e.toString())) to null
    }
}

context(world: World)
fun EntityInspection.edit(
    snapshotId: String,
    componentTypeId: String,
    propertyPath: PropertyPathDto,
    operation: PropertyEdit
) {
    val path = livePath(snapshotId, componentTypeId, propertyPath) ?: return
    try {
        @Suppress("UNCHECKED_CAST")
        val componentType = world.componentManager.getComponentsMap(entity).keys
            .firstOrNull { it.id == componentTypeId } as? ComponentType<Component>
            ?: error("Component $componentTypeId not found")
        val rootComponent = entity.getComponent<Component>(componentType)
            ?: error("Component $componentTypeId not found")
        val rootNode = rootComponent.propertyEditNode()

        when (val result = rootNode.edit(path.segments, operation)) {
            PropertyEditResult.MutatedInPlace -> world.componentManager.markDirty(entity, componentType)
            is PropertyEditResult.Replaced -> {
                val updatedRoot = result.value as? Component
                    ?: error("Root replacement is not a component: ${result.value}")
                entity.setComponent(updatedRoot, componentType)
            }
        }
    } catch (e: Exception) {
        ScriptEngine.LOGGER.warn("Невозможно изменить свойство компонента $componentTypeId", e)
    }
}
