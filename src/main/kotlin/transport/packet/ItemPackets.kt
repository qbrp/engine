package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Deprecated("")
interface ItemComponent : Component

@Serializable
data class WriteableUpdatePacket(val item: PersistentId, val contents: List<String>) : Packet

val SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT = Endpoint<WriteableUpdatePacket>()