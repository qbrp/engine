package org.lain.engine.script.dev

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.componentTypeOf
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
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

data class EntityInspectionSnapshot(
    val id: EntityId,
    val clientSide: Boolean,
    val components: List<ComponentInspectionData>,
    val debugObjects: Map<Int, InspectionObject>, // для объектов в памяти - table и collection
    val gameObjects: Map<Int, Any>
) {
    fun toDto() = Dto(id, clientSide, components, debugObjects)

    @Serializable
    data class Dto(
        val id: EntityId,
        val clientSide: Boolean,
        val components: List<ComponentInspectionData>,
        val debugObjects: Map<Int, InspectionObject>, // для объектов в памяти - table и collection
    )
}

@Serializable
data class ComponentInspectionData(val type: String, val data: ComponentInspectionResult)

@Serializable
sealed class ComponentInspectionResult {
    @Serializable
    @SerialName("value")
    data class Value(val entry: InspectionValue) : ComponentInspectionResult()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ComponentInspectionResult()
}

/**
 * Ставится игроку при открытии окна EntityDebug. Сигнализирует, что нужно отправлять информацию на клиент
 */
data class EntityDebugViewComponent(
    val entities: MutableMap<PersistentId, Inspecting>
) : Component {
    data class Inspecting(
        val entity: EntityId,
        val rate: Int,
        var lastSnapshotTime: Int = 0
    )
}

fun World.tickEntityDebugViewSnapshotSystem(handler: ServerHandler) {
    iterate<PlayerComponent, EntityDebugViewComponent>() { _, (player), debugView ->
        debugView.entities.forEach { (persistentId, entityView) ->
            if (entityView.lastSnapshotTime-- <= 0) {
                entityView.lastSnapshotTime = entityView.rate
                handler.onEntityDebugSnapshot(player, persistentId, entityView.entity.snapshotInspection().toDto())
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
        context.gameObjects
    )
}

fun EntityInspectionSnapshot.applyEdit(objectId: Int, property: String, value: InspectionPrimitive) {
    when (val obj = gameObjects[objectId] ?: error("Object $objectId not found")) {
        is ScriptInspectionTarget -> obj.set(property, value.toScriptValue())
        else -> {
            val mutableProperty = obj::class.memberProperties
                .find { it.name == property } as? KMutableProperty1<Any, Any?>
                ?: error("Mutable property $property not found on ${obj::class.qualifiedName}")
            mutableProperty.set(obj, value.toJvmValue())
        }
    }
}

class InspectionSerializationContext(
    var lastId: Int = 0,
    val visited: MutableMap<Int, Int> = mutableMapOf(),
    val debugObjects: MutableMap<Int, InspectionObject> = mutableMapOf(),
    val gameObjects: MutableMap<Int, Any> = mutableMapOf(),
) {
    fun registerObject(id: Int, obj: Any, identity: Any = obj) {
        gameObjects[id] = obj
        visited[identity.identityKey()] = id
    }

    fun nextId() = lastId++
}

private fun Component.shouldSkip(type: ComponentType<*>): Boolean {
    return type == CoreScriptComponents.PLAYER
            || this is LuaEntityComponent
            || this is PlayerReplicationState
            || this is Changes
            || this is EntityDebugViewComponent
            || this is MinecraftEntity
            || this is MinecraftItem
            || this is MinecraftPlayer
            || this is UnloadComponent
}

context(ctx: InspectionSerializationContext)
fun Component.toInspectionData(type: ComponentType<out Component>): ComponentInspectionData {
    val component = this@toInspectionData
    val data = try {
        ComponentInspectionResult.Value(
            when (type) {
                is ScriptComponentType -> {
                    component as ScriptComponent
                    component.value.toScriptInspectionValue(false, component.inspectionTarget)
                }

                else -> component.toJvmInspectionValue(false)
            }
        )
    } catch (e: Exception) {
        ScriptEngine.LOGGER.error("Невозможно получить данные для отладки компонента: $type", e)
        ComponentInspectionResult.Error(e.message ?: e.toString())
    }

    return ComponentInspectionData(type.id, data)
}

context(ctx: InspectionSerializationContext)
fun <T : Any> T.appendSerializationContext(
    target: ScriptInspectionTarget? = null,
    transformer: context(InspectionSerializationContext) (T) -> InspectionObject
): Int {
    val identity = target?.identity ?: this
    ctx.visited[identity.identityKey()]?.let { return it }
    val id = ctx.nextId()
    ctx.registerObject(id, target ?: this, identity)
    ctx.debugObjects[id] = transformer(this)
    return id
}

fun Any.identityKey(): Int = System.identityHashCode(this)