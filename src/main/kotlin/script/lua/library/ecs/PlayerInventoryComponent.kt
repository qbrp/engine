package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Equipment
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.require
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.asUserdataOrThrow
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.world.World
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

private data class LuaPlayerInventory(
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
                "items" -> inventory.items.toLuaList { it.luaEntity() }
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

context(lua: LuaScriptEngine)
fun World.pullEquipment() {
    iterate<ScriptComponent, Equipment>(CoreScriptComponents.PLAYER_EQUIPMENT) { _, equipmentL, equipment ->
        val slotsL = equipmentL.value.toLuaValue().checktable()["slots"].checktable()

        val slots = buildMap {
            slotsL.keys().forEach { key ->
                val slot = EquipmentSlotId(key.asUserdataOrThrow<EngineId>())
                val item = slotsL[key].checkLuaEntity()
                put(slot, item)
            }
        }

        equipment.slots.clear()
        equipment.slots.putAll(slots)
    }
}