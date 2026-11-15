package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.lain.engine.item.ItemId
import org.lain.engine.script.CallbackType
import org.lain.engine.script.EngineId
import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.PhaseStep
import org.lain.engine.script.SInt
import org.lain.engine.script.SNumber
import org.lain.engine.script.Script
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptId
import org.lain.engine.script.ScriptSystemId
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.SystemSide

class LuaBuildCompilationTest : LuaScriptEngineTestBase() {
    @Test
    fun luaTableBuildsLinkedContentsWithoutComposer() {
        val lua = createLuaEngine("lua/build_direct.lua")

        val build = lua.compileContents().successOrThrow()
        val namespace = build.namespaces.getValue(NamespaceId("lua_test"))

        val itemId = ItemId(EngineId("lua_test/item"))
        val item = namespace.items.getValue(itemId)
        assertEquals("Lua item", item.name)
        assertEquals(7, item.maxCount)
        assertEquals(EngineId("lua_test/model"), item.assets?.default)
        assertEquals(itemId, build.inventoryTab.entries.single().prefabId)
        assertEquals(listOf("First", "Second"), build.inventoryTab.entries.single().tooltipLines)

        val component = namespace.components.getValue(ScriptComponentId(EngineId("lua_test/state")))
        assertEquals(true, component.meta.savable)
        assertEquals(true, component.meta.networking)

        val system = namespace.systems.getValue(ScriptSystemId(EngineId("lua_test/tick")))
        assertEquals(SystemSide.BOTH, system.side)
        assertEquals(listOf(component), system.query.map { it.componentType })
        assertEquals("Lua phase", build.phases.base.name)
        assertSame(system, (build.phases.base.steps.single() as PhaseStep.System).system)
        assertNotNull(build.callbacks?.of(CallbackType.WORLD_TICK))

        @Suppress("UNCHECKED_CAST")
        val script = namespace.scripts.getValue(ScriptId(EngineId("lua_test/answer")))
            as Script<ScriptContext.ComponentMigration, ScriptValue>
        assertEquals(
            ExecutionResult.Success(SNumber(42.0)),
            script.execute(ScriptContext.ComponentMigration(SInt(40))),
        )
    }
}
