package org.lain.engine.mc.server

import kotlinx.serialization.Serializable
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Serializable
data class AuthPacket(
    val mods: List<String>,
    val version: String
) : Packet {
    override val requireAuthorized: Boolean = false
}

val SERVERBOUND_AUTH_ENDPOINT = Endpoint<AuthPacket>()