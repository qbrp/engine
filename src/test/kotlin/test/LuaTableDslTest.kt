package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.luaStr
import org.luaj.vm2.LuaValue

class LuaTableDslTest {
    @Test
    fun userdataIndexPreservesMethodsAndDynamicProperties() {
        val type = LuaUserdataType<String> {
            functionSelf("read") { self -> self.luaStr() }
            indexSelf { self, key ->
                when (key.tojstring()) {
                    "length" -> LuaValue.valueOf(self.length)
                    else -> LuaValue.NIL
                }
            }
        }
        val value = type.newInstance("contents")

        assertEquals("contents", value.get("read").call(value).tojstring())
        assertEquals(8, value.get("length").toint())
        assertTrue(value.get("missing").isnil())
    }
}
