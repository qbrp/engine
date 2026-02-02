package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.SoundPlay
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Serializable
data class SoundPlayPacket(val play: SoundPlay) : Packet

val CLIENTBOUND_SOUND_PLAY_ENDPOINT = Endpoint<SoundPlayPacket>()
