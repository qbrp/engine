package org.lain.engine.script

import org.lain.cyberia.ecs.*
import org.lain.engine.util.component.ComponentMeta

@JvmInline
value class ScriptComponentId(val id: String) {
    override fun toString(): String = id
}

data class ScriptComponent(val field: Any) : Component

data class ScriptComponentType(
    val ecsType: ComponentType<ScriptComponent>,
    val meta: ComponentMeta = ComponentMeta(false, false)
) {
    val id get() = ecsType.id
}

fun String.toScriptComponentId(): ScriptComponentId = ScriptComponentId(this)

object BuiltinScriptComponents {
    val PLAYER = ScriptComponentType(ComponentType("core/player/component"))
    val LOCATION = ScriptComponentType(ComponentType("core/player/location"))
    val DYNAMIC_VOXEL = ScriptComponentType(ComponentType("core/voxel/dynamic_voxel"))
    val USE_RESTRICTION = ScriptComponentType(ComponentType("core/voxel/use_restriction"))
    val ALL = listOf(PLAYER, DYNAMIC_VOXEL, LOCATION, USE_RESTRICTION).associateBy { it.id }
}

context(ctx: WriteComponentAccess)
fun EntityId.setScriptComponent(scriptComponent: ScriptComponent, type: ScriptComponentType) {
    setComponent(scriptComponent, type.ecsType)
}