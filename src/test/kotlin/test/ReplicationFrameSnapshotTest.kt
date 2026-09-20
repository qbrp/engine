package org.lain.engine.test

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.engine.server.EntityNetworkSnapshot
import org.lain.engine.server.ReplicationFrameSnapshot
import org.lain.engine.server.ReplicationTarget
import org.lain.engine.data.COMPONENT_CBOR
import org.lain.engine.data.persistentId
import org.lain.engine.transport.packet.ReplicationResyncRequestPacket

class ReplicationFrameSnapshotTest : EngineTest() {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun processedInputTickIsSerializedWithReplicationFrame() {
        val frame = ReplicationFrameSnapshot(
            world = null,
            entities = mapOf(
                persistentId("test-entity") to EntityNetworkSnapshot.Full(
                    revision = 3,
                    components = emptyList(),
                ),
            ),
            processedInputTick = 42,
        )

        val bytes = COMPONENT_CBOR.encodeToByteArray(
            ReplicationFrameSnapshot.serializer(),
            frame,
        )

        assertEquals(
            frame,
            COMPONENT_CBOR.decodeFromByteArray(
                ReplicationFrameSnapshot.serializer(),
                bytes,
            ),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun replicationResyncTargetsAreSerialized() {
        val packets = listOf(
            ReplicationResyncRequestPacket(ReplicationTarget.World),
            ReplicationResyncRequestPacket(
                ReplicationTarget.Entity(persistentId("test-entity")),
            ),
        )

        packets.forEach { packet ->
            val bytes = ProtoBuf.encodeToByteArray(
                ReplicationResyncRequestPacket.serializer(),
                packet,
            )

            assertEquals(
                packet,
                ProtoBuf.decodeFromByteArray(
                    ReplicationResyncRequestPacket.serializer(),
                    bytes,
                ),
            )
        }
    }
}
