package org.lain.engine.test

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.data.ComponentPayload
import org.lain.engine.data.ComponentPersistentRecord
import org.lain.engine.data.ComponentSnapshot
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
import org.lain.engine.data.encode
import org.lain.engine.data.loadEntity
import org.lain.engine.data.saveEntity
import org.lain.engine.data.serializeToComponentPayload
import org.lain.engine.data.toJsonComponentPayload
import org.lain.engine.item.Count
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
