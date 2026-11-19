package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.BiologicalCategory
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.BodyType
import org.lain.engine.player.character.CharacterAppearance
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.CharacterGradientName
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.OwnedCharacterId
import org.lain.engine.player.character.ServerCharacterData
import org.lain.engine.player.character.ServerCharacterId
import org.lain.engine.player.character.SelectedLook
import org.lain.engine.player.character.Skin
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.lua.library.toLuaValue
import org.lain.engine.script.lua.loadScript
import org.lain.engine.test.DummyWorld
import org.lain.engine.util.Color
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaAppliedCharacterBindingTest : LuaScriptEngineTestBase() {
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
    fun entityReadsAndIterationExposeTheAnnotatedCharacterStructure() {
        val profile = profile()
        val entity = addCharacter(profile)

        val character = read(entity).checktable()
        val worldL = with(lua) { world.luaWorld() }
        val iterated = functions.get("iterate").call(worldL).get(1)

        assertSame(character, iterated)
        assertEquals(
            setOf("name", "biology", "sex", "height", "body_type", "appearance", "looks", "base_look"),
            character.fieldNames(),
        )
        assertEquals("humanlike", character.get("biology").checkjstring())
        assertEquals("female", character.get("sex").checkjstring())
        assertEquals(1.75, character.get("height").checkdouble())
        assertEquals("broad", character.get("body_type").checkjstring())
        val name = character.get("name").checktable()
        assertEquals(setOf("text", "color1", "color2"), name.fieldNames())
        assertEquals(profile.name.name, name.get("text").checkjstring())
        assertEquals(profile.name.firstColor.integer, name.get("color1").checkint())
        assertEquals(profile.name.secondColor!!.integer, name.get("color2").checkint())
        val appearance = character.get("appearance").checktable()
        assertEquals(setOf("breastSize", "eyeLevel", "description"), appearance.fieldNames())
        assertEquals(0.5, appearance.get("breastSize").checkdouble())
        assertEquals(3, appearance.get("eyeLevel").checkint())
        assertEquals(profile.appearanceDescription, appearance.get("description").checkjstring())
        assertEquals(2, character.get("looks").length())
        assertEquals("other", character.get("looks").get(1).get("id").checkjstring())
        assertEquals("base", character.get("looks").get(2).get("id").checkjstring())
        assertEquals("base", character.get("base_look").get("id").checkjstring())
    }

    @Test
    fun characterDataMappingIncludesEveryLookAndItsNestedFieldsInOrder() {
        val data = appliedCharacter(profile()).character.data

        val character = data.toLuaValue()
        val looks = character.get("looks").checktable()

        assertEquals(data.looks.size, looks.length())
        data.looks.forEachIndexed { index, look ->
            val value = looks.get(index + 1)
            assertEquals(look.id, value.get("id").checkjstring())
            assertEquals(look.title, value.get("title").checkjstring())
            assertEquals(look.skin.url, value.get("skin").get("url").checkjstring())
            assertEquals(look.appearanceDescriptionAddon, value.get("description").checkjstring())
            assertEquals(look.base, value.get("base").checkboolean())
        }
        assertEquals(data.baseLook.id, character.get("base_look").get("id").checkjstring())
        assertEquals(data.baseLook.skin.url, character.get("base_look").get("skin").get("url").checkjstring())
    }

    @Test
    fun emptyLookListDoesNotDiscardTheExplicitBaseLook() {
        val data = appliedCharacter(profile()).character.data.copy(looks = emptyList())

        val character = data.toLuaValue()

        assertEquals(0, character.get("looks").checktable().length())
        assertEquals(data.baseLook.id, character.get("base_look").get("id").checkjstring())
    }

    @Test
    fun characterAndAppliedLookKeepIndependentCachedPresentationsOnTheSameEntity() {
        val component = appliedCharacter(profile())
        val selectedLook = component.character.data.looks.first()
        val entity = with(world) {
            world.addEntity {
                setComponent(component)
                setComponent(SelectedLook(selectedLook))
            }
        }
        val entityL = with(lua) { with(world) { entity.luaEntity() } }

        val character = read(entity)
        val look = functions.get("get_look").call(entityL)

        assertNotSame(character, look)
        assertEquals(component.character.data.profile.name.name, character.get("name").get("text").checkjstring())
        assertEquals(selectedLook.id, look.get("id").checkjstring())
        assertSame(character, read(entity))
        assertSame(look, functions.get("get_look").call(entityL))
    }

    @Test
    fun gradientNameWithoutSecondColorRepeatsTheFirstColor() {
        val name = CharacterGradientName("Одноцветное имя", Color.WHITE).toLuaValue()

        assertEquals("Одноцветное имя", name.get("text").checkjstring())
        assertEquals(Color.WHITE.integer, name.get("color1").checkint())
        assertEquals(Color.WHITE.integer, name.get("color2").checkint())
    }

    @Test
    fun appearanceCanBeConvertedIndependentlyWithItsDescription() {
        val appearance = CharacterAppearance(breastSize = 0f, eyeLevel = 2).toLuaValue("Описание")

        assertEquals(0.0, appearance.get("breastSize").checkdouble())
        assertEquals(2, appearance.get("eyeLevel").checkint())
        assertEquals("Описание", appearance.get("description").checkjstring())
    }

    @Test
    fun biologyConversionMatchesEveryAnnotatedLiteral() {
        val expected = mapOf(
            BiologicalCategory.HUMAN to "human",
            BiologicalCategory.HUMANLIKE_HUMANOID to "humanlike",
            BiologicalCategory.HUMANOID to "humanoid",
            BiologicalCategory.CREATURE to "creature",
        )

        for (value in BiologicalCategory.entries) {
            assertEquals(expected.getValue(value), value.toLuaValue().checkjstring())
        }
    }

    @Test
    fun sexConversionMatchesEveryAnnotatedLiteral() {
        val expected = mapOf(
            BiologicalSex.MALE to "male",
            BiologicalSex.FEMALE to "female",
            BiologicalSex.OTHER to "other",
        )

        for (value in BiologicalSex.entries) {
            assertEquals(expected.getValue(value), value.toLuaValue().checkjstring())
        }
    }

    @Test
    fun bodyTypeConversionMatchesEveryAnnotatedLiteral() {
        val expected = mapOf(BodyType.SLIM to "slim", BodyType.NORMAL to "normal", BodyType.BROAD to "broad")

        for (value in BodyType.entries) {
            assertEquals(expected.getValue(value), value.toLuaValue().checkjstring())
        }
    }

    @Test
    fun heightConversionUsesMeters() {
        assertEquals(1.625, CharacterHeight(1.625f).toLuaValue().checkdouble())
    }

    @Test
    fun convertedTablesDoNotMutateTheOriginalProfileOrOtherConversions() {
        val profile = profile()
        val first = profile.toLuaValue()
        val second = profile.toLuaValue()

        first.get("name").set("text", LuaValue.valueOf("Другое имя"))
        first.get("appearance").set("breastSize", LuaValue.valueOf(99))

        assertNotSame(first, second)
        assertNotSame(first.get("name"), second.get("name"))
        assertNotSame(first.get("appearance"), second.get("appearance"))
        assertEquals(profile.name.name, second.get("name").get("text").checkjstring())
        assertEquals(profile.appearance.breastSize, second.get("appearance").get("breastSize").tofloat())
    }

    @Test
    fun replacedAppliedCharacterIsVisibleOnTheNextTick() {
        val firstProfile = profile()
        val secondProfile = firstProfile.copy(name = CharacterGradientName("Другой персонаж", Color.WHITE))
        val entity = addCharacter(firstProfile)
        val first = read(entity)

        with(world) { entity.setComponent(appliedCharacter(secondProfile)) }
        world.simulation.ticks++
        val second = read(entity)

        assertNotSame(first, second)
        assertEquals(firstProfile.name.name, first.get("name").get("text").checkjstring())
        assertEquals(secondProfile.name.name, second.get("name").get("text").checkjstring())
    }

    private fun profile() = CharacterProfile(
        name = CharacterGradientName("Имя персонажа", Color(0xFF123456), Color(0xFFABCDEF)),
        biologicalCategory = BiologicalCategory.HUMANLIKE_HUMANOID,
        biologicalSex = BiologicalSex.FEMALE,
        appearanceDescription = "Описание внешности",
        height = CharacterHeight(1.75f),
        bodyType = BodyType.BROAD,
        appearance = CharacterAppearance(breastSize = 0.5f, eyeLevel = 3),
    )

    private fun appliedCharacter(profile: CharacterProfile): AppliedCharacter {
        val id = ServerCharacterId("test-character")
        val baseLook = Look("base", "Основной", Skin("https://example.com/base.png"), "Описание основного образа", true)
        val otherLook = Look("other", "Другой", Skin("https://example.com/other.png"), "Описание другого образа", false)
        return AppliedCharacter(
            ServerCharacterData(id, CharacterData(profile, listOf(otherLook, baseLook), baseLook)),
            OwnedCharacterId.server(id),
        )
    }

    private fun addCharacter(profile: CharacterProfile): EntityId = with(world) {
        world.addEntity { setComponent(appliedCharacter(profile)) }
    }

    private fun read(entity: EntityId): LuaValue {
        val entityL = with(lua) { with(world) { entity.luaEntity() } }
        return functions.get("get").call(entityL)
    }

    private fun LuaTable.fieldNames(): Set<String> = keys().mapTo(mutableSetOf()) { it.checkjstring() }
}
