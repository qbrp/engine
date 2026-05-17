package org.lain.engine.client.script

import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl
import net.minecraft.client.KeyMapping
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.script.lua.oneArgFunction
import org.lain.engine.script.lua.threeArgFunction
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.script.lua.twoArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

object KeyMappingsStatus {
    val disabled: MutableSet<KeyMapping> = mutableSetOf()
}

context(ctx: ClientLuaContext)
fun Globals.setupKeyMappings() {
    val keysLibrary = LuaTable()
    val keyMappings = (MinecraftClient.options.keyMappings + KeyBindingRegistryImpl.process(emptyArray<KeyMapping>()))
        .associateBy { it.name }
        .mapValues { (_, keyMapping) ->
            val table = LuaTable()
            table.set("name", keyMapping.name)

            val meta = LuaTable()
            meta.set("__index", twoArgFunction { self, key ->
                when (key.tojstring()) {
                    "is_down" -> keyMapping.isDown.toLuaValue()
                    else -> self.rawget(key)
                }
            })
            meta.set("__newindex", threeArgFunction { self, key, value ->
                when (key.tojstring()) {
                    "enabled" -> {
                        if (value.toboolean()) {
                            KeyMappingsStatus.disabled.add(keyMapping)
                        } else {
                            KeyMappingsStatus.disabled.remove(keyMapping)
                        }
                    }
                    else -> self.rawset(key, value)
                }
                LuaValue.NIL
            })
            table.setmetatable(meta)
        }
    ctx.globals.set("Keys", keysLibrary)
    keysLibrary.set("get_key_mapping", oneArgFunction { name ->
        val nameString = name.tojstring()
        keyMappings[nameString] ?: error("Key mapping $nameString not found")
    })
}