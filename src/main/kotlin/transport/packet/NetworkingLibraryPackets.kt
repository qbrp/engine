package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.ScriptValue
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import java.util.Queue

@Serializable
data class ServerboundChannelDataPacket(val entity: PersistentId, val delta: List<ScriptValue>) : Packet

val SERVERBOUND_CHANNEL_DATA_ENDPOINT = Endpoint<ServerboundChannelDataPacket>()