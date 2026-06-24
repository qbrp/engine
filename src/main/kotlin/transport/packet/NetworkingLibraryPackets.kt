package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptValue
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import java.util.Queue

@Serializable
data class EntityComponentRpcPacket(val entity: PersistentId, val delta: List<ScriptValue>) : Packet

val SERVERBOUND_ENTITY_COMPONENT_RPC_ENDPOINT = Endpoint<EntityComponentRpcPacket>()