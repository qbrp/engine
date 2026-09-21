package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.engine.data.CustomPersistentId
import org.lain.engine.data.EntityDatabaseKind
import org.lain.engine.data.EntityPersistenceData
import org.lain.engine.data.EntityPersistentRecord
import org.lain.engine.data.connectDatabase
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
            items = "serialized-inventory",
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
}
