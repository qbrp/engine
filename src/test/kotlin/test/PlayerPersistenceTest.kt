package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.data.PersistentPlayerData
import org.lain.engine.data.PersistentPlayerSnapshot
import org.lain.engine.player.PlayerId
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.character.CharacterId
import org.lain.engine.player.character.PlayCharacterId
import java.nio.file.Path
import java.util.UUID

class PlayerPersistenceTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun currentUsedCharactersSurviveSaveAndLoad() = runBlocking {
        val server = setupTestEngineServer(tempDir)
        try {
            val playerId = PlayerId(UUID.randomUUID())
            val characterId = PlayCharacterId.player(playerId, CharacterId("character"))
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
}
