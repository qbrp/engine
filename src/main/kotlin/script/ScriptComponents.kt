package org.lain.engine.script

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.util.component.ComponentMeta

@JvmInline
value class ScriptComponentId(val id: String) {
    override fun toString(): String = id
}

class ScriptComponent(val value: Any, val type: ScriptComponentType) : Component {
    override fun toString(): String {
        return "${type.id}($value)"
    }
}

data class ScriptComponentType(
    val ecsType: ComponentType<ScriptComponent>,
    val meta: ComponentMeta
) : ComponentType<ScriptComponent> by ecsType

fun String.toScriptComponentId(): ScriptComponentId = ScriptComponentId(this)

object CoreScriptComponents {
    private val all = mutableMapOf<ScriptComponentId, ScriptComponentType>()

    val PLAYER = register("core/player/component")
    val LOCATION = register("core/location")
    val DYNAMIC_VOXEL = register("core/voxel/dynamic_voxel")
    val USE_RESTRICTION = register("core/voxel/use_restriction", ComponentMeta(savable = true, networking = true, serializationClass = null))
    val LIGHT_SOURCE = register("core/light/source", ComponentMeta(savable = true, networking = true, serializationClass = null))
    val LUMINANCE = register("core/light/luminance", ComponentMeta(savable = true, networking = true, serializationClass = null))
    val PARENT = register("core/ownership/parent", ComponentMeta(savable = true, networking = true, serializationClass = null))
    val CHILDREN = register("core/ownership/children", ComponentMeta(savable = true, networking = true, serializationClass = null))
    val ENTITY_RPC_RECEIVER = register("core/networking/entity_rpc_receiver", ComponentMeta(savable = false, networking = false, serializationClass = null))
    val ENTITY_RPC_QUEUE = register("core/networking/entity_rpc_queue", ComponentMeta(savable = false, networking = false, serializationClass = null))
    val DYNAMIC_VOXEL_INTEREST = register("core/networking/voxel_interest", ComponentMeta(savable = true, networking = false, serializationClass = null))

    fun get(id: ScriptComponentId) = all[id]

    fun getAll() = all.values.toList()

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