package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.CharacterDatabasePersistentId
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.MaterializedComponents
import org.lain.engine.data.PersistentCharacterRecord
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.BiologicalCategory
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.BodyType
import org.lain.engine.player.character.CharacterGradientName
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.player.character.CharacterId
import org.lain.engine.player.character.CharacterPhysical
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.GenderParams
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.Skin
import org.lain.engine.player.character.UsedCharacters
import org.lain.engine.player.character.applyCharacter
import org.lain.engine.server.ServerPlatform
import org.lain.engine.util.Color
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.util.UUID

class CharacterInstantiationTest : EngineTest() {
    @Test
    fun applyingPersistentCharacterPreservesPlayerPersistentId() {
        val simulation = TestEngineSimulation()
        val world = World(WorldId("test"), simulation)
        simulation.loadWorld(world)

        val playerId = PlayerId(UUID.randomUUID())
        val playerPersistentId = playerId.asPersistentId()
        val playerEntity = world.addEntity {
            setComponent(PersistentIdComponent(playerPersistentId))
            setComponent(UsedCharacters(mutableSetOf()))
            setComponent(Location(MutableEVec3()))
        }
        val player = EnginePlayer(playerId, playerEntity, world)

        val characterId = CharacterId("character")
        val look = Look(
            id = "look",
            title = "Look",
            skin = Skin("https://example.com/skin.png"),
            appearanceDescriptionAddon = "",
            base = true,
        )
        val character = EngineCharacter(
            profile = CharacterProfile(
                id = characterId,
                accountId = "account",
                name = CharacterGradientName("Character", Color.WHITE),
                biologicalCategory = BiologicalCategory.HUMAN,
                biologicalSex = BiologicalSex.FEMALE,
                appearanceDescription = "",
                height = CharacterHeight(1.7f),
                bodyType = BodyType.NORMAL,
                genderParams = GenderParams(0f),
            ),
            looks = listOf(look),
            baseLook = look,
        )
        val savedPhysical = CharacterPhysical(BodyType.BROAD, BiologicalSex.FEMALE)
        val persistentCharacter = PersistentCharacterRecord(
            id = characterId,
            components = MaterializedComponents(
                persistentId = CharacterDatabasePersistentId(playerId, characterId),
                data = EntityPersistenceData.Character(look.id, ""),
                resolved = listOf(savedPhysical),
                unresolved = emptyList(),
            ),
            items = "",
            look = look.id,
        )

        player.applyCharacter(character, persistentCharacter, ServerPlatform.DUMMY)

        with(world) {
            assertEquals(
                playerPersistentId,
                playerEntity.requireComponent<PersistentIdComponent>().id,
            )
            assertEquals(
                savedPhysical,
                playerEntity.requireComponent<CharacterPhysical>(),
            )
        }
    }
}
