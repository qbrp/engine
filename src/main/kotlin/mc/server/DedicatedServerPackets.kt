package org.lain.engine.mc.server

import kotlinx.serialization.Serializable
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Serializable
data class ClientHelloPacket(
    val mods: List<String>,
    val version: String
) : Packet {
    override val requireAuthorized: Boolean = false
}

val SERVERBOUND_CLIENT_HELLO_ENDPOINT = Endpoint<ClientHelloPacket>()