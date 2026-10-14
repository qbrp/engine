package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.exists
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.PersistentId
import org.lain.engine.data.UnloadComponent
import org.lain.engine.mc.ecs.MinecraftEntity
import org.lain.engine.mc.ecs.MinecraftItem
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.PlayerComponent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.lua.library.LuaEntityComponent
import org.lain.engine.server.ServerHandler
import org.lain.engine.server.replication.Changes
import org.lain.engine.server.replication.PlayerReplicationState
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.reflect.full.starProjectedType

data class EntityInspectionSnapshot(
    val id: EntityId,
    val clientSide: Boolean,
    val components: List<ComponentInspectionData>,
    val debugObjects: Map<Int, InspectionObject>, // для объектов в памяти - table и collection
    val editTargets: Map<Int, InspectionEditTarget>,
    val typeDescriptors: Map<Int, InspectionTypeDescriptor>,
    val nodes: Map<InspectionPathDto, InspectionNode>
) {
    fun toDto() = Dto(
        id, clientSide, components, debugObjects, typeDescriptors, nodes
    )

    @Serializable
    data class Dto(
        val id: EntityId,
        val clientSide: Boolean,
        val components: List<ComponentInspectionData>,
        val debugObjects: Map<Int, InspectionObject>, // для объектов в памяти - table и collection
        val typeDescriptors: Map<Int, InspectionTypeDescriptor>,
        val nodes: Map<InspectionPathDto, InspectionNode>
    )
}

@Serializable
data class ComponentInspectionData(val type: String, val data: ComponentInspectionResult)

@Serializable
sealed class ComponentInspectionResult {
    @Serializable
    @SerialName("value")
    data class Value(val node: InspectionNode) : ComponentInspectionResult()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ComponentInspectionResult()
}

data class EntityInspection(
    val entity: EntityId,
    val rate: Int,
    var lastSnapshot: EntityInspectionSnapshot,
    var lastSnapshotTime: Int = 0
)

data class EntityInspectionComponent(
    val entities: MutableMap<PersistentId, EntityInspection>
) : Component

fun World.tickEntityDebugViewSnapshotSystem(handler: ServerHandler) {
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
                val snapshot = inspection.entity.snapshotInspection()
                inspection.lastSnapshot = snapshot
                handler.onEntityDebugSnapshot(player, persistentId, snapshot.toDto())
            }
        }
    }
}

context(world: World)
fun EntityId.snapshotInspection(): EntityInspectionSnapshot {
    val components = world.componentManager.getComponentsMap(this)
    val context = InspectionSerializationContext()
    val debugData = components
        .filter { (_, component) -> !component.shouldSkip(componentTypeOf(component)) }
        .mapNotNull { (type, component) ->
            with(context) {
                component.toInspectionData(type)
            }
        }
    return EntityInspectionSnapshot(
        this,
        world.isClient,
        debugData,
        context.debugObjects,
        context.editTargets,
        context.typeDescriptors,
        context.nodes
    )
}

private fun Component.shouldSkip(type: ComponentType<*>): Boolean {
    return type == CoreScriptComponents.PLAYER
            || this is LuaEntityComponent
            || this is PlayerReplicationState
            || this is Changes
            || this is EntityInspectionComponent
            || this is MinecraftEntity
            || this is MinecraftItem
            || this is MinecraftPlayer
            || this is UnloadComponent
}

context(ctx: InspectionSerializationContext)
fun Component.toInspectionData(type: ComponentType<out Component>): ComponentInspectionData {
    val component = this@toInspectionData
    val location = InspectionLocation(
        InspectionPath(type, emptyList()),
        InspectionPathDto(type.id, emptyList()),
    )
    val rootTargetId = ctx.editTarget(location.live)
    val data = try {
        ComponentInspectionResult.Value(
            when (type) {
                is ScriptComponentType -> {
                    component as ScriptComponent
                    component.inspectionValueNode.toScriptInspectionNode(rootTargetId, location)
                }

                else -> component.toJvmInspectionValue(this::class.starProjectedType, location)
                    .asNode(rootTargetId, location)
            }
        )
    } catch (e: Exception) {
        ScriptEngine.LOGGER.error("Невозможно получить данные для отладки компонента: $type", e)
        ComponentInspectionResult.Error(e.message ?: e.toString())
    }

    return ComponentInspectionData(type.id, data)
}
