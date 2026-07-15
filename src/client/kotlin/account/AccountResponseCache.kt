package org.lain.engine.client.account

import kotlinx.serialization.json.Json
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.ensureExists

object AccountResponseCache {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val file = ENGINE_DIR
        .resolve("account.json")

    fun load(): AccountResponse? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<AccountResponse>(file.readText())
        }.getOrNull()
    }

    fun save(response: AccountResponse) {
        runCatching {
            file.ensureExists()
            file.writeText(json.encodeToString(response))
        }
    }
}
