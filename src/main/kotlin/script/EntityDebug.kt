package org.lain.engine.script

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.handle
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.lua.LuaEntityComponent
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.UUID
import javax.naming.OperationNotSupportedException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

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
    abstract fun toLuaValue(): LuaValue
    abstract fun toJvmValue(): Any

    @Serializable @SerialName("str") class Str(val string: String) : DebugPrimitive() {
        override fun toLuaValue(): LuaValue = LuaValue.valueOf(string)
        override fun toJvmValue(): Any = string
    }
    @Serializable @SerialName("bool") class Bool(val bool: Boolean) : DebugPrimitive() {
        val string = bool.toString()
        override fun toLuaValue(): LuaValue = LuaValue.valueOf(bool.toString())
        override fun toJvmValue(): Any = bool
    }
    @Serializable @SerialName("int") class Int(val int: kotlin.Int) : DebugPrimitive() {
        val string = int.toString()
        override fun toLuaValue(): LuaValue = LuaValue.valueOf(int.toString())
        override fun toJvmValue(): Any = int
    }
    @Serializable @SerialName("double") class Double(val double: kotlin.Double) : DebugPrimitive() {
        val string = double.toString()
        override fun toLuaValue(): LuaValue = LuaValue.valueOf(double.toString())
        override fun toJvmValue(): Any = double
    }
    @Serializable @SerialName("uuid") class Uuid(val uuid: String) : DebugPrimitive() {
        val string = uuid.toString()
        override fun toLuaValue(): LuaValue = LuaValue.valueOf(uuid)
        override fun toJvmValue(): Any = uuid
    }
    @Serializable @SerialName("enum") data class Enum(val enumClass: String, val name: String) : DebugPrimitive() {
        val string = name
        override fun toLuaValue(): LuaValue = name.toLuaValue()
        override fun toJvmValue(): Any {
            val clazz = Class.forName(enumClass)
            val result = (clazz.enumConstants as Array<kotlin.Enum<*>>)
                .firstOrNull { it.name == name }
            return result!!
        }
    }
    @Serializable @SerialName("other") class Other(val str: String) : DebugPrimitive() {
        override fun toJvmValue(): Any { throw OperationNotSupportedException() }
        override fun toLuaValue(): LuaValue { throw OperationNotSupportedException() }
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

context(world: World)
fun handleEntityDebugView(handler: ServerHandler, player: EnginePlayer) = player.handle<EntityDebugViewComponent> {
    if (lastSnapshotTime-- <= 0) {
        lastSnapshotTime = 20 * 2 // 2 секунды
        handler.onEntityDebugSnapshot(player, entity.snapshotDebugData().toDto())
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
        context.debugObjects,
        context.gameObjects
    )
}

context(world: World)
fun EntityId.setDataDebug(debugData: EntityDebugData, objectId: Int, property: String, value: DebugPrimitive) {
    val obj = debugData.gameObjects[objectId] ?: error("Object $objectId not found")
    if (obj is LuaValue && obj.checktable() != null) {
        val table = obj.checktable()
        table.set(property, value.toLuaValue())
    } else {
        val clazz = obj::class
        val property = clazz.memberProperties.find { it.name == property } as KMutableProperty1<Any, Any?>
        property.set(obj, value.toLuaValue())
    }
}

class DebugSerializationContext(
    var lastId: Int = 0,
    val visited: MutableMap<Int, Int> = mutableMapOf(),
    val debugObjects: MutableMap<Int, DebugObject> = mutableMapOf(),
    val gameObjects: MutableMap<Int, Any> = mutableMapOf(),
) {
    fun appendObjectJvm(id: Int, obj: Any) {
        appendObject(id, obj, obj.toJvmDebugObject())
    }

    fun appendObjectLua(id: Int, obj: LuaTable) {
        appendObject(id, obj, obj.toLuaDebugObject())
    }

    fun appendObject(id: Int, obj: Any, debugObject: DebugObject) {
        debugObjects[id] = debugObject
        gameObjects[id] = obj
        visited[this.identityKey()] = id
    }

    fun nextId() = lastId++
}

private fun Any.identityKey(): Int = System.identityHashCode(this)

//TODO: проработать Lua-bridge через абстракцию
context(ctx: DebugSerializationContext)
fun Component.toDebugData(type: ComponentType<out Component>): ComponentDebugData {
    val component = this@toDebugData
    val id = ctx.nextId()
    when (type) {
        is ScriptComponentType -> {
            component as ScriptComponent
            val table = component.luaValue.checktable()
            ctx.appendObjectLua(id, table)
        }

        else -> ctx.appendObjectJvm(id, component)
    }

    return ComponentDebugData(type.id, id)
}

context(ctx: DebugSerializationContext)
private fun LuaValue.toLuaDebugEntry(): DebugEntry = when(type()) {
    LuaValue.TNIL -> DebugEntry.Null
    LuaValue.TTABLE -> DebugEntry.Reference(appendSerializationContext { it.checktable().toLuaDebugObject() })
    LuaValue.TSTRING ->  DebugEntry.Primitive(false, DebugPrimitive.Str(tojstring()))
    LuaValue.TINT -> DebugEntry.Primitive(false, DebugPrimitive.Int(toint()))
    LuaValue.TNUMBER -> DebugEntry.Primitive(false, DebugPrimitive.Double(todouble()))
    LuaValue.TBOOLEAN -> DebugEntry.Primitive(false, DebugPrimitive.Bool(toboolean()))
    else -> DebugEntry.Primitive(true, DebugPrimitive.Other(tojstring()))
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
    ctx.appendObject(id, this, transformer(this))
    return id
}