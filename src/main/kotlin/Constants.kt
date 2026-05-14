package org.lain.engine

import net.fabricmc.loader.api.FabricLoader
import org.lain.engine.Constants.DEBUG_PACKETS
import org.slf4j.LoggerFactory

object Constants {
    const val DEBUG_PACKETS = false
    const val SIMULATE_LATENCY = false
    val DEBUG_ALL = true
    val LOAD_LUA_LIBRARIES = false
    val DEVELOPER_TEST_ENVIRONMENT = System.getenv("ENGINE_DEV").toBoolean()
    val ALLOWED_VERSIONS = listOf(ENGINE_MOD_VERSION)
    val ENGINE_MOD_VERSION
        get() = FabricLoader.getInstance().getModContainer(CommonEngineServerMod.MOD_ID).get().metadata.version.friendlyString
}

private val PACKET_LOGGER = LoggerFactory.getLogger("Engine Packets")
fun debugPacket(msg: String) {
    if (DEBUG_PACKETS) { PACKET_LOGGER.info(msg) }
}