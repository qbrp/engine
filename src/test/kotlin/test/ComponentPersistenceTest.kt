package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.Component
import org.lain.engine.data.ComponentPayload
import org.lain.engine.data.ComponentPersistentRecord
import org.lain.engine.data.ComponentSnapshot
import org.lain.engine.data.ComponentsTable
import org.lain.engine.data.CustomPersistentId
import org.lain.engine.data.EntityDatabaseKind
import org.lain.engine.data.EntityPersistentRecord
import org.lain.engine.data.LegacyPersistentComponentDto
import org.lain.engine.data.SavingComponentSnapshot
import org.lain.engine.data.WorldPersistent
import org.lain.engine.data.asRawEngineId
import org.lain.engine.data.decode
import org.lain.engine.data.decodeComponentPayload
import org.lain.engine.data.decodeWorldPersistent
import org.lain.engine.data.disconnectDatabase
import org.lain.engine.data.encode
import org.lain.engine.data.loadEntity
import org.lain.engine.data.migrate
import org.lain.engine.data.saveEntity
import org.lain.engine.data.serializeToComponentPayload
import org.lain.engine.data.snapshot
import org.lain.engine.data.toJsonComponentPayload
import org.lain.engine.data.ComponentBatchDto
import org.lain.engine.data.upsertComponentsBatchTransaction
import org.lain.engine.item.Barrel
import org.lain.engine.item.Count
import org.lain.engine.item.GunFireState
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemName
import org.lain.engine.item.ItemSounds
import org.lain.engine.script.EngineId
import org.lain.engine.script.SInt
import org.lain.engine.script.SList
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.toScriptComponentId
import java.nio.file.Path

@Serializable
private data class LegacyWorldPersistentFixture(
    val components: List<LegacyPersistentComponentDto.Script>
)

class ComponentPersistenceTest : EngineTest() {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun mutableComponentSnapshotsDoNotTrackLiveMutations() {
        val barrel = Barrel(5, 10, null)
        val fireState = GunFireState(triggerSoundPlayed = false, fired = true)

        val barrelSnapshot = (barrel.snapshot() as ComponentSnapshot.Kotlin<*>).component as Barrel
        val fireStateSnapshot =
            (fireState.snapshot() as ComponentSnapshot.Kotlin<*>).component as GunFireState

        barrel.bullets = 2
        fireState.triggerSoundPlayed = true
        fireState.fired = false

        assertEquals(5, barrelSnapshot.bullets)
        assertFalse(fireStateSnapshot.triggerSoundPlayed)
        assertTrue(fireStateSnapshot.fired)
    }

    @Test
    fun kotlinComponentRoundTripsThroughDatabase() = runBlocking {
        val database = org.lain.engine.data.connectDatabase(tempDir.toString())
        val persistentId = CustomPersistentId("component-payload")
        val record = SavingComponentSnapshot(
            ComponentSnapshot.Kotlin(Count(3, 12)),
            componentTypeOf(Count::class),
        ).serializeToRecord()

        database.saveEntity(
            EntityDatabaseKind.PLAYER,
            EntityPersistentRecord(persistentId, null, listOf(record), emptyList()),
        )

        val loadedRecord = database.loadEntity(persistentId)!!.components.single()
        assertInstanceOf(ComponentPayload.Kotlin::class.java, loadedRecord.payload)
        assertEquals(Count(3, 12), (loadedRecord.decode() as ComponentSnapshot.Kotlin<*>).component)
    }

    @Test
    fun loadingLeavesStoredIdsUntouchedAndSavingRewritesRecoveredComponents() = runBlocking {
        val database = org.lain.engine.data.connectDatabase(tempDir.toString())
        try {
            val persistentId = CustomPersistentId("legacy-component-ids")
            val components: Map<String, Component> = linkedMapOf(
                "count" to Count(3, 12),
                "itemname" to ItemName("Saved name"),
                "itemassets" to ItemAssets(mapOf("default" to EngineId("test/model"))),
                "itemsounds" to ItemSounds(emptyMap()),
            )
            val records = components.map { (legacyId, component) ->
                SavingComponentSnapshot(component.snapshot(), componentTypeOf(component))
                    .serializeToRecord()
                    .copy(id = legacyId.asRawEngineId(), version = 7, error = "previous load error")
            }
            database.saveEntity(
                EntityDatabaseKind.PLAYER,
                EntityPersistentRecord(persistentId, null, records, emptyList()),
            )

            val loaded = requireNotNull(database.loadEntity(persistentId))
            val expectedIds = components.values.mapTo(mutableSetOf()) { componentTypeOf(it).id }
            assertEquals(components.keys, loaded.components.mapTo(mutableSetOf()) { it.id.id })
            loaded.components.forEach { record ->
                val component = (record.decode() as ComponentSnapshot.Kotlin<*>).component
                assertEquals(components[record.id.id], component)
                assertEquals(7, record.version)
                assertEquals("previous load error", record.error)
            }

            val storedIds = transaction(database) {
                ComponentsTable.selectAll()
                    .where { ComponentsTable.entity eq persistentId.toString() }
                    .mapTo(mutableSetOf()) { it[ComponentsTable.id] }
            }
            assertEquals(components.keys, storedIds)
            val resolvedRecords = loaded.components.map { record ->
                val snapshot = record.decode() as ComponentSnapshot.Kotlin<*>
                SavingComponentSnapshot(snapshot, componentTypeOf(snapshot.component)).serializeToRecord()
            }
            database.saveEntity(EntityDatabaseKind.PLAYER, loaded.copy(components = resolvedRecords))
            val saved = requireNotNull(database.loadEntity(persistentId))
            assertEquals(expectedIds, saved.components.mapTo(mutableSetOf()) { it.id.id })
            assertEquals(components.size, saved.components.size)
            assertEquals(components.size, loaded.components.size)
        } finally {
            disconnectDatabase(database)
        }
    }

    @Test
    fun unresolvedLegacyRecordsKeepTheirOriginalIdAndArePreservedBySaving() = runBlocking {
        val database = org.lain.engine.data.connectDatabase(tempDir.toString())
        try {
            val persistentId = CustomPersistentId("unresolved-legacy-component")
            val count = SavingComponentSnapshot(Count(3, 12).snapshot(), componentTypeOf(Count::class))
                .serializeToRecord()
            val unresolved = count.copy(id = "unregistered_component".asRawEngineId(), error = "missing serializer")
            database.saveEntity(EntityDatabaseKind.PLAYER,
                EntityPersistentRecord(persistentId, null, listOf(unresolved), emptyList()))

            val loaded = requireNotNull(database.loadEntity(persistentId)).components.single()
            database.upsertComponentsBatchTransaction(listOf(ComponentBatchDto(persistentId, loaded)))
            database.saveEntity(EntityDatabaseKind.PLAYER,
                EntityPersistentRecord(persistentId, null, listOf(count), emptyList()))
            val saved = requireNotNull(database.loadEntity(persistentId)).components.associateBy { it.id.id }
            assertEquals(setOf(count.id.id, unresolved.id.id), saved.keys)
            assertEquals(unresolved.error, saved.getValue(unresolved.id.id).error)
        } finally {
            disconnectDatabase(database)
        }
    }

    @Test
    fun identifierMigrationIsExactAndIdempotent() {
        assertEquals("core/item/assets", "itemassets".asRawEngineId().migrate().id)
        assertEquals("core/item/name", "itemname".asRawEngineId().migrate().id)
        assertEquals("core/item/sounds", "itemsounds".asRawEngineId().migrate().id)
        assertEquals("core/item/equippable", "core/equippable".asRawEngineId().migrate().id)
        for (id in listOf("core/item/assets", "custom/itemassets", "core/player/attributes", "unknown_component")) {
            val raw = id.asRawEngineId()
            assertEquals(raw, raw.migrate())
            assertEquals(raw.migrate(), raw.migrate().migrate())
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun legacyKotlinPayloadAcceptsMigratedRecordIdAndRejectsAnotherComponent() {
        val component = Count(3, 12)
        val payload = component.snapshot().serializeToComponentPayload() as ComponentPayload.Kotlin
        val legacy = LegacyPersistentComponentDto.Payload("count", payload.cbor)
        val encoded = Cbor.encodeToByteArray<LegacyPersistentComponentDto>(legacy)

        val decoded = decodeComponentPayload(encoded, "core/item/count".asRawEngineId())
        assertEquals(component, (decoded.decode("count".asRawEngineId()) as ComponentSnapshot.Kotlin<*>).component)
        assertThrows(IllegalArgumentException::class.java) {
            decodeComponentPayload(encoded, "core/item/name".asRawEngineId())
        }
    }

    @Test
    fun scriptComponentRoundTripsThroughCborPayload() {
        val id = EngineId("test/component").toScriptComponentId()
        val value = STable(mapOf(SString("values") to SList(listOf(SInt(1), SInt(2)))))
        val snapshot = ComponentSnapshot.Script(id, value)
        val payload = snapshot.serializeToComponentPayload()
        val record = ComponentPersistentRecord(id.toString().asRawEngineId(), 2, payload, null)

        assertInstanceOf(ComponentPayload.Script.Cbor::class.java, payload)
        assertEquals(snapshot, record.decode())
    }

    @Test
    fun scriptComponentRoundTripsThroughWorldJson() {
        val id = EngineId("test/component").toScriptComponentId()
        val value = STable(mapOf(STable(mapOf(SString("key") to SInt(1))) to SString("value")))
        val snapshot = ComponentSnapshot.Script(id, value)
        val persistent = WorldPersistent(
            listOf(
                ComponentPersistentRecord(
                    id.toString().asRawEngineId(),
                    3,
                    snapshot.toJsonComponentPayload(),
                    null,
                )
            )
        )

        val decoded = decodeWorldPersistent(persistent.encode()).components.single()
        assertInstanceOf(ComponentPayload.Script.Json::class.java, decoded.payload)
        assertEquals(snapshot, decoded.decode())
        assertEquals(3, decoded.version)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun legacyCborPayloadIsConverted() {
        val id = EngineId("test/component").toScriptComponentId()
        val value = SList(listOf(SInt(1), SString("legacy")))
        val legacy = LegacyPersistentComponentDto.Script(id, value)
        val encoded = Cbor.encodeToByteArray<LegacyPersistentComponentDto>(legacy)

        val payload = decodeComponentPayload(encoded, id.toString().asRawEngineId())
        val record = ComponentPersistentRecord(id.toString().asRawEngineId(), 1, payload, null)

        assertInstanceOf(ComponentPayload.Script.Cbor::class.java, payload)
        assertEquals(ComponentSnapshot.Script(id, value), record.decode())
    }

    @Test
    fun legacyWorldJsonIsConverted() {
        val id = EngineId("test/component").toScriptComponentId()
        val value = SList(listOf(SInt(1), SString("legacy")))
        val encoded = Json.encodeToString(
            LegacyWorldPersistentFixture(
                listOf(LegacyPersistentComponentDto.Script(id, value))
            )
        )

        val record = decodeWorldPersistent(encoded).components.single()

        assertInstanceOf(ComponentPayload.Script.Json::class.java, record.payload)
        assertEquals(ComponentSnapshot.Script(id, value), record.decode())
        assertEquals(0, record.version)
    }
}
