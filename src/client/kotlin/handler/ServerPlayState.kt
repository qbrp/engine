package org.lain.engine.client.handler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.engine.server.ServerId
import org.lain.engine.util.file.FileSystem

@Serializable
data class ServerPlayState(val character: String?) {
    suspend fun save(server: ServerId) = withContext(Dispatchers.IO) {
        val file = resolveFile(server)
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(Json.encodeToString(ServerPlayState(character)))
    }

    companion object {
        private fun resolveFile(server: ServerId) =
            FileSystem.serverPlayStates.resolve(server.value + ".json")

        suspend fun open(server: ServerId): ServerPlayState? = withContext(Dispatchers.IO) {
            val file = resolveFile(server)
            if (!file.exists()) {
                null
            } else {
                runCatching { Json.decodeFromString<ServerPlayState>(file.readText()) }
                    .onFailure { e ->
                        FileSystem.LOGGER.warn("Не удалось прочитать игровое состояния сервера ${file.path}", e)
                    }
                    .getOrNull()
            }
        }
    }
}