package org.lain.engine.client.handler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.lain.engine.storage.ComponentDto
import org.lain.engine.storage.EntityProvider
import org.lain.engine.storage.PersistentId

class PendingEntityProvider(private val pendingEntities: MutableMap<PersistentId, CompletableDeferred<PendingEntity?>>) : EntityProvider {
    override suspend fun loadEntity(
        persistentId: PersistentId
    ): List<ComponentDto>? {
        return withTimeoutOrNull(5000) {
            pendingEntities
                .computeIfAbsent(persistentId) {
                    CompletableDeferred()
                }
                .await()
                ?.components
        }
    }
}

data class PendingEntity(val components: List<ComponentDto>)