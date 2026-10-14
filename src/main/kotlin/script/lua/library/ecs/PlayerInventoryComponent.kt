package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Equipment
import org.lain.engine.player.EquipmentSlotId
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.require
import org.lain.engine.player.toEquipmentSlotId
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.asUserdataOrThrow
import org.lain.engine.script.lua.library.LuaComponentBinding
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaTableOf
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaListUserdata
import org.lain.engine.script.lua.toLuaMutableMapUserdata
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.world.World
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

object EquipmentBinding : LuaComponentBinding<Equipment> {
    override val componentType: ComponentType<Equipment>
        get() = Equipment.TYPE
    override val id: EngineId
        get() = EngineId("core/player/equipment")

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: Equipment): LuaValue {
        return luaTable {
            "slots"(
                component.slots.toLuaMutableMapUserdata(
                    keyToLua = { it.engineId.coerceToLua() },
                    keyFromLua = { it.resolveIdReference().toEquipmentSlotId() },
                    valueToLua = { value ->
                        world?.let { with(world) { value.luaEntity() } }
                            ?: error("cannot create lua entity in this context")
                    },
                    valueFromLua = { it.checkLuaEntity() }
                )
            )
        }
    }
}