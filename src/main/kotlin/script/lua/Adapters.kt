package org.lain.engine.script.lua

import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.SBool
import org.lain.engine.script.SNil
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.world.DynamicVoxelInterest
import org.lain.engine.world.LightBehaviour
import org.lain.engine.world.LightSource
import org.lain.engine.world.Location
import org.lain.engine.world.Luminance
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.LinkedList

fun LuaValue.toScriptValue(): ScriptValue = when(type()) {
    LuaValue.TNIL -> SNil
    LuaValue.TBOOLEAN -> SBool(toboolean())
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
    is SBool -> value.toLuaValue()
    is SNumber -> value.toLuaValue()
    is SString -> value.toLuaValue()
    is STable -> LuaTable.tableOf(
        map
            .toList()
            .flatMap { (k, v) -> listOf(k.toLuaValue(), v.toLuaValue()) }
            .toTypedArray()
    )
}

fun createLuaScriptComponent(value: ScriptValue, type: ScriptComponentType): ScriptComponent {
    return ScriptComponent(value.toLuaValue(), type)
}

context(world: World, luaContext: LuaContext)
fun EnginePlayer.prepareLuaScriptComponents() {
    entityId.setScriptComponent(
        luaTableOf(luaValue("object"), coerceToLua()),
        CoreScriptComponents.PLAYER
    )
    entityId.setScriptComponent(
        luaTableOf(luaValue("vector"), emptyLuaTable()),
        CoreScriptComponents.LOCATION
    )
}

fun World.adaptScriptPlayerComponents() {
    iterate<Location> { entity, location ->
        val scriptLocation = entity.getComponent(CoreScriptComponents.LOCATION) ?: return@iterate
        val table = scriptLocation.luaValue
        val vector = table.get("vector")?.checktable()
            ?: LuaValue.tableOf().also {
                table.set("vector", it)
            }

        vector.set(1, location.x.toLuaValue())
        vector.set(2, location.y.toLuaValue())
        vector.set(3, location.z.toLuaValue())
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
        val behaivour = lightSource.luaValue.get("behaviour").checktable()
        lightSourceArray.getOrSet(entity) { LightSource(behaivour.toLightBehaviour()) }
    }
    iterate(CoreScriptComponents.LUMINANCE) { entity, luminance ->
        val level = luminance.luaValue.get("level").toint()
        luminanceArray.getOrSet(entity, { Luminance(level) }).let {
            it.value = level
        }
    }
}

context(lua: LuaContext)
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
            ScriptComponent(
                luaTableOf(
                    luaValue("messages"), emptyLuaTable()
                ),
                CoreScriptComponents.ENTITY_RPC_RECEIVER
            )
        }
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