package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.data.CustomPersistentId
import org.lain.engine.data.EntityDatabaseKind
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.EntityPersistentRecord
import org.lain.engine.data.EntityTable
import org.lain.engine.data.connectDatabase
import org.lain.engine.data.disconnectDatabase
import org.lain.engine.data.loadEntity
import org.lain.engine.data.saveEntity
import java.nio.file.Path

class DatabasePersistenceTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun characterDataRoundTrips() = runBlocking {
        val database = connectDatabase(tempDir.toString())
        val persistentId = CustomPersistentId("player-character")
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
        val persistentId = CustomPersistentId("legacy-player-character")
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
    }

    private object LegacyCharactersTable : Table("Characters") {
        val uuid = reference("uuid", EntityTable.uuid)
        val look = text("look")
        val items = text("items")

        override val primaryKey = PrimaryKey(uuid)
    }
}
