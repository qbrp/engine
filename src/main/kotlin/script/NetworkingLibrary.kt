package org.lain.engine.script

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.world.World
import java.util.Queue

data class EntityRpcReceiver(val values: Queue<Message>) : Component {
    data class Message(val sender: EnginePlayer, val value: ScriptValue)
}

data class EntityRpcQueue(val values: Queue<ScriptValue>) : Component

fun World.flushEntityRpcMessageReceiver() {
    iterate<EntityRpcReceiver>() { entity, channel ->
        channel.values.clear()
    }
}