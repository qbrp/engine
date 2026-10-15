package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.player.Equipment
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.ecs.DecrementItemBinding
import org.lain.engine.script.lua.library.ecs.EquipmentBinding
import org.lain.engine.script.lua.library.ecs.EquippableBinding
import org.lain.engine.script.lua.library.ecs.GiveItemBinding
import org.lain.engine.script.lua.library.ecs.ItemNameBinding
import org.lain.engine.script.lua.library.ecs.LocationBinding
import org.lain.engine.util.ecs.EngineComponentType
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue

interface LuaComponentBinding<C : Component> {
    val componentType: ComponentType<C>
    val id: EngineId

    context(lua: LuaScriptEngine)
    fun toLua(world: World?, entity: EntityId, component: C): LuaValue
    context(lua: LuaScriptEngine)
    fun fromLua(entity: EntityId, value: LuaValue): C? = null
}

@JvmInline
value class LuaComponentBindingId(val value: EngineId) {
    override fun toString(): String {
        return value.toString()
    }
}

object LuaComponentBindingRegistry {
    private val bindings = mutableMapOf<LuaComponentBindingId, LuaComponentBinding<*>>()

    init {
        register(LocationBinding)
        register(EquipmentBinding)
        register(ItemNameBinding)
        register(EquippableBinding)
        register(DecrementItemBinding)
        register(GiveItemBinding)
    }

    fun get(id: LuaComponentBindingId): LuaComponentBinding<*> {
        return bindings[id] ?: error("Component binding $id not exists")
    }

    fun register(binding: LuaComponentBinding<*>) {
        bindings[LuaComponentBindingId(binding.id)] = binding
    }
}
