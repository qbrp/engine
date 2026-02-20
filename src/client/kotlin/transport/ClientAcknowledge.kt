package org.lain.engine.client.transport

import org.lain.engine.transport.packet.ACKNOWLEDGE_CONFIRM_CHANNEL
import org.lain.engine.transport.packet.ACKNOWLEDGE_REQUEST_CHANNEL
import org.lain.engine.transport.packet.AcknowledgePacket

private data class PendingPacket(val id: Long, var acknowledges: Int = 0)

class ClientAcknowledgeHandler() {
    private val transportContext by injectClientTransportContext()
    private val pendingPackets = mutableListOf<PendingPacket>()

    private fun tryAcknowledge(id: Long): Boolean {
        return if (!transportContext.packetHistory.contains(id)) {
            false
        } else {
            ACKNOWLEDGE_CONFIRM_CHANNEL
                .sendC2SPacket(AcknowledgePacket(id))
            true
        }
    }

    fun tick() {
        val toRemove = mutableListOf<PendingPacket>()
        for (packet in pendingPackets) {
            val acknowledged = tryAcknowledge(packet.id)
            packet.acknowledges++
            if (acknowledged || packet.acknowledges > 40) {
                toRemove.add(packet)
            }
        }
        pendingPackets.removeAll(toRemove)
    }

    fun run() {
        ACKNOWLEDGE_REQUEST_CHANNEL.registerClientReceiver {
            val acknowledged = tryAcknowledge(id)
            if (!acknowledged) pendingPackets.add(PendingPacket(id))
        }
    }
}