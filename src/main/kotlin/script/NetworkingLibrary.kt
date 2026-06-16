package org.lain.engine.script

import kotlinx.serialization.json.JsonElement
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import java.util.Queue

data class ServerboundChannelComponent(val values: Queue<ScriptValue>) : Component

fun World.flushServerboundChannelComponents() {
    iterate<ServerboundChannelComponent>() { entity, channel ->
        channel.values.clear()
    }
}