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
import org.lain.engine.player.character.CharacterId
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
    val skinEyeY: Float = 2f,
    val appliedCharacter: CharacterId? = null,
    val usedCharacters: Set<CharacterId>
)

data class PersistentCharacterSnapshot(
    val id: CharacterId,
    val look: String,
    val items: SerializedInventory,
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

data class CharacterData(
    val id: CharacterId,
    val components: MaterializedComponents,
    val inventory: SerializedInventory,
    val items: Set<EntityId>,
    val look: String,
    val equipment: Map<EquipmentSlotId, EntityId> = emptyMap(),
)

fun CharacterDatabasePersistentId(playerId: PlayerId, characterId: CharacterId): PersistentId {
    return CustomPersistentId("$playerId-$characterId")
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
            skinEyeY = require<EnginePlayerModel>().skinEyeY,
            appliedCharacter = get<AppliedCharacter>()
                ?.character
                ?.profile
                ?.id,
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
    private val characterMutexes = ConcurrentHashMap<PersistentId, Mutex>()

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)


    suspend fun close() {
        withContext(NonCancellable) {
            supervisor.complete()
            supervisor.join()
        }
    }

    fun save(snapshot: PersistentPlayerSnapshot) {
        scope.launch {
            playerMutex(snapshot.id)
                .withLock { snapshot.save(database, directory) }
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

    suspend fun loadRecord(playerId: PlayerId): PersistentPlayerRecord? = withContext(Dispatchers.IO) {
        playerMutex(playerId).withLock {
            val file = directory.resolve("$playerId.json")
            if (file.exists()) {
                JSON.decodeFromString<PersistentPlayerRecord>(file.readText())
            } else {
                null
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
        characterId: CharacterId,
    ): CharacterData? = characterMutex(playerId, characterId).withLock {
        val entity = database.loadEntity(CharacterDatabasePersistentId(playerId, characterId)) ?: return null
        val data = entity.data as? EntityPersistenceData.Character ?: run {
            LOGGER.warn(
                "У персонажа $characterId игрока $playerId отсутствуют сохранённые данные, " +
                        "персонаж будет создан заново"
            )
            return null
        }

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

        return CharacterData(
            characterId,
            components,
            data.inventory,
            items,
            data.look,
            equipment
        )
    }

    fun saveCharacter(
        player: EnginePlayer,
        character: PersistentCharacterSnapshot
    ) {
        scope.launch {
            characterMutex(player.id, character.id).withLock {
                saveCharacterSuspend(player.id, character)
            }
        }
    }

    private suspend fun saveCharacterSuspend(
        player: PlayerId,
        character: PersistentCharacterSnapshot
    ) {
        suspendTransaction {
            database.saveEntity(
                EntityDatabaseKind.CHARACTER,
                EntityPersistentRecord(
                    CharacterDatabasePersistentId(player, character.id),
                    EntityPersistenceData.Character(
                        character.look,
                        character.items,
                        character.equipment
                    ),
                    character.components.map { it.serializeToRecord() },
                    emptyList()
                )
            )
        }
    }

    private fun playerMutex(playerId: PlayerId) = playerMutexes.computeIfAbsent(playerId) { Mutex() }
    private fun characterMutex(playerId: PlayerId, characterId: CharacterId) =
        characterMutexes.computeIfAbsent(CharacterDatabasePersistentId(playerId, characterId)) { Mutex() }
}
