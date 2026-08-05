package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.player.require
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.castLua
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.setLuaScriptComponent
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.location
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

private fun LuaPlayerPhysicsComponent(player: EnginePlayer) = luaTable {
    val physics = player.require<PlayerPhysics>()
    "no_clip"(physics.noClip)
}

context(luaScriptEngine: LuaScriptEngine)
private fun LuaPlayerComponent(player: EnginePlayer) = luaTable {
    "object"(player.coerceToLua())
}

private data class LuaPlayerInventory(
    val player: EnginePlayer,
    val inventory: PlayerInventory,
)

private fun LuaValue.asLuaPlayerInventory() = checkuserdata() as LuaPlayerInventory

context(lua: LuaScriptEngine)
fun PlayerInventoryMetaTable() = luaTable {
    index { self, key ->
        val (player, inventory) = self.asLuaPlayerInventory()
        context(player.world, lua) {
            when (key.tojstring()) {
                "main_hand_item" -> inventory.mainHandItem?.coerceToLua() ?: NIL
                "off_hand_item" -> inventory.offHandItem?.coerceToLua() ?: NIL
                "selected_slot" -> luaValue(inventory.selectedSlot)
                "items" -> inventory.items.toLuaList { it.coerceToLua() }
                else -> NIL
            }
        }
    }
}

context(lua: LuaScriptEngine)
private fun EnginePlayer.coerceInventoryToLua(): LuaUserdata =
    LuaUserdata(
        LuaPlayerInventory(this, require())
    ).also {
        it.setmetatable(lua.playerInventoryMetaTable)
    }

context(world: World, luaScriptEngine: LuaScriptEngine)
fun EnginePlayer.prepareLuaScriptComponents() {
    entity.setLuaScriptComponent(
        LuaPlayerComponent(this),
        CoreScriptComponents.PLAYER
    )
    entity.setLuaScriptComponent(
        LuaLocationComponent(location),
        CoreScriptComponents.LOCATION
    )
    entity.setLuaScriptComponent(
        coerceInventoryToLua(),
        CoreScriptComponents.PLAYER_INVENTORY
    )
    entity.setLuaScriptComponent(
        LuaPlayerPhysicsComponent(this),
        CoreScriptComponents.PLAYER_PHYSICS
    )
}

fun World.applyLuaPlayerComponents() {
    iterate<ScriptComponent, PlayerPhysics>(
        CoreScriptComponents.PLAYER_PHYSICS,
        componentTypeOf(PlayerPhysics::class)
    ) { entity, luaPhysics, physics ->
        physics.noClip = luaPhysics.castedLuaValue.get("no_clip").toboolean()
    }
}