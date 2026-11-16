package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.ComponentType
import org.lain.engine.script.ContentHolder
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.loadScript
import org.lain.engine.util.ecs.ComponentMeta
import org.lain.engine.util.ecs.ComponentState
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaComponentStateTest : LuaScriptEngineTestBase() {
    private lateinit var lua: LuaScriptEngine
    private lateinit var functions: LuaTable
    private lateinit var health: ScriptComponentType
    private lateinit var label: ScriptComponentType

    @BeforeEach
    fun setUp() {
        health = componentType("lua_test/health")
        label = componentType("lua_test/label")
        lua = createLuaEngine("lua/component_state.lua")
        lua.namespacedStorage.update(
            NamespacedStorage(
                components = ContentHolder(mapOf(health.engineId to health, label.engineId to label)),
            )
        )
        lua.componentLibrary.setupSimulation()
        functions = lua.globals.loadScript(lua.entrypoint).call().checktable()
    }

    @Test
    fun createsIndependentEmptyStates() {
        val result = functions.get("independent_states").invoke()
        val first = result.arg(1).componentState()
        val second = result.arg(2).componentState()

        assertSame(result.arg(3), (first.getComponent(health) as LuaScriptComponent).luaValue)
        assertTrue(second.getComponents().isEmpty())
    }

    @Test
    fun luaReadsAndMutatesStoredComponents() {
        val result = functions.get("set_and_read").invoke()
        val state = result.arg(1).componentState()
        val healthValue = (state.getComponent(health) as LuaScriptComponent).luaValue
        val labelValue = (state.getComponent(label) as LuaScriptComponent).luaValue

        assertSame(result.arg(2), healthValue)
        assertSame(result.arg(3), labelValue)
        assertEquals(25, healthValue.get("value").checkint())
        assertEquals("updated", labelValue.get("text").checkjstring())
        assertEquals(2, state.getComponents().size)
    }

    @Test
    fun removalReturnsTheOldValueAndAllowsReinsertion() {
        val result = functions.get("remove_and_reinsert").invoke()
        val state = result.arg(1).componentState()

        assertSame(result.arg(2), (state.getComponent(health) as LuaScriptComponent).luaValue)
        assertSame(result.arg(3), (state.getComponent(label) as LuaScriptComponent).luaValue)
        assertEquals(2, state.getComponents().size)
    }

    @Test
    fun requiringAnAbsentComponentFailsWithoutChangingTheState() {
        val state = functions.get("missing_required").call().componentState()

        assertTrue(state.getComponents().isEmpty())
    }

    @Test
    fun addingAnExistingTypeFailsWithoutReplacingTheComponent() {
        val result = functions.get("duplicate_type").invoke()
        val state = result.arg(1).componentState()

        assertSame(result.arg(2), (state.getComponent(health) as LuaScriptComponent).luaValue)
        assertEquals(1, state.getComponents().size)
    }

    @Test
    fun nativeComponentBindingsAreRejected() {
        val state = functions.get("native_binding").call().componentState()

        assertTrue(state.getComponents().isEmpty())
    }

    @Test
    fun luaCanReadAndRemoveAComponentInsertedFromKotlin() {
        val state = ComponentState()
        val value = LuaTable().apply { set("value", LuaValue.valueOf(42)) }
        state.setComponent(health, LuaScriptComponent(value, health, lua))

        val removed = functions.get("read_from_kotlin").call(lua.componentStateUserdataType.newInstance(state))

        assertSame(value, removed)
        assertEquals(43, value.get("value").checkint())
        assertNull(state.getComponent(health))
    }

    private fun componentType(id: String): ScriptComponentType {
        val componentId = ScriptComponentId(EngineId(id))
        return ScriptComponentType(
            engineId = componentId,
            ecsType = ComponentType<ScriptComponent>(componentId.id.toString()),
            meta = ComponentMeta(savable = false, networking = false),
            migrations = emptyList(),
        )
    }

    private fun LuaValue.componentState(): ComponentState =
        checkuserdata(ComponentState::class.java) as ComponentState
}
