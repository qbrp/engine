package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.data.AcquireResult
import org.lain.engine.data.CharactersTable
import org.lain.engine.data.CustomPersistentId
import org.lain.engine.data.EntityDatabaseKind
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.EntityPersistentRecord
import org.lain.engine.data.PersistentId
import org.lain.engine.data.TransactionContext
import org.lain.engine.data.Uuid
import org.lain.engine.data.asRawEngineId
import org.lain.engine.data.createTransactionContext
import org.lain.engine.data.databasePersistentId
import org.lain.engine.data.disconnectDatabase
import org.lain.engine.data.loadEntity
import org.lain.engine.data.saveEntity
import org.lain.engine.item.Item
import org.lain.engine.player.PlayerId
import org.lain.engine.player.character.OwnedCharacterId
import org.lain.engine.player.character.ServerCharacterId
import org.lain.engine.player.toEquipmentSlotId
import org.lain.engine.script.BuiltinNamespaces
import org.lain.engine.script.EngineId
import org.lain.engine.server.EngineServer
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.world.World
import java.nio.file.Path
import java.util.UUID

class CharacterPersistenceTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    private lateinit var server: EngineServer
    private lateinit var world: World
    private val transactions = mutableListOf<TransactionContext>()
    private var previousTransport: ServerTransportContext? = null
    private val playerId = PlayerId(UUID.randomUUID())
    private val id = OwnedCharacterId.player(playerId, ServerCharacterId("saved"))
    private val headSlot = EngineId("test/head").toEquipmentSlotId()
    private val bodySlot = EngineId("test/body").toEquipmentSlotId()

    @BeforeEach
    fun setup() {
        previousTransport = Injector.resolveOrNull(ServerTransportContext::class)
        server = setupTestEngineServer(tempDir)
        world = server.simulation.defaultWorld
    }

    @AfterEach
    fun teardown() = runBlocking {
        if (!::server.isInitialized) return@runBlocking
        try {
            transactions.forEach { it.rollbackIfActive(IllegalStateException("test cleanup")) }
            server.update()
            server.characterManager.close()
            server.playerPersistence.close()
            server.chunkPersistence.close()
        } finally {
            server.chat.close()
            disconnectDatabase(server.database)
            previousTransport?.let { Injector.register<ServerTransportContext>(it) }
                ?: Injector.unregister<ServerTransportContext>()
        }
    }

    @Test
    fun missingCharacterHasNoPersistentState() = runBlocking {
        val context = context()
        val loaded = server.playerPersistence.loadPersistentCharacter(
            context, world.componentReviveSettings, playerId, id,
        )
        assertNull(loaded)
        context.commit()
    }

    @Test
    fun equalCharacterIdsKeepPlayerAndServerSavesSeparate() = runBlocking {
        val otherPlayer = PlayerId(UUID.randomUUID())
        val owners = listOf(id, OwnedCharacterId.player(otherPlayer, id.character), OwnedCharacterId.server(id.character))
        for ((index, owner) in owners.withIndex()) {
            save(owner, EntityPersistenceData.Character("look-" + index, "inventory-" + index))
        }

        for ((index, owner) in owners.withIndex()) {
            val context = context()
            val loaded = requireNotNull(server.playerPersistence.loadPersistentCharacter(
                context, world.componentReviveSettings, playerId, owner,
            ))
            assertEquals(owner, loaded.id)
            assertEquals("look-" + index, loaded.look)
            assertEquals("inventory-" + index, loaded.inventory)
            context.commit()
        }
    }

    @Test
    fun duplicateEquipmentReferenceIsRejectedBeforeLoadingItems() = runBlocking {
        val duplicated = Uuid.next()
        save(id, EntityPersistenceData.Character(
            "base", "inventory", linkedMapOf(headSlot to duplicated, bodySlot to duplicated),
        ))
        val context = context()

        val failure = runCatching {
            server.playerPersistence.loadPersistentCharacter(context, world.componentReviveSettings, playerId, id)
        }.exceptionOrNull()

        assertInstanceOf(IllegalStateException::class.java, failure)
        assertNull(world.persistentIdToEntity[duplicated])
        assertLoadCanBeRetried(duplicated)
    }

    @Test
    fun corruptedEquipmentJsonPropagatesAsAnError() = runBlocking {
        save(id, EntityPersistenceData.Character("base", "inventory"))
        val persistentId = id.databasePersistentId().toString()
        transaction(server.database) {
            CharactersTable.update({ CharactersTable.uuid eq persistentId }) {
                it[CharactersTable.equipment] = "{invalid-json"
            }
        }
        val context = context()

        val failure = runCatching {
            server.playerPersistence.loadPersistentCharacter(context, world.componentReviveSettings, playerId, id)
        }.exceptionOrNull()

        assertInstanceOf(SerializationException::class.java, failure)
        context.rollbackIfActive(requireNotNull(failure))
        assertNull(world.persistentIdToEntity[id.databasePersistentId()])
    }

    @Test
    fun failedEquipmentPreparationReleasesPreviouslyPreparedItem() = runBlocking {
        val itemId = Uuid.next()
        server.database.saveEntity(
            EntityDatabaseKind.ITEM,
            EntityPersistentRecord(
                itemId, EntityPersistenceData.Item("test/item".asRawEngineId(), 1, 1), emptyList(), emptyList(),
            ),
        )
        save(id, EntityPersistenceData.Character(
            "base", "inventory", linkedMapOf(headSlot to itemId, bodySlot to CustomPersistentId("invalid-item-id")),
        ))
        val context = context()

        val failure = runCatching {
            server.playerPersistence.loadPersistentCharacter(context, world.componentReviveSettings, playerId, id)
        }.exceptionOrNull()

        assertInstanceOf(IllegalArgumentException::class.java, failure)
        context.rollbackIfActive(requireNotNull(failure))
        server.update()
        assertNull(world.persistentIdToEntity[itemId])
        assertLoadCanBeRetried(itemId)
    }

    @Test
    fun missingEquipmentItemGetsInvalidPlaceholder() = runBlocking {
        save(id, EntityPersistenceData.Character("base", "inventory", mapOf(headSlot to Uuid.next())))
        val context = context()

        val loaded = requireNotNull(server.playerPersistence.loadPersistentCharacter(
            context, world.componentReviveSettings, playerId, id,
        ))
        context.commit()

        val placeholder = requireNotNull(loaded.equipment[headSlot])
        assertEquals(BuiltinNamespaces.Items.INVALID_ID, with(world) { placeholder.requireComponent<Item>().id })
        assertEquals("inventory", loaded.inventory)
    }

    @Test
    fun rollingBackMissingItemFallbackDestroysItsPlaceholder() = runBlocking {
        save(id, EntityPersistenceData.Character("base", "inventory", mapOf(headSlot to Uuid.next())))
        val context = context()
        val loaded = requireNotNull(server.playerPersistence.loadPersistentCharacter(
            context, world.componentReviveSettings, playerId, id,
        ))
        val placeholder = requireNotNull(loaded.equipment[headSlot])

        context.rollback(IllegalStateException("character loading cancelled"))
        server.update()

        assertFalse(world.componentManager.exists(placeholder), "Rollback must destroy the invalid-item placeholder")
    }

    @Test
    fun characterInventoryItemReferencesSurviveDatabaseRoundTrip() = runBlocking {
        val item = Uuid.next()
        val expected = EntityPersistenceData.Character("base", "inventory", items = listOf(item))
        save(id, expected)

        val loaded = assertInstanceOf(
            EntityPersistenceData.Character::class.java,
            server.database.loadEntity(id.databasePersistentId())?.data,
        )

        assertEquals(expected.items, loaded.items)
    }

    private fun context(): TransactionContext =
        server.createTransactionContext(world).also { transactions += it }

    private suspend fun save(id: OwnedCharacterId, data: EntityPersistenceData.Character) {
        server.database.saveEntity(
            EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(id.databasePersistentId(), data, emptyList(), emptyList()),
        )
    }

    private fun assertLoadCanBeRetried(id: PersistentId) {
        val acquired = assertInstanceOf(AcquireResult.Acquired::class.java, server.entityCoordinator.acquire(world, id))
        server.entityCoordinator.abortLoadReservation(acquired.reservation, IllegalStateException("test cleanup"))
    }
}
