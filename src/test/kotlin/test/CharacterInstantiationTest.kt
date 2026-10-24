package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.MaterializedComponents
import org.lain.engine.data.PlayCharacterData
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.createTransactionContext
import org.lain.engine.data.databasePersistentId
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
import org.lain.engine.player.character.BackendCharacter
import org.lain.engine.player.character.GenderParams
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.PlayCharacterId
import org.lain.engine.player.character.Skin
import org.lain.engine.player.character.UsedCharacters
import org.lain.engine.util.Color
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.world.Location
import java.nio.file.Path
import java.util.UUID

class CharacterInstantiationTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun applyingPersistentCharacterPreservesPlayerPersistentId() = runBlocking {
        val server = setupTestEngineServer(tempDir)
        try {
            val world = server.simulation.defaultWorld

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
            val character = BackendCharacter(
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
            val playCharacterId = PlayCharacterId.player(playerId, characterId)
            val persistentCharacter = PlayCharacterData(
                id = playCharacterId,
                components = MaterializedComponents(
                    persistentId = playCharacterId.databasePersistentId(),
                    data = EntityPersistenceData.Character(look.id, ""),
                    resolved = listOf(savedPhysical),
                    unresolved = emptyList(),
                    migrations = emptyList()
                ),
                inventory = "",
                look = look.id,
                items = emptySet()
            )

            val context = server.createTransactionContext(world)
            server.characterManager.apply(
                context,
                player,
                character,
                persistentCharacter,
                playCharacterId,
            )
            context.commit()

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
        } finally {
            server.playerPersistence.close()
            server.chunkPersistence.close()
            server.characterManager.close()
            server.chat.close()
        }
    }
}
