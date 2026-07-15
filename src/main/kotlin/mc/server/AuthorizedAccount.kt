package org.lain.engine.mc.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.account.CharacterData
import java.net.http.HttpClient
import java.time.Instant

open class AuthorizedAccount(
    private val httpClient: EngineHttpClient,
    val bearerToken: String,
    val expireTime: Instant
) {
    private fun checkValid() {
        check(Instant.now().isBefore(expireTime)) {
            "Backend session expired"
        }
    }

    suspend fun getCharacter(id: String): CharacterData = withContext(Dispatchers.IO) {
        checkValid()
        httpClient.getJson("characters/$id", bearerToken)
    }

    suspend fun getAccount(): AccountResponse = withContext(Dispatchers.IO) {
        checkValid()
        httpClient.getJson("/me", bearerToken = bearerToken)
    }
}