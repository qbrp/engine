package org.lain.engine.script.lua

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerInventory
import org.lain.engine.script.*
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.world.*
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.*

fun LuaValue.toScriptValue(): ScriptValue = when(type()) {
    LuaValue.TNIL -> SNil
    LuaValue.TBOOLEAN -> SBool(toboolean())
    LuaValue.TINT -> SInt(toint())
    LuaValue.TNUMBER -> SNumber(todouble())
    LuaValue.TSTRING -> SString(tojstring())
    LuaValue.TTABLE -> {
        val t = checktable()
        val map = mutableMapOf<ScriptValue, ScriptValue>()
        for (k in t.keys()) {
            val key = k.toScriptValue()
            val value = t.get(k)
            map[key] = value.toScriptValue()
        }
        STable(map)
    }
    else -> error("Unsupported Lua type: ${typename()}")
}

fun ScriptValue.toLuaValue(): LuaValue = when (this) {
    SNil -> LuaValue.NIL
    is SBool -> value.luaBool()
    is SNumber -> value.luaNum()
    is SString -> value.luaStr()
    is STable -> LuaTable.tableOf(
        map
            .toList()
            .flatMap { (k, v) -> listOf(k.toLuaValue(), v.toLuaValue()) }
            .toTypedArray()
    )
    is SInt -> value.luaNum()
    is SList -> values.toLuaList { it.toLuaValue() }
}

context(world: World, luaScriptEngine: LuaScriptEngine)
fun EnginePlayer.prepareLuaScriptComponents() {
    entity.setLuaScriptComponent(
        luaTableOf(luaValue("object"), coerceToLua()),
        CoreScriptComponents.PLAYER
    )
    entity.setLuaScriptComponent(
        luaTableOf(luaValue("vector"), emptyLuaTable()),
        CoreScriptComponents.LOCATION
    )
}

context(luaScriptEngine: LuaScriptEngine)
fun World.adaptScriptPlayerComponents() {
    iterate<Location> { entity, location ->
        val scriptLocation = entity.getComponent(CoreScriptComponents.LOCATION)?.castLua() ?: return@iterate
        val table = scriptLocation.luaValue
        val vector = table.get("vector")?.checktable()
            ?: LuaValue.tableOf().also {
                table.set("vector", it)
            }

        vector.set(1, location.x.toDouble().luaNum())
        vector.set(2, location.y.toDouble().luaNum())
        vector.set(3, location.z.toDouble().luaNum())
    }
    iterate<PlayerInventory>() { entity, playerInventory ->
        val scriptInventory = entity.getComponent(CoreScriptComponents.PLAYER_INVENTORY)?.castLua()
            ?: run {
                entity.setLuaScriptComponent(luaTableOf(), CoreScriptComponents.PLAYER_INVENTORY)
            }
        val table = scriptInventory.luaValue.checktable()
        table.set("main_hand_item", playerInventory.mainHandItem?.coerceToLua() ?: LuaValue.NIL)
        table.set("off_hand_item", playerInventory.offHandItem?.coerceToLua() ?: LuaValue.NIL)
        table.set("selected_slot", playerInventory.selectedSlot)
        table.set("items", LuaTable.listOf(playerInventory.items.map { it.coerceToLua() }.toTypedArray()))
    }
}

fun LuaTable.toLightBehaviour(): LightBehaviour {
    val parameters = get("params").checktable()
    return when(val type = get("type").tojstring()) {
        "sphere" -> LightBehaviour.Sphere(parameters.get("radius").toint())
        else -> error("Unsupported light behaviour type: $type")
    }
}

fun World.adaptScriptLightComponents() {
    val luminanceArray = componentManager.getComponentArray<Luminance>()
    val lightSourceArray = componentManager.getComponentArray<LightSource>()
    iterate(CoreScriptComponents.LIGHT_SOURCE) { entity, lightSource ->
        val behaivour = lightSource.castLua().luaValue.get("behaviour").checktable()
        lightSourceArray.getOrSet(entity) { LightSource(behaivour.toLightBehaviour()) }
    }
    iterate(CoreScriptComponents.LUMINANCE) { entity, luminance ->
        val level = luminance.castLua().luaValue.get("level").toint()
        luminanceArray.getOrSet(entity, { Luminance(level) }).let {
            it.value = level
        }
    }
}

context(lua: LuaScriptEngine)
fun World.adaptScriptNetworkingComponents() {
    val serverboundChannelComponentArray = componentManager.getComponentArray(CoreScriptComponents.ENTITY_RPC_RECEIVER)
    val dynamicVoxelInterestComponentArray = componentManager.getComponentArray<DynamicVoxelInterest>()
    iterate<ScriptComponent>(CoreScriptComponents.ENTITY_RPC_RECEIVER) { entity, script ->
        if (entity.getComponent<EntityRpcReceiver>() == null) {
            entity.setComponent(
                EntityRpcReceiver(values = LinkedList())
            )
        }
    }

    iterate<EntityRpcReceiver>() { entity, channel ->
        val channelL = serverboundChannelComponentArray.getOrSet(entity) {
            LuaScriptComponent(
                luaTable {
                    "messages"(emptyLuaTable())
                },
                CoreScriptComponents.ENTITY_RPC_RECEIVER
            )
        }.castLua()
        val valuesTable = LuaTable()
        channel.values.forEachIndexed { i, msg ->
            valuesTable.set(
                i + 1,
                luaTableOf(
                    luaValue("data"), msg.value.toLuaValue(),
                    luaValue("sender"), msg.sender.coerceToLua()
                )
            )
        }
        channelL.luaValue.set("messages", valuesTable)
    }

    iterate(CoreScriptComponents.DYNAMIC_VOXEL_INTEREST) { entity, _ ->
        dynamicVoxelInterestComponentArray.getOrSet(entity) { DynamicVoxelInterest }
    }
}