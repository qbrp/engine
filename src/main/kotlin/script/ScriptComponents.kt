package org.lain.engine.script

import org.lain.cyberia.ecs.*
import org.lain.engine.util.component.ComponentMeta

@JvmInline
value class ScriptComponentId(val id: String)

data class ScriptComponent(val field: Any) : Component

data class ScriptComponentType(
    val ecsType: ComponentType<ScriptComponent>,
    val meta: ComponentMeta = ComponentMeta(false, false)
) {
    val id get() = ecsType.id
}

fun String.toScriptComponentId(): ScriptComponentId = ScriptComponentId(this)

object BuiltinScriptComponents {
    val PLAYER = ScriptComponentType(ComponentType("player"))
    val ALL = listOf(PLAYER).associateBy { it.id }
}

context(ctx: WriteComponentAccess)
fun EntityId.setScriptComponent(scriptComponent: ScriptComponent, type: ScriptComponentType) {
    setComponent(scriptComponent, type.ecsType)
}