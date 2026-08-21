package org.lain.engine.script

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.util.component.ComponentMeta
import org.lain.engine.util.component.IndexedComponentType
import kotlin.reflect.KClass

@JvmInline
value class ScriptComponentId(val id: String) {
    override fun toString(): String = id
}

interface ScriptComponent : Component {
    val value: ScriptValue
    val type: ScriptComponentType
    val debugTarget: ScriptDebugTarget?
        get() = null
}

class ScriptComponentType(
    val ecsType: ComponentType<ScriptComponent>,
    val meta: ComponentMeta
) : IndexedComponentType<ScriptComponent>(ecsType.id) {
    override fun toString(): String = "ScriptComponentType($idx, $id, $meta)"
}

fun String.toScriptComponentId(): ScriptComponentId = ScriptComponentId(this)

object CoreScriptComponents {
    private val all = mutableMapOf<ScriptComponentId, ScriptComponentType>()

    val PLAYER = register("core/player/component")
    val PLAYER_INVENTORY = register("core/player/inventory")
    val PLAYER_PHYSICS = register("core/player/physics")
    val PLAYER_MODE = register("core/player/game_mode")
    val LOCATION = register("core/location")
    val DYNAMIC_VOXEL = register("core/voxel/dynamic_voxel")
    val USE_RESTRICTION = register("core/voxel/use_restriction", savable = true, networking = true) // TODO: переместить в движок
    val LIGHT_SOURCE = register("core/light/source", savable = true, networking = true)
    val LUMINANCE = register("core/light/luminance", savable = true, networking = true)
    val PARENT = register("core/ownership/parent", savable = true, networking = true)
    val CHILDREN = register("core/ownership/children", savable = true, networking = true)
    val ENTITY_RPC_RECEIVER = register("core/networking/entity_rpc_receiver", savable = false, networking = false)
    val ENTITY_RPC_QUEUE = register("core/networking/entity_rpc_queue", savable = false, networking = false)
    val DYNAMIC_VOXEL_INTEREST = register("core/networking/voxel_interest", savable = true, networking = false)
    val VOXEL_DOOR = register("core/voxel/door", savable = true, networking = true)

    fun get(id: ScriptComponentId) = all[id]

    fun getAll() = all.values.toList()

    private fun register(
        id: String,
        savable: Boolean = false,
        networking: Boolean = false,
        serializationClass: KClass<out Any>? = null
    ) = register(id, ComponentMeta(savable, serializationClass, networking))

    private fun register(
        id: String,
        meta: ComponentMeta = ComponentMeta(false, null, false)
    ): ScriptComponentType {
        val ecsType = ComponentType<ScriptComponent>(id)
        val type = ScriptComponentType(ecsType, meta)
        all[ScriptComponentId(id)] = type
        return type
    }
}
