package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.item.Mass
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.library.ecs.AppliedCharacterBinding
import org.lain.engine.script.lua.library.ecs.AppliedLookBinding
import org.lain.engine.script.lua.library.ecs.DecrementItemBinding
import org.lain.engine.script.lua.library.ecs.EquipmentBinding
import org.lain.engine.script.lua.library.ecs.EquippableBinding
import org.lain.engine.script.lua.library.ecs.GiveItemBinding
import org.lain.engine.script.lua.library.ecs.AssetVariantsBinding
import org.lain.engine.script.lua.library.ecs.ItemBinding
import org.lain.engine.script.lua.library.ecs.ItemNameBinding
import org.lain.engine.script.lua.library.ecs.LocationBinding
import org.lain.engine.script.lua.library.ecs.MovementStatusBinding
import org.lain.engine.script.lua.library.ecs.PlayerAttributesBinding
import org.lain.engine.script.lua.library.ecs.PlayerCustomAttributesBinding
import org.lain.engine.script.lua.luaNum
import org.lain.engine.util.ecs.ComponentMeta
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.EngineComponentType
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue
import kotlin.reflect.KClass

interface LuaComponentBinding<C : Component> {
    val componentType: ComponentType<C>
    val cacheComponentType: ComponentType<LuaCachedComponentBinding>? get() = null
    val id: EngineId

    context(lua: LuaScriptEngine)
    fun toLua(world: World?, entity: EntityId, component: C): LuaValue
    context(lua: LuaScriptEngine)
    fun fromLua(entity: EntityId, value: LuaValue): C? = null
}

class ComponentBinding<C : Component>(
    override val id: EngineId,
    val componentClass: KClass<C>,
    val cache: Boolean = false,
    private val toLuaValue: LuaScriptEngine.(World?, EntityId, C) -> LuaValue,
    private val fromLuaValue: LuaScriptEngine.(EntityId, LuaValue) -> C? = { _, _ -> null },
) : LuaComponentBinding<C> {
    override val componentType: ComponentType<C>
        get() = componentTypeOf(componentClass)
    override var cacheComponentType: ComponentType<LuaCachedComponentBinding>? = null
        internal set

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: C): LuaValue =
        lua.toLuaValue(world, entity, component)

    context(lua: LuaScriptEngine)
    override fun fromLua(entity: EntityId, value: LuaValue): C? = lua.fromLuaValue(entity, value)
}

data class LuaCachedComponentBinding(val tick: Long, val component: LuaValue) : Component

@JvmInline
value class LuaComponentBindingId(val value: EngineId) {
    override fun toString(): String {
        return value.toString()
    }
}

object LuaComponentBindingRegistry {
    private val bindings = linkedMapOf<LuaComponentBindingId, ComponentBinding<*>>()
    private var initialized = false

    init {
        register(LocationBinding())
        register(EquipmentBinding())
        register(ItemNameBinding())
        register(EquippableBinding())
        register(DecrementItemBinding())
        register(GiveItemBinding())
        register(ItemBinding())
        register(AssetVariantsBinding())
        register(PlayerAttributesBinding())
        register(PlayerCustomAttributesBinding())
        register(MovementStatusBinding())
        register(MassBinding())
        register(AppliedCharacterBinding())
        register(AppliedLookBinding())
    }

    fun get(id: LuaComponentBindingId): ComponentBinding<*> {
        return bindings[id] ?: error("Component binding $id not exists")
    }

    fun getOrNull(id: LuaComponentBindingId): ComponentBinding<*>? {
        return bindings[id]
    }

    fun register(binding: ComponentBinding<*>) {
        check(!initialized) { "Component bindings must be registered before bootstrap" }
        val id = LuaComponentBindingId(binding.id)
        check(id !in bindings) { "Component binding $id is already registered" }
        check(bindings.values.none { it.componentClass == binding.componentClass }) {
            "Component class ${binding.componentClass.qualifiedName} already has a Lua binding"
        }
        bindings[id] = binding
    }

    fun listBindings(): List<ComponentBinding<*>> = bindings.values.toList()

    internal fun initializeCacheComponentTypes() {
        val meta = ComponentMeta(savable = false, networking = false)
        bindings.values.filter { it.cache }.forEach { binding ->
            val type = EngineComponentType<LuaCachedComponentBinding>(
                "core/script/lua_binding_cache/${binding.id.full}",
                meta,
            )
            ComponentTypeRegistry.registerComponentType(type, meta)
            binding.cacheComponentType = type
        }
        initialized = true
    }
}

fun MassBinding(cache: Boolean = true): ComponentBinding<Mass> {
    val userdataType = LuaUserdataType<Mass> {
        indexSelf { self, key ->
            if (key.tojstring() == "kg") {
                self.mass.toDouble().luaNum()
            } else {
                NIL
            }
        }
        newIndexSelf { self, key, value ->
            if (key.tojstring() == "kg") {
                self.mass = value.tofloat()
            }
        }
    }

    return ComponentBinding(
        id = EngineId("core/mass"),
        componentClass = Mass::class,
        cache = cache,
        toLuaValue = { _, _, component -> userdataType.newInstance(component) },
        fromLuaValue = { _, value -> Mass(value["kg"].tofloat()) },
    )
}
