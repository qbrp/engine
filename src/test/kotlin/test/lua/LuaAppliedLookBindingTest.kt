package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.Skin
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.lua.library.toLuaValue
import org.lain.engine.script.lua.loadScript
import org.lain.engine.test.DummyWorld
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaAppliedLookBindingTest : LuaScriptEngineTestBase() {
    private lateinit var lua: LuaScriptEngine
    private lateinit var world: World
    private lateinit var functions: LuaTable

    @BeforeEach
    fun setup() {
        lua = createLuaEngine("lua/character_binding.lua")
        lua.componentLibrary.setupSimulation()
        world = DummyWorld()
        lua.loadWorld(world)
        functions = lua.globals.loadScript(lua.entrypoint).call().checktable()
    }

    @Test
    fun lookMappingIncludesEveryFieldAndNestedSkin() {
        val look = look()

        val value = look.toLuaValue()

        assertEquals(setOf("id", "title", "skin", "description", "base"), value.fieldNames())
        assertEquals(look.id, value.get("id").checkjstring())
        assertEquals(look.title, value.get("title").checkjstring())
        assertEquals(look.appearanceDescriptionAddon, value.get("description").checkjstring())
        assertEquals(false, value.get("base").checkboolean())
        val skin = value.get("skin").checktable()
        assertEquals(setOf("url"), skin.fieldNames())
        assertEquals(look.skin.url, skin.get("url").checkjstring())
    }

    @Test
    fun appliedLookBindingReadsSelectedLookThroughEntityAndIteration() {
        val look = look()
        val entity = with(world) { world.addEntity { setComponent(SelectedLook(look)) } }

        val value = read(entity)
        val worldL = with(lua) { world.luaWorld() }
        val iterated = functions.get("iterate_looks").call(worldL).get(1)

        assertSame(value, iterated)
        assertEquals(look.id, value.get("id").checkjstring())
        assertEquals(look.skin.url, value.get("skin").get("url").checkjstring())
    }

    @Test
    fun replacingSelectedLookIsVisibleOnTheNextTick() {
        val firstLook = look()
        val replacement = firstLook.copy(
            id = "base", title = "Основной", skin = Skin("https://example.com/base.png"),
            appearanceDescriptionAddon = "Другое описание", base = true,
        )
        val entity = with(world) { world.addEntity { setComponent(SelectedLook(firstLook)) } }
        val first = read(entity)

        with(world) { entity.setComponent(SelectedLook(replacement)) }
        world.simulation.ticks++
        val current = read(entity)

        assertNotSame(first, current)
        assertEquals(firstLook.id, first.get("id").checkjstring())
        assertEquals(replacement.id, current.get("id").checkjstring())
        assertEquals(replacement.skin.url, current.get("skin").get("url").checkjstring())
        assertEquals(replacement.appearanceDescriptionAddon, current.get("description").checkjstring())
        assertEquals(true, current.get("base").checkboolean())
    }

    @Test
    fun mappedLooksDoNotShareMutableSkinTables() {
        val look = look()
        val first = look.toLuaValue()
        val second = look.toLuaValue()

        first.get("skin").set("url", LuaValue.valueOf("changed"))

        assertNotSame(first.get("skin"), second.get("skin"))
        assertEquals(look.skin.url, second.get("skin").get("url").checkjstring())
    }

    private fun look() = Look(
        id = "festive", title = "Праздничный", skin = Skin("https://example.com/festive.png"),
        appearanceDescriptionAddon = "Описание наряда", base = false,
    )

    private fun read(entity: EntityId): LuaValue {
        val entityL = with(lua) { with(world) { entity.luaEntity() } }
        return functions.get("get_look").call(entityL)
    }

    private fun LuaTable.fieldNames(): Set<String> = keys().mapTo(mutableSetOf()) { it.checkjstring() }
}
