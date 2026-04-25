package org.lain.engine.script

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.util.component.ComponentMeta

@JvmInline
value class ScriptComponentId(val id: String) {
    override fun toString(): String = id
}

data class ScriptComponent(val field: Any) : Component

data class ScriptComponentType(
    val ecsType: ComponentType<ScriptComponent>,
    val meta: ComponentMeta = ComponentMeta(false, ScriptComponent::class, false)
) {
    val id get() = ecsType.id
}

fun String.toScriptComponentId(): ScriptComponentId = ScriptComponentId(this)

object CoreScriptComponents {
    private val all = mutableMapOf<ScriptComponentId, ScriptComponentType>()

    val PLAYER = register("core/player/component")
    val LOCATION = register("core/location")
    val DYNAMIC_VOXEL = register("core/voxel/dynamic_voxel")
    val USE_RESTRICTION = register("core/voxel/use_restriction")
    val LIGHT_SOURCE = register("core/light/source")
    val LUMINANCE = register("core/light/luminance")

    fun get(id: ScriptComponentId) = all[id]

    fun getAll() = all.values.toList()

    private fun register(id: String): ComponentType<ScriptComponent> {
        val ecsType = ComponentType<ScriptComponent>(id)
        val type = ScriptComponentType(ecsType)
        all[ScriptComponentId(id)] = type
        return ecsType
    }
}