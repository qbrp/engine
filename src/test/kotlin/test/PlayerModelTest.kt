package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayerModel
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
import org.lain.engine.player.character.Skin
import org.lain.engine.player.scale
import org.lain.engine.player.tickPlayerModelSystem
import org.lain.engine.util.Color
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

class PlayerModelTest : EngineTest() {
    @Test
    fun serverAndClientModelsDeriveCurrentParametersFromTheAppliedCharacter() {
        for (isClient in listOf(false, true)) {
            val world = World(WorldId("model-test"), TestEngineSimulation(isClient = isClient))
            val model = EnginePlayerModel(lastBreastsSize = 0.2f)
            val first = character(0.5f)
            val second = character(0.8f)
            val entity = with(world) {
                world.addEntity {
                    setComponent(model)
                    setComponent(first)
                }
            }

            world.tickPlayerModelSystem()

            assertEquals(first.character.data.profile.height.scale, model.scale)
            assertEquals(0.5f, model.breastsSize)
            assertEquals(0.2f, model.lastBreastsSize)
            with(world) { entity.setComponent(second) }
            world.tickPlayerModelSystem()

            assertEquals(0.8f, model.breastsSize)
            assertEquals(0.2f, model.lastBreastsSize)
        }
    }

    private fun character(breastSize: Float): AppliedCharacter {
        val id = ServerCharacterId("test-character")
        val look = Look("base", "Основной", Skin("https://example.com/skin.png"), "", true)
        val profile = CharacterProfile(
            CharacterGradientName("Персонаж", Color.WHITE), BiologicalCategory.HUMAN, BiologicalSex.FEMALE,
            "Описание", CharacterHeight(1.75f), BodyType.NORMAL, CharacterAppearance(breastSize, 2),
        )
        return AppliedCharacter(
            ServerCharacterData(id, CharacterData(profile, listOf(look), look)),
            OwnedCharacterId.server(id),
        )
    }
}
