package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.lain.engine.script.SBool
import org.lain.engine.script.SInt
import org.lain.engine.script.SList
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValueParseException
import org.lain.engine.script.parseScriptValue

class ScriptValueTextTest {
    @Test
    fun parsesNestedLuaTable() {
        assertEquals(
            STable(
                linkedMapOf(
                    SString("enabled") to SBool(true),
                    SString("count") to SInt(3),
                    SString("values") to SList(listOf(SString("a"), SNumber(2.5))),
                )
            ),
            "{ enabled = true, count = 3; values = { 'a', 2.5 }, }".parseScriptValue(),
        )
    }

    @Test
    fun parsesExplicitAndImplicitKeys() {
        assertEquals(
            STable(
                linkedMapOf(
                    SString("name") to SString("test"),
                    SInt(1) to SInt(10),
                    SInt(4) to SString("four"),
                )
            ),
            "{ name = 'test', 10, [4] = 'four' }".parseScriptValue(),
        )
    }

    @Test
    fun rejectsExecutableLuaExpressions() {
        assertThrows(ScriptValueParseException::class.java) {
            "os.execute('anything')".parseScriptValue()
        }
    }
}
