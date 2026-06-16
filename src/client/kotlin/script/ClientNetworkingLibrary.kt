package org.lain.engine.client.script

import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ServerboundChannelComponent
import org.lain.engine.script.lua.emptyLuaTable
import org.lain.engine.script.lua.luaTableOf
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toKotlin
import org.lain.engine.script.lua.toList
import org.lain.engine.script.lua.toScriptValue
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.world.World
import java.util.LinkedList

fun World.updateClientServerboundChannelSystem(handler: ClientHandler) {
    val serverboundChannelComponentArray = componentManager.getComponentArray<ServerboundChannelComponent>()
    iterate(CoreScriptComponents.SERVERBOUND_CHANNEL) { entity, channelL ->
        val channelK = serverboundChannelComponentArray.getOrSet(entity) {
            ServerboundChannelComponent(LinkedList())
        }
        val valuesL = channelL.luaValue.get("values")
        channelK.values.addAll(valuesL.checktable().toList { it.toScriptValue() })
        channelL.luaValue.set("values", emptyLuaTable())
    }

    iterate<PersistentIdComponent, ServerboundChannelComponent>() { entity, (peristentId), channel ->
        val values = channel.values
        if (values.isNotEmpty()) {
            handler.sendServerboundChannelData(peristentId, values.toList())
            values.clear()
        }
    }
}