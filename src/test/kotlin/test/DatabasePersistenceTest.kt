package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.data.CustomPersistentId
import org.lain.engine.data.CharactersTable
import org.lain.engine.data.EntityDatabaseKind
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.EntityPersistentRecord
import org.lain.engine.data.EntityTable
import org.lain.engine.data.connectDatabase
import org.lain.engine.data.disconnectDatabase
import org.lain.engine.data.loadEntity
import org.lain.engine.data.saveEntity
import org.lain.engine.player.toEquipmentSlotId
import org.lain.engine.script.EngineId
import java.nio.file.Path

class DatabasePersistenceTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun characterDataRoundTrips() = runBlocking {
        val database = connectDatabase(tempDir.toString())
        val persistentId = CustomPersistentId("player-variant")
        val expected = EntityPersistenceData.Character(
            look = "test-look",
            inventory = "serialized-inventory",
        )

        database.saveEntity(
            EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(
                persistentId,
                expected,
                emptyList(),
                emptyList(),
            )
        )

        assertEquals(expected, database.loadEntity(persistentId)?.data)
    }

    @Test
    fun existingCharacterTableGetsEquipmentColumn() = runBlocking {
        val persistentId = CustomPersistentId("legacy-player-variant")
        val legacyDatabase = Database.connect("jdbc:sqlite:${tempDir.resolve("engine.db")}")
        transaction(legacyDatabase) {
            SchemaUtils.create(EntityTable, LegacyCharactersTable)
            EntityTable.insert {
                it[EntityTable.uuid] = persistentId.toString()
                it[EntityTable.kind] = EntityDatabaseKind.CHARACTER
            }
            LegacyCharactersTable.insert {
                it[LegacyCharactersTable.uuid] = persistentId.toString()
                it[LegacyCharactersTable.look] = "legacy-look"
                it[LegacyCharactersTable.items] = "legacy-inventory"
            }
        }
        disconnectDatabase(legacyDatabase)

        val migratedDatabase = connectDatabase(tempDir.toString())

        assertEquals(
            EntityPersistenceData.Character("legacy-look", "legacy-inventory"),
            migratedDatabase.loadEntity(persistentId)?.data,
        )
        disconnectDatabase(migratedDatabase)
    }

    @Test
    fun legacyItemsColumnIsMovedToInventoryAndSurvivesReopening() = runBlocking {
        val persistentId = CustomPersistentId("legacy-items-inventory")
        val legacyDatabase = Database.connect("jdbc:sqlite:${tempDir.resolve("engine.db")}")
        transaction(legacyDatabase) {
            SchemaUtils.create(EntityTable, LegacyItemsTable)
            EntityTable.insert {
                it[EntityTable.uuid] = persistentId.toString()
                it[EntityTable.kind] = EntityDatabaseKind.CHARACTER
            }
            LegacyItemsTable.insert {
                it[LegacyItemsTable.uuid] = persistentId.toString()
                it[LegacyItemsTable.look] = "legacy-look"
                it[LegacyItemsTable.items] = LEGACY_INVENTORY
            }
        }
        disconnectDatabase(legacyDatabase)

        repeat(2) {
            val database = connectDatabase(tempDir.toString())
            try {
                assertEquals(
                    EntityPersistenceData.Character("legacy-look", LEGACY_INVENTORY),
                    database.loadEntity(persistentId)?.data,
                )
                assertCharacterColumns(database, persistentId, LEGACY_INVENTORY, "[]")
            } finally {
                disconnectDatabase(database)
            }
        }
    }

    @Test
    fun partiallyMigratedSnbtIsRecoveredWithoutChangingEquipment() = runBlocking {
        val persistentId = CustomPersistentId("partial-inventory-migration")
        val equipment = mapOf(EngineId("test/head").toEquipmentSlotId() to CustomPersistentId("equipped-item"))
        val initialDatabase = connectDatabase(tempDir.toString())
        initialDatabase.saveEntity(
            EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(persistentId,
                EntityPersistenceData.Character("base", "{}", equipment), emptyList(), emptyList()),
        )
        transaction(initialDatabase) {
            CharactersTable.update({ CharactersTable.uuid eq persistentId.toString() }) {
                it[CharactersTable.items] = LEGACY_INVENTORY
            }
        }
        disconnectDatabase(initialDatabase)

        val database = connectDatabase(tempDir.toString())
        try {
            assertEquals(
                EntityPersistenceData.Character("base", LEGACY_INVENTORY, equipment),
                database.loadEntity(persistentId)?.data,
            )
            assertCharacterColumns(database, persistentId, LEGACY_INVENTORY, "[]")
        } finally {
            disconnectDatabase(database)
        }
    }

    @Test
    fun oldEmptyObjectItemsAreRepairedAndModernItemReferencesArePreserved() = runBlocking {
        val legacyId = CustomPersistentId("legacy-empty-items")
        val modernId = CustomPersistentId("modern-items")
        val modern = EntityPersistenceData.Character("modern", LEGACY_INVENTORY,
            items = listOf(CustomPersistentId("stored-item")))
        val initialDatabase = connectDatabase(tempDir.toString())
        initialDatabase.saveEntity(EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(legacyId, EntityPersistenceData.Character("base", LEGACY_INVENTORY),
                emptyList(), emptyList()))
        initialDatabase.saveEntity(EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(modernId, modern, emptyList(), emptyList()))
        transaction(initialDatabase) {
            CharactersTable.update({ CharactersTable.uuid eq legacyId.toString() }) {
                it[CharactersTable.items] = "{}"
            }
        }
        disconnectDatabase(initialDatabase)

        val database = connectDatabase(tempDir.toString())
        try {
            assertEquals(EntityPersistenceData.Character("base", LEGACY_INVENTORY), database.loadEntity(legacyId)?.data)
            assertCharacterColumns(database, legacyId, LEGACY_INVENTORY, "[]")
            assertEquals(modern, database.loadEntity(modernId)?.data)
        } finally {
            disconnectDatabase(database)
        }
    }

    @Test
    fun conflictingInventoriesAbortMigrationWithoutOverwritingEitherValue() = runBlocking {
        val persistentId = CustomPersistentId("conflicting-inventories")
        val canonicalInventory = "{Inventory:[]}"
        val initialDatabase = connectDatabase(tempDir.toString())
        initialDatabase.saveEntity(EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(persistentId, EntityPersistenceData.Character("base", canonicalInventory),
                emptyList(), emptyList()))
        transaction(initialDatabase) {
            CharactersTable.update({ CharactersTable.uuid eq persistentId.toString() }) {
                it[CharactersTable.items] = LEGACY_INVENTORY
            }
        }
        disconnectDatabase(initialDatabase)

        assertThrows(IllegalStateException::class.java) { connectDatabase(tempDir.toString()) }
        val rawDatabase = Database.connect("jdbc:sqlite:${tempDir.resolve("engine.db")}")
        try {
            assertCharacterColumns(rawDatabase, persistentId, canonicalInventory, LEGACY_INVENTORY)
        } finally {
            disconnectDatabase(rawDatabase)
        }
    }

    @Test
    fun invalidItemsJsonIsStillReportedAndRemainsIntact() = runBlocking {
        val persistentId = CustomPersistentId("invalid-items-json")
        val initialDatabase = connectDatabase(tempDir.toString())
        initialDatabase.saveEntity(EntityDatabaseKind.CHARACTER,
            EntityPersistentRecord(persistentId, EntityPersistenceData.Character("base", LEGACY_INVENTORY),
                emptyList(), emptyList()))
        transaction(initialDatabase) {
            CharactersTable.update({ CharactersTable.uuid eq persistentId.toString() }) {
                it[CharactersTable.items] = "{invalid-json"
            }
        }
        disconnectDatabase(initialDatabase)

        val database = connectDatabase(tempDir.toString())
        try {
            assertThrows(SerializationException::class.java) {
                runBlocking { database.loadEntity(persistentId) }
            }
            assertCharacterColumns(database, persistentId, LEGACY_INVENTORY, "{invalid-json")
        } finally {
            disconnectDatabase(database)
        }
    }

    private fun assertCharacterColumns(database: Database, id: CustomPersistentId, inventory: String, items: String) {
        transaction(database) {
            val row = CharactersTable.selectAll().where { CharactersTable.uuid eq id.toString() }.single()
            assertEquals(inventory, row[CharactersTable.inventory])
            assertEquals(items, row[CharactersTable.items])
        }
    }

    private object LegacyCharactersTable : Table("Characters") {
        val uuid = reference("uuid", EntityTable.uuid)
        val look = text("look")
        val items = text("inventory")

        override val primaryKey = PrimaryKey(uuid)
    }

    private object LegacyItemsTable : Table("Characters") {
        val uuid = reference("uuid", EntityTable.uuid)
        val look = text("look")
        val items = text("items")

        override val primaryKey = PrimaryKey(uuid)
    }

    companion object {
        private const val LEGACY_INVENTORY = """{Inventory:[{Slot:2b,id:"minecraft:stone",count:4}]}"""
    }
}
