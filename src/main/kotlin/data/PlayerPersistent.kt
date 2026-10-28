package org.lain.engine.data

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.lain.engine.player.*
import org.lain.engine.player.character.AppliedCharacter
import org.lain.engine.player.character.BackendCharacterId
import org.lain.engine.player.character.OwnedCharacterId
import org.lain.engine.player.character.ServerCharacterId
import org.lain.engine.player.character.UsedCharacters
import org.lain.engine.server.EngineServer
import org.lain.engine.util.Color
import org.lain.engine.util.LogDiagnosticContext
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.file.FileSystem
import org.lain.engine.util.file.writeTextAtomically
import org.lain.engine.world.World
import java.io.File
import java.util.concurrent.ConcurrentHashMap

//TODO: Persistence jobs неупорядочены и не дожидаются shutdown. Player/chunk/world saves пишут напрямую в конечный файл без temp+atomic move; более старый job способен завершиться последним. Shutdown ждёт только отдельный blocking item save. Возможны torn JSON/CBOR и потеря последних изменений. PlayerPersistent.kt:81, ChunksPersistent.kt:52, EngineMinecraftServer.kt:183

val File.playerData
    get() = this.resolve("engine-players")
        .let(FileSystem::ensureDirectory)

private val JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule { polymorphicComponentSerializer() }
    allowStructuredMapKeys = true
}

@Serializable
data class CustomNamePersistentData(
    val string: String,
    val color1: Color,
    val color2: Color? = null,
) {
    fun toDomain(name: String): CustomName? = try {
        CustomName(string, color1, color2)
    } catch (e: InvalidCustomNameException) {
        LOGGER.warn("Игрок $name имеет неправильное имя: $string (${e.message})")
        null
    }
}

private fun CustomName.toPersistentData() = CustomNamePersistentData(string, color1, color2)

typealias SerializedInventory = String

@Serializable
data class PersistentPlayerData(
    @SerialName("custom_name") val customName: CustomNamePersistentData?,
    @SerialName("speed_intention") val speedIntention: Float,
    val stamina: Float,
    val voiceApparatus: VoiceApparatus,
    val voiceLoose: VoiceLoose?,
    @SerialName("chat_heads") val chatHeads: Boolean = true,
    val appliedCharacter: ServerCharacterId? = null,
    @SerialName("usedCharacters") val usedCharactersLegacy: Set<BackendCharacterId> = emptySet(),
    @SerialName("used_characters") val usedCharacters: Set<OwnedCharacterId> = emptySet()
)

data class PersistentCharacterSnapshot(
    val id: ServerCharacterId,
    val look: String,
    val inventory: SerializedInventory,
    val items: List<PersistentId>,
    val equipment: Map<EquipmentSlotId, PersistentId> = mapOf(),
    val components: List<SavingComponentSnapshot>
)

data class PersistentPlayerSnapshot(
    val id: PlayerId,
    val data: PersistentPlayerData,
    val components: List<SavingComponentSnapshot>
)

@Serializable
data class PersistentPlayerRecord(
    val id: PlayerId,
    val data: PersistentPlayerData,
)

data class PlayCharacterData(
    val id: OwnedCharacterId,
    val components: MaterializedComponents,
    val inventory: SerializedInventory,
    val items: Set<EntityId>,
    val look: String,
    val equipment: Map<EquipmentSlotId, EntityId> = emptyMap(),
)

fun OwnedCharacterId.databasePersistentId(): PersistentId {
    return CustomPersistentId(stringRepresentation())
}

context(world: World)
fun EntityId.savingSnapshot() =
    world.componentManager.getSavableComponents(this)
        .map { (type, component) -> SavingComponentSnapshot(component.snapshot(), type) }

fun EnginePlayer.snapshotPersistent(): PersistentPlayerSnapshot = with(world) {
    val movementStatus = require<MovementStatus>()

    PersistentPlayerSnapshot(
        id = this@snapshotPersistent.id,
        data = PersistentPlayerData(
            customName = customName?.toPersistentData(),
            speedIntention = movementStatus.intention,
            stamina = movementStatus.stamina,
            voiceApparatus = require<VoiceApparatus>().copy(),
            voiceLoose = get<VoiceLoose>()?.copy(),
            chatHeads = chatHeadsEnabled,
            appliedCharacter = get<AppliedCharacter>()?.id?.character,
            usedCharacters = get<UsedCharacters>()?.characters.orEmpty()
        ),
        components = entity.savingSnapshot(),
    )
}

class PlayerPersistence(server: EngineServer) {
    private val database = server.database
    private val itemLoader = server.itemLoader
    private val directory = server.globals.savePath.playerData

    private val playerMutexes = ConcurrentHashMap<PlayerId, Mutex>()
    private val characterMutexes = ConcurrentHashMap<OwnedCharacterId, Mutex>()

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)


    suspend fun close() {
        withContext(NonCancellable) {
            supervisor.complete()
            supervisor.join()
        }
    }

    fun save(snapshot: PersistentPlayerSnapshot) {
        launchSave(playerMutex(snapshot.id)) {
            snapshot.save(database, directory)
        }
    }

    private suspend fun PersistentPlayerSnapshot.save(
        database: Database,
        playersFile: File,
    ) = withContext(Dispatchers.IO) {
        try {
            // в будущем надо сделать это действие полностью атомарным
            val file = playersFile.resolve("$id.json")
            FileSystem.ensureFile(file)

            file.writeTextAtomically(
                JSON.encodeToString(
                    PersistentPlayerRecord(id, data)
                )
            )

            database.saveEntity(
                EntityDatabaseKind.PLAYER,
                EntityPersistentRecord(
                    id.asPersistentId(),
                    null,
                    components.map { it.serializeToRecord() },
                    emptyList(),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOGGER.error("При сохранении игрока $id возникла ошибка", e)
        }
    }

    suspend fun loadRecord(playerId: PlayerId): PersistentPlayerRecord? {
        return withContext(Dispatchers.IO) {
            playerMutex(playerId).withLock {
                val file = directory.resolve("$playerId.json")
                if (file.exists()) {
                    val record = JSON.decodeFromString<PersistentPlayerRecord>(file.readText())
                    val legacyCharacters = record.data.usedCharactersLegacy.mapTo(mutableSetOf()) {
                        OwnedCharacterId.player(playerId, it.asServer())
                    }
                    record.copy(
                        data = record.data.copy(
                            usedCharacters = record.data.usedCharacters + legacyCharacters
                        )
                    )
                } else {
                    null
                }
            }
        }
    }

    suspend fun loadPersistentEntity(
        playerId: PlayerId,
        context: TransactionContext,
        diagnosticContext: LogDiagnosticContext = LogDiagnosticContext()
    ): EntityId = with(diagnosticContext) {
        playerMutex(playerId).withLock {
            val playerPersistentId = playerId.asPersistentId()
            EntityLoadOperation(playerPersistentId, database, context)
                .execute()
                .getEntityOrThrow()
        }
    }

    context(diagnostic: LogDiagnosticContext)
    suspend fun loadPersistentEntity(
        playerId: PlayerId,
        context: TransactionContext,
    ): EntityId = loadPersistentEntity(playerId, context, diagnostic)

    suspend fun loadPersistentCharacter(
        transactionContext: TransactionContext,
        settings: ComponentReviveSettings,
        playerId: PlayerId,
        characterId: OwnedCharacterId,
    ): PlayCharacterData? {
        return characterMutex(characterId).withLock {
            val entity = database.loadEntity(characterId.databasePersistentId()) ?: return null
            val data = entity.data as? EntityPersistenceData.Character ?: return null

            val referencedItemIds = data.equipment.values + data.items
            val referencedItemIdsSet = referencedItemIds.toSet()
            if (referencedItemIds.size != referencedItemIdsSet.size) {
                error("Полный набор предметов персонажа $characterId содержит дубликаты")
            }

            val itemLoadContext = ItemLoadContext.PreparingCharacter(playerId, characterId)
            val equipment = transactionContext.withChild {
                data.equipment.mapValues { (_, persistentId) ->
                    itemLoader.loadWorldItem(persistentId, itemLoadContext, it)
                }
            }
            val items = transactionContext.withChild {
                data.items.mapTo(mutableSetOf()) { persistentId ->
                    itemLoader.loadWorldItem(persistentId, itemLoadContext, it)
                }
            }

            val components = entity.materialize(settings, EntityResolver.EMPTY)

            PlayCharacterData(
                characterId,
                components,
                data.inventory,
                items,
                data.look,
                equipment
            )
        }
    }

    fun saveCharacter(
        id: OwnedCharacterId,
        character: PersistentCharacterSnapshot
    ) {
        launchSave(characterMutex(id)) {
            saveCharacterSuspend(id, character)
        }
    }

    private suspend fun saveCharacterSuspend(
        id: OwnedCharacterId,
        character: PersistentCharacterSnapshot
    ) {
        suspendTransaction {
            database.saveEntity(
                EntityDatabaseKind.CHARACTER,
                EntityPersistentRecord(
                    id.databasePersistentId(),
                    EntityPersistenceData.Character(
                        character.look,
                        character.inventory,
                        character.equipment,
                        character.items
                    ),
                    character.components.map { it.serializeToRecord() },
                    emptyList()
                )
            )
        }
    }

    private fun launchSave(
        mutex: Mutex,
        block: suspend () -> Unit,
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                yield()
                block()
            }
        }
    }

    private fun playerMutex(playerId: PlayerId) = playerMutexes.computeIfAbsent(playerId) { Mutex() }
    private fun characterMutex(id: OwnedCharacterId) = characterMutexes.computeIfAbsent(id) { Mutex() }
}
