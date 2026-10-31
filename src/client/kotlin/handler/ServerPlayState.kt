package org.lain.engine.client.handler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.lain.engine.client.render.player.CharacterSceneSnapshot
import org.lain.engine.data.polymorphicComponentSerializer
import org.lain.engine.player.character.CharacterSource
import org.lain.engine.server.ServerId
import org.lain.engine.util.file.FileSystem

data class LocalCharacter(
    val source: CharacterSource,
    var renderState: CharacterSceneSnapshot? = null
)

@Serializable
data class ServerPlayState(
    val character: CharacterSource?,
    val characters: Map<CharacterSource, Character>
) {
    @Serializable
    data class Character(
        val source: CharacterSource,
        val renderState: CharacterSceneSnapshot?
    )

    fun currentCharacter() = characters[character]

    fun update(character: LocalCharacter): ServerPlayState {
        val charactersCopy = characters.toMutableMap()
        val source = character.source
        charactersCopy[source] = Character( source, character.renderState)
        return copy(
            character = source,
            characters = charactersCopy
        )
    }

    suspend fun save(server: ServerId) = withContext(Dispatchers.IO) {
        val file = resolveFile(server)
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(JSON.encodeToString<ServerPlayState>(this@ServerPlayState))
    }

    companion object {
        private val JSON = Json {
            allowStructuredMapKeys = true
            serializersModule = SerializersModule { polymorphicComponentSerializer() }
        }
        private fun resolveFile(server: ServerId) =
            FileSystem.serverPlayStates.resolve(server.value + ".json")

        suspend fun open(server: ServerId): ServerPlayState? = withContext(Dispatchers.IO) {
            val file = resolveFile(server)
            if (!file.exists()) {
                null
            } else {
                runCatching { JSON.decodeFromString<ServerPlayState>(file.readText()) }
                    .onFailure { e ->
                        FileSystem.LOGGER.warn("Не удалось прочитать игровое состояния сервера ${file.path}", e)
                    }
                    .getOrNull()
            }
        }
    }
}