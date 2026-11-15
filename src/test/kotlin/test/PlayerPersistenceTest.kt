package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.data.PersistentPlayerData
import org.lain.engine.data.PersistentPlayerSnapshot
import org.lain.engine.data.playerData
import org.lain.engine.data.snapshotPersistent
import org.lain.engine.player.CustomPlayerAttributes
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.DisplayName
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerInstantiateSettings
import org.lain.engine.player.Username
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.require
import org.lain.engine.player.serverPlayerInstance
import org.lain.engine.player.set
import org.lain.engine.player.character.BackendCharacterId
import org.lain.engine.player.character.OwnedCharacterId
import org.lain.engine.player.character.ServerCharacterId
import org.lain.engine.script.SInt
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.world.World
import java.nio.file.Path
import java.util.UUID

class PlayerPersistenceTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun customAttributesSurviveSnapshotSaveLoadAndInstantiation() = runBlocking {
        val server = setupTestEngineServer(tempDir)
        try {
            val world = server.simulation.defaultWorld
            val player = createPlayer(world)
            val nestedValues = mutableMapOf<ScriptValue, ScriptValue>(SString("level") to SInt(7))
            val custom = CustomPlayerAttributes(
                speed = 0.15f,
                jumpStrength = 0.5f,
                gravity = 0.08f,
                script = mutableMapOf(
                    "mana" to SNumber(42.5),
                    "stats" to STable(nestedValues),
                ),
            )
            player.set(custom)
            val expected = custom.copy()
            val snapshot = player.snapshotPersistent()

            custom.speed = null
            custom.script.clear()
            nestedValues[SString("level")] = SInt(99)
            server.playerPersistence.save(snapshot)

            val loaded = requireNotNull(server.playerPersistence.loadRecord(player.id))
            assertEquals(expected, loaded.data.customAttributes)
            assertEquals(
                STable(mapOf(SString("level") to SInt(7))),
                loaded.data.customAttributes.script["stats"],
            )

            val restored = createPlayer(world, loaded.data).require<CustomPlayerAttributes>()
            assertEquals(expected, restored)
            assertNotSame(loaded.data.customAttributes.script["stats"], restored.script["stats"])
            restored.speed = null
            restored.script.clear()
            assertEquals(expected, loaded.data.customAttributes)
        } finally {
            server.playerPersistence.close()
            server.chunkPersistence.close()
            server.characterManager.close()
            server.chat.close()
        }
    }

    @Test
    fun legacyPlayerRecordWithoutCustomAttributesLoadsWithDefaults() = runBlocking {
        val server = setupTestEngineServer(tempDir)
        try {
            val playerId = PlayerId(UUID.randomUUID())
            tempDir.resolve("world").toFile().playerData.resolve("$playerId.json").writeText(
                """
                {
                  "id": "$playerId",
                  "data": {
                    "custom_name": null,
                    "speed_intention": 0.0,
                    "stamina": 1.0,
                    "voiceApparatus": {"inputVolume": 1.0},
                    "voiceLoose": null
                  }
                }
                """.trimIndent()
            )

            val loaded = requireNotNull(server.playerPersistence.loadRecord(playerId))
            assertEquals(CustomPlayerAttributes(), loaded.data.customAttributes)
            val restored = createPlayer(server.simulation.defaultWorld, loaded.data)
            assertEquals(CustomPlayerAttributes(), restored.require<CustomPlayerAttributes>())
        } finally {
            server.playerPersistence.close()
            server.chunkPersistence.close()
            server.characterManager.close()
            server.chat.close()
        }
    }

    @Test
    fun currentUsedCharactersSurviveSaveAndLoad() = runBlocking {
        val server = setupTestEngineServer(tempDir)
        try {
            val playerId = PlayerId(UUID.randomUUID())
            val characterId = OwnedCharacterId.player(playerId, ServerCharacterId("variant"))
            server.playerPersistence.save(
                PersistentPlayerSnapshot(
                    playerId,
                    PersistentPlayerData(
                        customName = null,
                        speedIntention = 0f,
                        stamina = 1f,
                        voiceApparatus = VoiceApparatus(1f),
                        voiceLoose = null,
                        usedCharacters = setOf(characterId),
                    ),
                    emptyList(),
                )
            )

            val loaded = requireNotNull(server.playerPersistence.loadRecord(playerId))

            assertEquals(setOf(characterId), loaded.data.usedCharacters)
        } finally {
            server.playerPersistence.close()
            server.chunkPersistence.close()
            server.characterManager.close()
            server.chat.close()
        }
    }

    private fun createPlayer(world: World, persistent: PersistentPlayerData? = null): EnginePlayer = with(world) {
        serverPlayerInstance(
            settings = PlayerInstantiateSettings(
                world = world,
                pos = MutableEVec3(),
                displayName = DisplayName(Username("TestPlayer")),
                developerModeStatus = DeveloperModeStatus(),
            ),
            persistent = persistent,
            defaults = DefaultPlayerAttributes(),
            id = PlayerId(UUID.randomUUID()),
            entity = world.addEntity(),
        )
    }
}
