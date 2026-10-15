package org.lain.engine.script.lua.library

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.CallbackType.Companion.type
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.asUserdataOrThrow
import org.lain.engine.script.lua.castedLuaValue
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue

@Suppress("UNCHECKED_CAST")
sealed interface LuaComponentAccess {
    val id: EngineId
    val componentType: ComponentType<Component>

    data class Native(
        val binding: LuaComponentBinding<*>,
    ) : LuaComponentAccess {
        override val id: EngineId
            get() = binding.id
        fun asGeneric() = binding as LuaComponentBinding<Component>
        override val componentType: ComponentType<Component>
            get() = asGeneric().componentType
    }

    data class Script(
        val type: ScriptComponentType,
    ) : LuaComponentAccess {
        override val id: EngineId
            get() = type.engineId.id
        override val componentType: ComponentType<Component>
            get() = type as ComponentType<Component>
    }
}

context(writeComponentAccess: WriteComponentAccess, lua: LuaScriptEngine)
fun EntityId.setLuaScriptComponent(value: LuaValue, type: ScriptComponentType): LuaScriptComponent {
    val component = LuaScriptComponent(value, type, lua)
    setComponent(component, type)
    return component
}

context(write: WriteComponentAccess, lua: LuaScriptEngine)
fun EntityId.setComponentLua(world: World?, value: LuaValue, componentAccess: LuaComponentAccess): LuaValue {
    return when(componentAccess) {
        is LuaComponentAccess.Native -> {
            val binding = componentAccess.asGeneric()
            val component = binding.fromLua(this, value)
                ?: error("$binding not supports setComponent operation")
            setComponent(component, componentAccess.componentType)
            binding.toLua(world, this, component)
        }
        is LuaComponentAccess.Script -> {
            setLuaScriptComponent(value, componentAccess.type)
                .castedLuaValue
        }
    }
}

context(read: ReadComponentAccess)
fun EntityId.hasComponentLua(componentAccess: LuaComponentAccess): Boolean {
    return hasComponent(
        when(componentAccess) {
            is LuaComponentAccess.Native -> componentAccess.asGeneric().componentType
            is LuaComponentAccess.Script -> componentAccess.type
        }
    )
}

context(lua: LuaScriptEngine)
private fun EntityId.getComponentBindingLua(
    world: World?,
    bindingAccess: LuaComponentAccess.Native,
    getter: (ComponentType<Component>) -> Component?
): LuaValue? {
    val binding = bindingAccess.asGeneric()
    return binding.toLua(
        world,
        this,
        getter(binding.componentType) ?: return null
    )
}

context(read: ReadComponentAccess, lua: LuaScriptEngine)
fun EntityId.getComponentLua(world: World?, componentAccess: LuaComponentAccess): LuaValue? {
    return when(componentAccess) {
        is LuaComponentAccess.Native -> {
            getComponentBindingLua(world, componentAccess) {
                type -> getComponent(type)
            }
        }
        is LuaComponentAccess.Script -> {
            getComponent(componentAccess.type)?.castedLuaValue
        }
    }
}

context(write: WriteComponentAccess, lua: LuaScriptEngine)
fun EntityId.removeComponentLua(world: World?, componentAccess: LuaComponentAccess): LuaValue? {
    return when(componentAccess) {
        is LuaComponentAccess.Native -> {
            getComponentBindingLua(world, componentAccess) {
                type -> removeComponent(type)
            }
        }
        is LuaComponentAccess.Script -> {
            removeComponent(componentAccess.type)?.castedLuaValue
        }
    }
}

fun LuaValue.fetchHolderLuaComponentAccess(): LuaComponentAccess {
    return get("access").asLuaComponentAccess()
}

fun LuaValue.asLuaComponentAccess(): LuaComponentAccess {
    return asUserdataOrThrow<LuaComponentAccess>()
}

fun LuaComponentAccess.asScript() = (this as? LuaComponentAccess.Script)?.type
    ?: error("$this is not a script component type access")