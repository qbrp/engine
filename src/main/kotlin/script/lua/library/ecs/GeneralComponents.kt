package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.library.LuaComponentBinding
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.util.ecs.componentType
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue

object LocationBinding : LuaComponentBinding<Location> {
    override val componentType = componentType<Location>()
    override val id = EngineId("core/location")

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: Location): LuaValue {
        return component.position.coerceToLua()
    }
}