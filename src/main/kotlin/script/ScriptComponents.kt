package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.util.ecs.ComponentMeta
import org.lain.engine.util.ecs.IndexedComponentType

@JvmInline
@Serializable
value class ScriptComponentId(val id: EngineId) : Identifiable {
    override val engineId: EngineId get() = id
    override fun toString(): String = id.toString()
}

interface ScriptComponent : Component {
    val value: ScriptValue
    val type: ScriptComponentType
    val debugTarget: ScriptDebugTarget?
        get() = null
}

class ScriptComponentType(
    val engineId: ScriptComponentId,
    val ecsType: ComponentType<ScriptComponent>,
    val meta: ComponentMeta,
    val version: Int = 0
) : IndexedComponentType<ScriptComponent>(ecsType.id) {
    override fun toString(): String = "ScriptComponentType($idx, $id, $meta)"
}

fun EngineId.toScriptComponentId(): ScriptComponentId = ScriptComponentId(this)

object CoreScriptComponents {
    private val all = mutableMapOf<ScriptComponentId, ScriptComponentType>()

    val PLAYER = register("core/player/component")
    val PLAYER_INVENTORY = register("core/player/inventory")
    val PLAYER_PHYSICS = register("core/player/physics")
    val PLAYER_MODE = register("core/player/game_mode")
    val PLAYER_INPUT = register("core/player/input")
    val PLAYER_ATTRIBUTES = register("core/player/attributes")
    val PLAYER_CUSTOM_ATTRIBUTES = register("core/player/custom_attributes")
    val PLAYER_MOVEMENT_STATUS = register("core/player/movement_status")
    val PLAYER_VELOCITY = register("core/player/velocity")
    val PLAYER_JUMP = register("core/player/jump")
    val LOCATION = register("core/location")
    val DYNAMIC_VOXEL = register("core/voxel/dynamic_voxel")
    val USE_RESTRICTION = register("core/voxel/use_restriction", savable = true, networking = true) // TODO: переместить в движок
    val LIGHT_SOURCE = register("core/light/source", savable = true, networking = true)
    val LUMINANCE = register("core/light/luminance", savable = true, networking = true)
    val ENTITY_RPC_RECEIVER = register("core/networking/entity_rpc_receiver", savable = false, networking = false)
    val ENTITY_RPC_QUEUE = register("core/networking/entity_rpc_queue", savable = false, networking = false)
    val DYNAMIC_VOXEL_INTEREST = register("core/networking/voxel_interest", savable = true, networking = false)
    val VOXEL_DOOR = register("core/voxel/door", savable = true, networking = true)

    fun get(id: ScriptComponentId) = all[id]

    fun getAll() = all.values.toList()

    private fun register(
        id: String,
        savable: Boolean = false,
        networking: Boolean = false
    ) = register(id, ComponentMeta(savable, networking))

    private fun register(
        id: String,
        meta: ComponentMeta = ComponentMeta(false, false)
    ): ScriptComponentType {
        val ecsType = ComponentType<ScriptComponent>(id)
        val scriptComponentId = EngineId(id).toScriptComponentId()
        val type = ScriptComponentType(scriptComponentId, ecsType, meta)
        all[scriptComponentId] = type
        return type
    }
}
