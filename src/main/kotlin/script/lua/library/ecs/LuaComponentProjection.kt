package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue

inline fun <reified T : Component> World.projectLuaComponent(
    sourceType: ScriptComponentType,
    crossinline projection: (LuaValue, T?) -> T,
) {
    val targetComponents = componentManager.getComponentArray<T>()
    iterate(sourceType) { entity, source ->
        val current = targetComponents.componentOf(entity)
        val projected = projection(source.castedLuaValue, current)
        if (projected != current) {
            entity.setComponent(projected)
        }
    }
}
