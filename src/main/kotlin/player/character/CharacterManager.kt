package org.lain.engine.player.character

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.PlayCharacterData
import org.lain.engine.data.TransactionContext
import org.lain.engine.data.createTransactionContext
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Equipment
import org.lain.engine.player.PlayerId
import org.lain.engine.player.account.SessionTicketDto
import org.lain.engine.player.get
import org.lain.engine.player.require
import org.lain.engine.server.EngineServer
import org.lain.engine.world.Location
import org.lain.engine.world.location
import java.util.concurrent.ConcurrentHashMap

@Serializable
sealed interface CharacterChangeRequest {
    val id: CharacterId

    @Serializable
    data class Provided(
        val character: BackendCharacter
    ) : CharacterChangeRequest {
        override val id: CharacterId
            get() = character.id
    }

    @Serializable
    data class ResolveByTicket(
        val characterId: CharacterId, val ticket: SessionTicketDto
    ) : CharacterChangeRequest {
        override val id: CharacterId
            get() = characterId
    }
}

interface EngineBackend {
    suspend fun validateCharacterChange(
        player: EnginePlayer, request: CharacterChangeRequest
    ): BackendCharacter
}

class CharacterOccupiedException(val player: PlayerId, val id: PlayCharacterId) : RuntimeException(
    "Failed to occupy character $id by $player: already occupied"
)

class CharacterManager(
    private val server: EngineServer, private val backend: EngineBackend
) {
    private val platform = server.platform
    private val persistence = server.playerPersistence
    private val handler = server.handler
    private val occupiedCharacters = ConcurrentHashMap<PlayCharacterId, PlayerId>()
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    private fun occupy(context: TransactionContext, player: EnginePlayer, id: PlayCharacterId) {
        val occupiedBy = occupiedCharacters.putIfAbsent(id, player.id)
        if (occupiedBy != null) {
            if (occupiedBy != player.id || player.get<AppliedCharacter>()?.id != id) {
                throw CharacterOccupiedException(player.id, id)
            }
            return
        }

        context.afterRollback { occupiedCharacters.remove(id, player.id) }
    }

    fun release(player: EnginePlayer) {
        player.get<AppliedCharacter>()?.let { applied ->
            occupiedCharacters.remove(applied.id, player.id)
        }
    }

    fun close() {
        coroutineScope.cancel()
    }

    fun change(
        player: EnginePlayer,
        request: CharacterChangeRequest,
        id: PlayCharacterId,
        requestId: Long,
    ) {
        val componentReviveSettings = player.world.componentReviveSettings
        val world = player.world
        val context = server.createTransactionContext(world)
        coroutineScope.launch {
            try {
                val validatedCharacter = backend.validateCharacterChange(player, request)
                val characterRecord = persistence.loadPersistentCharacter(
                    context, componentReviveSettings, player.id, id
                )
                // Пока что вполне логично относить применение персонажа к DataPrepare
                // Во многом это не побочный эффект других механик, а именно источник данных
                withContext(server.dataPrepareDispatcher) {
                    val serializedInventory = platform.serializeInventory(player)
                    try {
                        player.unloadCharacter(platform, persistence, serializedInventory)
                        apply(context, player, validatedCharacter, characterRecord, id)
                        context.commit()
                        handler.confirmCharacterApply(player, requestId)
                    } catch (e: Exception) {
                        platform.openInventory(player, serializedInventory)
                        throw e
                    }
                }
            } catch (e: Exception) {
                context.rollbackIfActive(e)
                if (e !is CancellationException) {
                    e.printStackTrace()
                    handler.confirmCharacterApply(player, requestId, "Не удалось применить персонажа")
                }
                throw e
            }
        }
    }

    fun apply(
        context: TransactionContext,
        player: EnginePlayer,
        character: BackendCharacter,
        data: PlayCharacterData?,
        id: PlayCharacterId,
        location: Location = player.location,
    ) = with(context.commands) {
        occupy(context, player, id)

        val world = player.world
        val entity = player.entity
        val previousCharacterId = player.get<AppliedCharacter>()?.id
        val lastLook = data?.look?.let { lookId -> character.looks.firstOrNull { it.id == lookId } }

        if (data == null) {
            entity.setComponent(character.getPhysical())
        }

        entity.setComponent(character.getDisplay())
        entity.setComponent(SelectedLook(lastLook ?: character.baseLook))
        entity.setComponent(AppliedCharacter(character, id))
        entity.setComponent(Equipment(data?.equipment?.toMutableMap() ?: mutableMapOf()))
        data?.components?.applyResolved(player.entity)

        world.emitEvent(
            CharacterApplyEvent(character, player.id),
            position = location.position
        )

        if (previousCharacterId != null && previousCharacterId != id) {
            context.afterCommit { occupiedCharacters.remove(previousCharacterId, player.id) }
        }
        context.afterCommit {
            platform.clearInventory(player)
            data?.inventory?.let {
                platform.openInventory(player, it)
            }
            player.require<UsedCharacters>().characters += id
            platform.onCharacterApplied(player, character)
        }
    }
}
