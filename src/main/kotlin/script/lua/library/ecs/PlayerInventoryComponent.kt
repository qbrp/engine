package org.lain.engine.script.lua.library.ecs

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Equipment
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.require
import org.lain.engine.player.toEquipmentSlotId
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.ComponentBinding
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.luaId
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaListUserdata
import org.lain.engine.script.lua.toLuaMutableMapUserdata
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

data class LuaPlayerInventory(
    val player: EnginePlayer,
    val inventory: PlayerInventory,
)

private fun LuaValue.asLuaPlayerInventory() = checkuserdata() as LuaPlayerInventory

context(lua: LuaScriptEngine)
fun PlayerInventoryMetaTable() = luaTable {
    index { self, key ->
        val (player, inventory) = self.asLuaPlayerInventory()
        context(player.world) {
            when (key.tojstring()) {
                "main_hand_item" -> inventory.mainHandItem?.luaEntity() ?: NIL
                "off_hand_item" -> inventory.offHandItem?.luaEntity() ?: NIL
                "selected_slot" -> luaValue(inventory.selectedSlot)
                "items" -> inventory.items.toList().toLuaListUserdata { it.luaEntity() }
                else -> NIL
            }
        }
    }
}

context(lua: LuaScriptEngine)
fun LuaPlayerInventoryComponent(player: EnginePlayer): LuaUserdata =
    LuaUserdata(
        LuaPlayerInventory(player, player.require())
    ).also {
        it.setmetatable(lua.playerInventoryMetaTable)
    }

fun EquipmentBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/player/equipment"),
    componentClass = Equipment::class,
    cache = cache,
    toLuaValue = { world, _, component ->
        luaTable {
            "slots"(
                component.slots.toLuaMutableMapUserdata(
                    keyToLua = { it.engineId.luaId() },
                    keyFromLua = { it.resolveIdReference().toEquipmentSlotId() },
                    valueToLua = { value ->
                        world?.let { with(world) { value.luaEntity() } }
                            ?: error("cannot create lua entity in this context")
                    },
                    valueFromLua = { it.checkLuaEntity() }
                )
            )
        }
    },
)
