package org.lain.engine.client.transport

import org.lain.engine.transport.packet.ACKNOWLEDGE_CONFIRM_CHANNEL
import org.lain.engine.transport.packet.ACKNOWLEDGE_REQUEST_CHANNEL
import org.lain.engine.transport.packet.AcknowledgePacket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.fixedRateTimer

private data class PendingPacket(val id: Long, var acknowledges: Int = 0)

class ClientAcknowledgeHandler() {
    private val transportContext by injectClientTransportContext()
    private val pendingPackets = CopyOnWriteArrayList<PendingPacket>()
    private val responder = fixedRateTimer("Engine Acknowledge Packet Responding", daemon = true, period = 20L) {
        for (packet in pendingPackets) {
            val acknowledged = tryAcknowledge(packet.id)
            packet.acknowledges++
            if (acknowledged || packet.acknowledges > 20) {
                pendingPackets.remove(packet)
            }
        }
    }

    private fun tryAcknowledge(id: Long): Boolean {
        return if (!transportContext.packetHistory.contains(id)) {
            false
        } else {
            ACKNOWLEDGE_CONFIRM_CHANNEL
                .sendC2SPacket(AcknowledgePacket(id))
            true
        }
    }

    fun run() {
        ACKNOWLEDGE_REQUEST_CHANNEL.registerClientReceiver {
            val acknowledged = tryAcknowledge(id)
            if (!acknowledged) pendingPackets.add(PendingPacket(id))
        }
    }
}