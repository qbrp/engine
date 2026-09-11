package org.lain.engine.client.account

import kotlinx.serialization.json.Json
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.util.file.FileSystem

object AccountResponseCache {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val file = FileSystem.accountCache

    fun load(): AccountResponse? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<AccountResponse>(file.readText())
        }.getOrNull()
    }

    fun save(response: AccountResponse) {
        runCatching {
            FileSystem.ensureFile(file)
            file.writeText(json.encodeToString(response))
        }
    }
}
