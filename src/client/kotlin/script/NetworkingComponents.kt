package org.lain.engine.client.script

import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.ecs.coerceToLua
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.world.World

context(lua: LuaScriptEngine)
fun World.applyLuaEntityRpcQueues() {
    val queueComponents = componentManager.getComponentArray(CoreScriptComponents.ENTITY_RPC_QUEUE)

    iterate<EntityRpcQueue>() { entity, queue ->
        queueComponents.getOrSet(entity) {
            LuaScriptComponent(queue.coerceToLua(), CoreScriptComponents.ENTITY_RPC_QUEUE, lua)
        }
    }
}

fun World.tickEntityRpcQueueSystem(handler: ClientHandler) {
    iterate<PersistentIdComponent, EntityRpcQueue>() { _, (persistentId), queue ->
        if (queue.values.isNotEmpty()) {
            handler.sendServerboundChannelData(persistentId, queue.values.toList())
            queue.values.clear()
        }
    }
}
