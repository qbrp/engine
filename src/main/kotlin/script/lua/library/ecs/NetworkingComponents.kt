package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.setLuaScriptComponent
import org.lain.engine.script.lua.toScriptValue
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.world.DynamicVoxelInterest
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL
import java.util.LinkedList

private data class LuaEntityRpcReceiver(
    val receiver: EntityRpcReceiver,
    var messages: LuaTable = LuaTable(),
)

private fun LuaValue.asLuaEntityRpcReceiver() = checkuserdata() as LuaEntityRpcReceiver

private data class LuaEntityRpcMessage(
    val sender: EnginePlayer,
    val data: LuaTable,
)

private fun LuaValue.asLuaEntityRpcMessage() = checkuserdata() as LuaEntityRpcMessage

context(lua: LuaScriptEngine)
fun EntityRpcMessageMetaTable() = luaTable {
    index { self, key ->
        val (sender, data) = self.asLuaEntityRpcMessage()
        context(lua) {
            when (key.tojstring()) {
                "sender" -> sender.coerceToLua()
                "data" -> data
                else -> NIL
            }
        }
    }
}

context(lua: LuaScriptEngine)
private fun EntityRpcReceiver.Message.coerceToLua(): LuaUserdata =
    LuaUserdata(LuaEntityRpcMessage(sender, value.toLuaValue().checktable())).also {
        it.setmetatable(lua.entityRpcMessageMetaTable)
    }

context(lua: LuaScriptEngine)
private fun LuaEntityRpcReceiver.refreshMessages() {
    messages = receiver.values.toLuaList { it.coerceToLua() }
}

context(lua: LuaScriptEngine)
fun EntityRpcReceiverMetaTable() = luaTable {
    index { self, key ->
        val receiver = self.asLuaEntityRpcReceiver()
        when (key.tojstring()) {
            "messages" -> receiver.messages
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
private fun EntityRpcReceiver.coerceToLua(): LuaUserdata =
    LuaUserdata(LuaEntityRpcReceiver(this)).also {
        it.setmetatable(lua.entityRpcReceiverMetaTable)
    }

private data class LuaEntityRpcQueue(val queue: EntityRpcQueue)

private fun LuaValue.asLuaEntityRpcQueue() = checkuserdata() as LuaEntityRpcQueue

context(lua: LuaScriptEngine)
fun EntityRpcQueueMetaTable() = luaTable {
    function2("send") { self, value ->
        self.asLuaEntityRpcQueue().queue.values.add(value.checktable().toScriptValue())
        NIL
    }
}

context(lua: LuaScriptEngine)
fun EntityRpcQueue.coerceToLua(): LuaUserdata = LuaUserdata(LuaEntityRpcQueue(this)).also {
    it.setmetatable(lua.entityRpcQueueMetaTable)
}

context(lua: LuaScriptEngine)
fun World.applyLuaNetworkingComponents() {
    val receiverComponents =
        componentManager.getComponentArray(CoreScriptComponents.ENTITY_RPC_RECEIVER)
    val dynamicVoxelInterestComponents = componentManager.getComponentArray<DynamicVoxelInterest>()

    iterate(CoreScriptComponents.ENTITY_RPC_RECEIVER) { entity, _ ->
        if (entity.getComponent<EntityRpcReceiver>() == null) {
            val receiver = EntityRpcReceiver(LinkedList())
            entity.setComponent(receiver)
            entity.setLuaScriptComponent(
                receiver.coerceToLua(),
                CoreScriptComponents.ENTITY_RPC_RECEIVER
            )
        }
    }

    iterate<EntityRpcReceiver>() { entity, receiver ->
        receiverComponents.getOrSet(entity) {
            LuaScriptComponent(receiver.coerceToLua(), CoreScriptComponents.ENTITY_RPC_RECEIVER, lua)
        }.castedLuaValue.asLuaEntityRpcReceiver().refreshMessages()
    }

    iterate(CoreScriptComponents.DYNAMIC_VOXEL_INTEREST) { entity, _ ->
        dynamicVoxelInterestComponents.getOrSet(entity) { DynamicVoxelInterest }
    }
}
