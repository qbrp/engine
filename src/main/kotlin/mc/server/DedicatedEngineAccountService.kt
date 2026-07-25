package org.lain.engine.mc.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.net.URI
import java.time.Duration
import java.time.Instant

class DedicatedEngineAccountService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = EngineHttpClient(
        requestTimeout = Duration.ofSeconds(5)
    )

    fun getAuthorized(sessionTicket: SessionTicket): AuthorizedAccount {
        if (Instant.now().isAfter(sessionTicket.expiresAt)) {
            error("Время действия сессионного тикета истекло")
        }
        return AuthorizedAccount(
            httpClient,
            sessionTicket.hash,
            sessionTicket.expiresAt
        )
    }
}