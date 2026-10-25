package org.lain.engine.script.lua.library

import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.script.Contents
import org.lain.engine.script.Identifiable
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

context(lua: LuaScriptEngine)
fun <K : Identifiable, V : Any> Map<K, V>.toLuaIdTable(
    valueTransform: (K, V) -> LuaValue,
): LuaTable {
    val table = LuaTable()
    entries.forEach { (key, value) ->
        table[key.engineId.coerceToLua()] = valueTransform(key, value)
    }
    return table
}

context(lua: LuaScriptEngine)
fun ContentsTable(contents: Contents) = luaTable {
    "equipment_slots"(
        contents.equipmentSlots.toLuaIdTable { id, slot -> slot.toLuaTable(id) }
    )
}

context(lua: LuaScriptEngine)
fun EquipmentSlot.toLuaTable(id: EquipmentSlotId): LuaTable = luaTable {
    "id"(id.engineId.coerceToLua())
    "name"(name.luaStr())
    "part"(part.name.lowercase().luaStr())
}