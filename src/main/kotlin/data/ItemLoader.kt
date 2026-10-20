package org.lain.engine.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.engine.item.EngineItem
import org.lain.engine.item.createInvalidItem
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.CharacterId
import org.lain.engine.player.character.PlayCharacterId
import org.lain.engine.server.EngineServer
import org.lain.engine.util.LogDiagnosticContext
import org.lain.engine.util.append
import org.lain.engine.util.math.Pos

// Для логирования
sealed class ItemLoadContext {
    abstract fun logContext(context: LogDiagnosticContext = LogDiagnosticContext()): LogDiagnosticContext

    data class FromInventory(val player: PlayerId?, val voxelPos: Pos?) : ItemLoadContext() {
        override fun logContext(context: LogDiagnosticContext) = context.append(
            "context" to "from_inventory",
            "player_id" to player.toString(),
            "voxel_pos" to voxelPos.toString()
        )
    }

    data class PreparingCharacter(val playerId: PlayerId, val character: PlayCharacterId) : ItemLoadContext() {
        override fun logContext(context: LogDiagnosticContext): LogDiagnosticContext = context.append(
            "context" to "preparing_character",
            "player_id" to playerId.toString(),
            "character_id" to character.toString()
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
        return withContext(Dispatchers.IO) {
            val diagnosticContext = itemLoadContext.logContext()
            try {
                transactionContext.withChild { childTransactionContext ->
                    context(diagnosticContext, childTransactionContext.commands) {
                        EntityLoadOperation(uuid, database, childTransactionContext)
                            .execute()
                            .getEntityOrThrow()
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                context(diagnosticContext, transactionContext.commands) {
                    server.createInvalidItem(transactionContext.world)
                }
            }
        }
    }
}
