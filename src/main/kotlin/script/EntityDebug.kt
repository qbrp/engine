package org.lain.engine.script

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerComponent
import org.lain.engine.script.DebugEntry.*
import org.lain.engine.script.DebugPrimitive.*
import org.lain.engine.script.lua.library.LuaEntityComponent
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import java.util.UUID
import javax.naming.OperationNotSupportedException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

data class EntityDebugData(
    val id: EntityId,
    val clientSide: Boolean,
    val components: List<ComponentDebugData>,
    val debugObjects: Map<Int, DebugObject>, // для объектов в памяти - table и collection
    val gameObjects: Map<Int, Any>
) {
    fun toDto() = Dto(id, clientSide, components, debugObjects)

    @Serializable
    data class Dto(
        val id: EntityId,
        val clientSide: Boolean,
        val components: List<ComponentDebugData>,
        val debugObjects: Map<Int, DebugObject>, // для объектов в памяти - table и collection
    )
}

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
@SerialName("primitive")
sealed class DebugPrimitive {
    abstract fun toScriptValue(): ScriptValue
    abstract fun toJvmValue(): Any

    @Serializable @SerialName("str") class Str(val string: String) : DebugPrimitive() {
        override fun toScriptValue(): ScriptValue = SString(string)
        override fun toJvmValue(): Any = string
    }
    @Serializable @SerialName("bool") class Bool(val bool: Boolean) : DebugPrimitive() {
        val string = bool.toString()
        override fun toScriptValue(): ScriptValue = SBool(bool)
        override fun toJvmValue(): Any = bool
    }
    @Serializable @SerialName("int") class Int(val int: kotlin.Int) : DebugPrimitive() {
        val string = int.toString()
        override fun toScriptValue(): ScriptValue = SNumber(int.toDouble())
        override fun toJvmValue(): Any = int
    }
    @Serializable @SerialName("double") class Double(val double: kotlin.Double) : DebugPrimitive() {
        val string = double.toString()
        override fun toScriptValue(): ScriptValue = SNumber(double)
        override fun toJvmValue(): Any = double
    }
    @Serializable @SerialName("uuid") class Uuid(val uuid: String) : DebugPrimitive() {
        val string = uuid
        override fun toScriptValue(): ScriptValue = SString(uuid)
        override fun toJvmValue(): Any = UUID.fromString(uuid)
    }
    @Serializable @SerialName("enum") data class Enum(val enumClass: String, val name: String) : DebugPrimitive() {
        val string = name
        override fun toScriptValue(): ScriptValue = SString(name)
        @Suppress("UNCHECKED_CAST")
        override fun toJvmValue(): Any {
            val clazz = Class.forName(enumClass)
            val result = (clazz.enumConstants as Array<kotlin.Enum<*>>)
                .firstOrNull { it.name == name }
            return result!!
        }
    }
    @Serializable @SerialName("other") class Other(val str: String) : DebugPrimitive() {
        override fun toJvmValue(): Any { throw OperationNotSupportedException() }
        override fun toScriptValue(): ScriptValue { throw OperationNotSupportedException() }
        val string = str
    } // non editable
}

@Serializable
sealed class DebugEntry {
    @Serializable
    @SerialName("ref")
    data class Reference(val id: Int) : DebugEntry()
    @Serializable
    @SerialName("primitive")
    data class Primitive(val readonly: Boolean, val entry: DebugPrimitive) : DebugEntry()
    @Serializable
    @SerialName("null")
    object Null : DebugEntry()
}

/**
 * Ставится игроку при открытии окна EntityDebug. Сигнализирует, что нужно отправлять информацию на клиент
 */
data class EntityDebugViewComponent(
    val entity: EntityId,
    val debugData: EntityDebugData,
    var lastSnapshotTime: Int = 20
) : Component

fun World.tickEntityDebugViewSnapshotSystem(handler: ServerHandler) {
    iterate<PlayerComponent, EntityDebugViewComponent>() { _, (player), view ->
        if (view.lastSnapshotTime-- <= 0) {
            view.lastSnapshotTime = 20 * 2 // 2 секунды
            handler.onEntityDebugSnapshot(player, view.entity.snapshotDebugData().toDto())
        }
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
                        SCRIPT_LOGGERRR.error("Невозможно получить данные для отладки компонента: $type", it)
                    }
                    .getOrNull()
            }
        }
    return EntityDebugData(
        this,
        world.isClient,
        debugData,
        context.debugObjects,
        context.gameObjects
    )
}

fun EntityDebugData.setDataDebug(objectId: Int, property: String, value: DebugPrimitive) {
    when (val obj = gameObjects[objectId] ?: error("Object $objectId not found")) {
        is ScriptDebugTarget -> obj.set(property, value.toScriptValue())
        else -> {
            val mutableProperty = obj::class.memberProperties
                .find { it.name == property } as? KMutableProperty1<Any, Any?>
                ?: error("Mutable property $property not found on ${obj::class.qualifiedName}")
            mutableProperty.set(obj, value.toJvmValue())
        }
    }
}

class DebugSerializationContext(
    var lastId: Int = 0,
    val visited: MutableMap<Int, Int> = mutableMapOf(),
    val debugObjects: MutableMap<Int, DebugObject> = mutableMapOf(),
    val gameObjects: MutableMap<Int, Any> = mutableMapOf(),
) {
    fun appendObjectJvm(id: Int, obj: Any) {
        registerObject(id, obj)
        debugObjects[id] = obj.toJvmDebugObject()
    }

    fun appendObjectScript(id: Int, obj: STable, target: ScriptDebugTarget?) {
        val gameObject = target ?: obj
        registerObject(id, gameObject, target?.identity ?: obj)
        debugObjects[id] = obj.toScriptDebugObject(target)
    }

    fun registerObject(id: Int, obj: Any, identity: Any = obj) {
        gameObjects[id] = obj
        visited[identity.identityKey()] = id
    }

    fun nextId() = lastId++
}

private fun Any.identityKey(): Int = System.identityHashCode(this)

context(ctx: DebugSerializationContext)
fun Component.toDebugData(type: ComponentType<out Component>): ComponentDebugData {
    val component = this@toDebugData
    val id = ctx.nextId()
    when (type) {
        is ScriptComponentType -> {
            component as ScriptComponent
            val table = component.value as? STable
                ?: error("Script component ${type.id} root value must be a table")
            ctx.appendObjectScript(id, table, component.debugTarget)
        }

        else -> ctx.appendObjectJvm(id, component)
    }

    return ComponentDebugData(type.id, id)
}

context(ctx: DebugSerializationContext)
private fun ScriptValue.toScriptDebugEntry(
    readonly: Boolean,
    target: ScriptDebugTarget? = null
): DebugEntry = when (this) {
    SNil -> DebugEntry.Null
    is STable -> Reference(appendScriptSerializationContext(target))
    is SString -> Primitive(readonly, Str(value))
    is SNumber -> Primitive(readonly, Double(value))
    is SBool -> Primitive(readonly, Bool(value))
    is SInt -> Primitive(readonly, Int(value))
    is SList -> TODO("Списки не поддерживаются, т.к. используются только для перевода ScriptValue -> LuaValue")
}

context(ctx: DebugSerializationContext)
private fun STable.toScriptDebugObject(target: ScriptDebugTarget?) = DebugObject.Table(
    map.entries.associate { (key, value) ->
        val childTarget = if (value is STable) target?.child(key) else null
        key.toDebugKey() to value.toScriptDebugEntry(target == null, childTarget)
    }
)

context(ctx: DebugSerializationContext)
private fun STable.appendScriptSerializationContext(target: ScriptDebugTarget?): Int {
    val identity = target?.identity ?: this
    ctx.visited[identity.identityKey()]?.let { return it }

    val id = ctx.nextId()
    ctx.registerObject(id, target ?: this, identity)
    ctx.debugObjects[id] = toScriptDebugObject(target)
    return id
}

private fun ScriptValue.toDebugKey(): String = when (this) {
    SNil -> "nil"
    is SString -> value
    is SNumber -> value.toString()
    is SInt -> value.toString()
    is SBool -> value.toString()
    is STable -> "table@${identityKey().toString(16)}"
    is SList -> "list@${identityKey().toString(16)}"
}

context(ctx: DebugSerializationContext)
private fun Any?.toJvmDebugEntry(readonly: Boolean): DebugEntry {
    return when (this) {
        is String -> DebugEntry.Primitive(readonly, DebugPrimitive.Str(this))
        is Int -> DebugEntry.Primitive(readonly, DebugPrimitive.Int(this))
        is Double -> DebugEntry.Primitive(readonly, DebugPrimitive.Double(this))
        is Number -> DebugEntry.Primitive(readonly, DebugPrimitive.Double(this.toDouble()))
        is Boolean -> DebugEntry.Primitive(readonly, DebugPrimitive.Bool(this))
        is UUID -> DebugEntry.Primitive(true, DebugPrimitive.Uuid(this.toString()))
        is Enum<*> -> DebugEntry.Primitive(readonly, DebugPrimitive.Enum(this::class.qualifiedName!!, name))
        is EnginePlayer -> DebugEntry.Primitive(true, DebugPrimitive.Other(this.toString()))
        null -> DebugEntry.Null
        else -> {
            val clazz = this::class
            when(clazz.isValue) {
                false -> DebugEntry.Reference(appendSerializationContext { it.toJvmDebugObject() })
                true -> {
                    val property = (clazz.memberProperties.first() as KProperty1<Any, *>)
                    property.get(this).toJvmDebugEntry(property is KMutableProperty1<*, *>)
                }
            }
        }
    }
}

context(ctx: DebugSerializationContext)
private fun Any.toJvmDebugObject(): DebugObject {
    return when (this) {
        is Collection<*> -> DebugObject.Collection(this.map { it.toJvmDebugEntry(true) })
        is Map<*, *> ->
            DebugObject.Table(
                this.map { (k, v) ->
                    k.toString() to v.toJvmDebugEntry(true)
                }.toMap()
            )

        else -> {
            val properties = this::class.memberProperties
            DebugObject.Table(
                properties.associate { prop ->
                    (prop.name to (prop as KProperty1<Any, *>).get(this).toJvmDebugEntry(true))
                }
            )
        }
    }
}

context(ctx: DebugSerializationContext)
private fun <T : Any> T.appendSerializationContext(transformer: context(DebugSerializationContext) (T) -> DebugObject): Int {
    ctx.visited[this.identityKey()]?.let { return it }
    val id = ctx.nextId()
    ctx.registerObject(id, this)
    ctx.debugObjects[id] = transformer(this)
    return id
}
