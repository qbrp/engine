package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.script.lua.toLuaListUserdata
import org.lain.engine.script.lua.toLuaMutableListUserdata
import org.lain.engine.script.lua.toLuaMutableMapUserdata
import org.lain.engine.script.lua.toLuaQueueUserdata
import org.luaj.vm2.LuaValue
import java.util.LinkedList

class LuaCollectionsTest {
    @Test
    fun listProvidesOneBasedReadAccess() {
        val value = listOf("one", "two").toLuaListUserdata(
            valueToLua = LuaValue::valueOf,
        )

        assertEquals(2, value.len().toint())
        assertEquals("one", value[1].tojstring())
        assertEquals("two", value.get("get").call(value, LuaValue.valueOf(2)).tojstring())
        assertTrue(value[3].isnil())
    }

    @Test
    fun mutableListMutationsUpdateBackingList() {
        val values = mutableListOf(1, 2)
        var changes = 0
        val value = values.toLuaMutableListUserdata(
            valueToLua = LuaValue::valueOf,
            valueFromLua = LuaValue::checkint,
            onChanged = { changes++ },
        )

        value[1] = LuaValue.valueOf(3)
        value.get("add").call(value, LuaValue.valueOf(4))
        val removed = value.get("remove_at").call(value, LuaValue.valueOf(2))

        assertEquals(listOf(3, 4), values)
        assertEquals(2, removed.toint())
        assertEquals(3, changes)
    }

    @Test
    fun mutableMapSupportsIndexAccessAndNilRemoval() {
        val values = mutableMapOf("first" to 1)
        var changes = 0
        val value = values.toLuaMutableMapUserdata(
            keyToLua = LuaValue::valueOf,
            keyFromLua = LuaValue::checkjstring,
            valueToLua = LuaValue::valueOf,
            valueFromLua = LuaValue::checkint,
            onChanged = { changes++ },
        )

        value["second"] = LuaValue.valueOf(2)
        value["first"] = LuaValue.NIL

        assertEquals(mapOf("second" to 2), values)
        assertEquals(2, value["second"].toint())
        assertFalse(value.get("is_empty").toboolean())
        assertEquals(2, changes)
    }

    @Test
    fun queueExposesQueueOperations() {
        val values = LinkedList(listOf("first", "second"))
        var changes = 0
        val value = values.toLuaQueueUserdata(
            valueToLua = LuaValue::valueOf,
            valueFromLua = LuaValue::checkjstring,
            onChanged = { changes++ },
        )

        val first = value.get("poll").call(value)
        value.get("add").call(value, LuaValue.valueOf("third"))

        assertEquals("first", first.tojstring())
        assertEquals(listOf("second", "third"), values.toList())
        assertEquals("second", value.get("peek").call(value).tojstring())
        assertEquals(2, changes)
    }
}
