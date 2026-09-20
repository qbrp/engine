package org.lain.engine.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.item.EngineItem
import org.lain.engine.item.createInvalidItem
import org.lain.engine.player.PlayerId
import org.lain.engine.server.EngineServer
import org.lain.engine.util.LogDiagnosticContext
import org.lain.engine.util.append
import org.lain.engine.util.math.Pos

// Для логирования
sealed class ItemLoadContext {
    abstract fun logContext(context: LogDiagnosticContext = LogDiagnosticContext()): LogDiagnosticContext

    data class PreparingPlayer(val playerId: PlayerId, val name: String) : ItemLoadContext() {
        override fun logContext(context: LogDiagnosticContext) = context.append(
            "context" to "preparing_player",
            "player_id" to playerId.toString(),
            "player_name" to name
        )
    }

    data class FromInventory(val player: PlayerId?, val voxelPos: Pos?) : ItemLoadContext() {
        override fun logContext(context: LogDiagnosticContext) = context.append(
            "context" to "from_inventory",
            "player_id" to player.toString(),
            "voxel_pos" to voxelPos.toString()
        )
    }
}

class ItemLoader(
    private val server: EngineServer,
    private val database: Database
) {
    suspend fun loadWorldItem(
        uuid: PersistentId,
        itemLoadContext: ItemLoadContext,
        transactionContext: TransactionContext,
    ): EngineItem {
        require(uuid is Uuid)
        val childTransactionContext = transactionContext.child()
        return withContext(Dispatchers.IO) {
            context(itemLoadContext.logContext(), childTransactionContext.commands) {
                val result = EntityLoadOperation(uuid, database, childTransactionContext)
                    .execute()

                val item = when(result) {
                    is EntityLoadResult.Success -> {
                        childTransactionContext.commit()
                        result.entity
                    }
                    is EntityLoadResult.Failure -> {
                        childTransactionContext.rollback(result.error)
                        server.createInvalidItem()
                    }
                }
                item
            }
        }
    }
}