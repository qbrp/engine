package org.lain.engine

import net.fabricmc.loader.api.FabricLoader
import org.lain.engine.Constants.DEBUG_PACKETS
import org.lain.engine.mc.CommonEngineMod
import org.slf4j.LoggerFactory

object Constants {
    const val DEBUG_PACKETS = true
    const val SIMULATE_LATENCY = false
    const val DEBUG_ALL = false
    const val LOAD_LUA_LIBRARIES = true
    val DEVELOPER_TEST_ENVIRONMENT = System.getenv("ENGINE_DEV").toBoolean()
    val ALLOWED_VERSIONS = listOf(ENGINE_MOD_VERSION)
    val ENGINE_MOD_VERSION: String
        get() = FabricLoader.getInstance().getModContainer(CommonEngineMod.MOD_ID).get().metadata.version.friendlyString
}

private val PACKET_LOGGER = LoggerFactory.getLogger("Engine Packets")
fun debugPacket(msg: String) {
    if (DEBUG_PACKETS) { PACKET_LOGGER.info(msg) }
}