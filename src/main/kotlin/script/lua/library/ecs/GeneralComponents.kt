package org.lain.engine.script.lua.library.ecs

import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.library.ComponentBinding
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.world.Location

fun LocationBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/location"),
    componentClass = Location::class,
    cache = cache,
    toLuaValue = { _, _, component -> component.position.coerceToLua() },
)
