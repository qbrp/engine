package org.lain.engine

import org.lain.engine.SharedConstants.DEBUG_PACKETS
import org.slf4j.LoggerFactory

object SharedConstants {
    const val DEBUG_PACKETS = true
    const val SIMULATE_LATENCY = false
}

private val PACKET_LOGGER = LoggerFactory.getLogger("Engine Packets")
fun debugPacket(msg: String) {
    if (DEBUG_PACKETS) { PACKET_LOGGER.info(msg) }
}