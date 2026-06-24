package org.lain.engine.client.script

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.emptyLuaTable
import org.lain.engine.script.lua.luaTableOf
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toScriptValue
import org.lain.engine.storage.PersistentIdComponent

import org.lain.engine.world.World
import java.util.LinkedList
import java.util.Queue

fun World.updateClientServerboundChannelSystem(handler: ClientHandler) {
    val serverboundChannelComponentArrayLua = componentManager.getComponentArray(CoreScriptComponents.ENTITY_RPC_QUEUE)

    iterate<EntityRpcQueue>() { entity, channelK ->
        val channelL = serverboundChannelComponentArrayLua.getOrSet(entity) {
            ScriptComponent(
                luaTableOf(
                    luaValue("values"), luaTableOf()
                ),
                CoreScriptComponents.ENTITY_RPC_QUEUE
            )
        }

        val valuesL = channelL.luaValue.get("values")
        channelK.values.addAll(
            valuesL.checktable().toList {
                it.toScriptValue()
            }
        )
        channelL.luaValue.set("values", emptyLuaTable())
    }

    iterate<PersistentIdComponent, EntityRpcQueue>() { entity, (peristentId), channel ->
        val values = channel.values
        if (values.isNotEmpty()) {
            handler.sendServerboundChannelData(peristentId, values.toList())
            values.clear()
        }
    }
}