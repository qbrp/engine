package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.setLuaScriptComponent
import org.lain.engine.world.Location
import org.lain.engine.world.World

fun LuaLocationComponent(location: Location) = location.position.coerceToLua()

context(luaScriptEngine: LuaScriptEngine)
fun World.refreshGeneralLuaComponentsView() {
    iterate<Location> { entity, location ->
        entity.getComponent(CoreScriptComponents.LOCATION)?.castLua() ?: return@iterate
        entity.setLuaScriptComponent(LuaLocationComponent(location), CoreScriptComponents.LOCATION)
    }
}
