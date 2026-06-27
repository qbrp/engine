package org.lain.engine.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.require
import org.lain.engine.script.*
import org.lain.engine.script.lua.LuaEntityComponent
import org.lain.engine.script.lua.luaValue
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

data class DeveloperMode(var enabled: Boolean, var acoustic: Boolean = false) : Component

var EnginePlayer.developerMode
    get() = this.require<DeveloperMode>().enabled
    set(value) {
        this.require<DeveloperMode>().enabled = value
    }

var EnginePlayer.acousticDebug
    get() = this.require<DeveloperMode>().let { it.acoustic && it.enabled }
    set(value) {
        this.require<DeveloperMode>().acoustic = value
    }

@Serializable
data class ScriptBindings(
    var attack: ScriptId? = null,
    var base: ScriptId? = null,
) : Component

val ATTACK_SCRIPT_VERB = VerbType("attack_script", "Вызвать скрипт атаки")
val BASE_SCRIPT_VERB = VerbType("base_script", "Вызвать скрипт взаимодействия")

fun appendScriptBindingVerbs(player: EnginePlayer) = player.handle<VerbLookup>() {
    val scriptBindings = player.require<ScriptBindings>()
    forAction<InputAction.Base> {
        BASE_SCRIPT_VERB.takeIf {
            scriptBindings.base != null && raycastPlayerNotNull(
                player,
                SOCIAL_INTERACTION_DISTANCE
            )
        }
    }
    forAction<InputAction.Attack> {
        ATTACK_SCRIPT_VERB.takeIf {
            scriptBindings.attack != null && raycastPlayerNotNull(
                player,
                SOCIAL_INTERACTION_DISTANCE
            )
        }
    }
}

context(contents: NamespacedStorageAccess, interaction: InteractionComponent)
fun handleHandScriptInteractions(player: EnginePlayer) {
    val bindings = player.require<ScriptBindings>()
    player.handleInteraction(ATTACK_SCRIPT_VERB) {
        contents
            .getVoidScript<ScriptContext.Interaction>(bindings.attack!!)
            ?.execute(player.interactionScriptContext)
        complete()
    }
    player.handleInteraction(BASE_SCRIPT_VERB) {
        contents
            .getVoidScript<ScriptContext.Interaction>(bindings.base!!)
            ?.execute(player.interactionScriptContext)
        complete()
    }
}

@Serializable
data class EntityDebugData(
    val id: EntityId,
    val clientSide: Boolean,
    val components: List<ComponentDebugData>,
    val objects: Map<Int, DebugObject>, // для объектов в памяти - table и collection
)

@Serializable
/**
 * @param type Идентификатор типа
 */
data class ComponentDebugData(val type: String, val rootId: Int)

@Serializable
sealed class DebugObject {
    @Serializable
    @SerialName("table")
    data class Table(val values: Map<String, DebugEntry>) : DebugObject()
    @Serializable
    @SerialName("collection")
    data class Collection(val values: List<DebugEntry>) : DebugObject()
}

@Serializable
sealed class DebugEntry {
    @Serializable
    @SerialName("ref")
    data class Reference(val id: Int) : DebugEntry()
    @Serializable
    @SerialName("primitive")
    data class Primitive(val value: String) : DebugEntry()
    @Serializable
    @SerialName("null")
    object Null : DebugEntry()
}

/**
 * Ставится игроку при открытии окна EntityDebug. Сигнализирует, что нужно отправлять информацию на клиент
 */
data class EntityDebugViewComponent(
    val entity: EntityId,
    var lastSnapshotTime: Int = 0
) : Component

context(world: World)
fun handleEntityDebugView(handler: ServerHandler, player: EnginePlayer) = player.handle<EntityDebugViewComponent> {
    if (lastSnapshotTime-- <= 0) {
        lastSnapshotTime = 20 * 2 // 2 секунды
        handler.onEntityDebugSnapshot(player, entity.snapshotDebugData())
    }
}

context(world: World)
fun EntityId.snapshotDebugData(): EntityDebugData {
    val components = world.componentManager.getComponentsMap(this, null)
    val context = DebugSerializationContext()
    val debugData = components
        .filter { (_, component) -> component !is LuaEntityComponent }
        .mapNotNull { (type, component) ->
            with(context) {
                runCatching { component.toDebugData(type) }
                    .onFailure {
                        LOGGER.error("Невозможно получить данные для отладки компонента: $type", it)
                    }
                    .getOrNull()
            }
        }
    return EntityDebugData(
        this,
        world.isClient,
        debugData,
        context.objects
    )
}

class DebugSerializationContext(
    var lastId: Int = 0,
    val visited: MutableMap<Int, Int> = mutableMapOf(),
    val objects: MutableMap<Int, DebugObject> = mutableMapOf(),
) {
    fun nextId() = lastId++
}

private fun Any.identityKey(): Int = System.identityHashCode(this)

//TODO: проработать Lua-bridge через абстракцию
context(ctx: DebugSerializationContext)
fun Component.toDebugData(type: ComponentType<out Component>): ComponentDebugData {
    val component = this@toDebugData
    val id = ctx.nextId()
    val debugObject = when (type) {
        is ScriptComponentType -> {
            component as ScriptComponent
            val table = component.luaValue.checktable()
            table.toLuaDebugObject()
        }

        else -> component.toJvmDebugObject()
    }
    ctx.visited[this.identityKey()] = id
    ctx.objects[id] = debugObject

    return ComponentDebugData(type.id, id)
}

context(ctx: DebugSerializationContext)
private fun LuaValue.toLuaDebugEntry(): DebugEntry {
    return if (isnil()) {
        DebugEntry.Null
    } else if (istable()) {
        DebugEntry.Reference(appendSerializationContext { it.checktable().toLuaDebugObject() })
    } else {
        DebugEntry.Primitive(tojstring())
    }
}

context(ctx: DebugSerializationContext)
private fun LuaTable.toLuaDebugObject() = DebugObject.Table(
    keys().associate { key ->
        val rawValue = this[key]
        val keyStr = key.tojstring()
        keyStr to rawValue.toLuaDebugEntry()
    }
)

context(ctx: DebugSerializationContext)
private fun Any?.toJvmDebugEntry(): DebugEntry {
    return when (this) {
        is String -> DebugEntry.Primitive(this)
        is Number, is Boolean -> DebugEntry.Primitive(this.toString())
        is UUID -> DebugEntry.Primitive(this.toString())
        is Enum<*> -> DebugEntry.Primitive(this.toString())
        is EnginePlayer -> DebugEntry.Primitive(this.toString())
        null -> DebugEntry.Null
        else -> {
            val clazz = this::class
            when(clazz.isValue) {
                false -> DebugEntry.Reference(appendSerializationContext { it.toJvmDebugObject() })
                true -> (clazz.memberProperties.first() as KProperty1<Any, *>).get(this).toJvmDebugEntry()
            }
        }
    }
}

context(ctx: DebugSerializationContext)
private fun Any.toJvmDebugObject(): DebugObject {
    return when (this) {
        is Collection<*> -> DebugObject.Collection(this.map { it.toJvmDebugEntry() })
        is Map<*, *> ->
            DebugObject.Table(
                this.map { (k, v) ->
                    k.toString() to v.toJvmDebugEntry()
                }.toMap()
            )

        else -> {
            val properties = this::class.memberProperties
            DebugObject.Table(
                properties.associate { prop ->
                    (prop.name to (prop as KProperty1<Any, *>).get(this).toJvmDebugEntry())
                }
            )
        }
    }
}

context(ctx: DebugSerializationContext)
private fun <T : Any> T.appendSerializationContext(transformer: context(DebugSerializationContext) (T) -> DebugObject): Int {
    ctx.visited[this.identityKey()]?.let { return it }

    val id = ctx.nextId()
    ctx.visited[this.identityKey()] = id

    val node = transformer(this)
    ctx.objects[id] = node

    return id
}