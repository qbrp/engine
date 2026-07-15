package org.lain.engine.mc.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.Constants
import org.lain.engine.mc.*
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.player.PlayerId
import org.lain.engine.player.Username
import org.lain.engine.script.*
import org.lain.engine.script.lua.*
import org.lain.engine.server.Notification
import org.lain.engine.server.network
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.network.ConnectionSession
import org.lain.engine.transport.network.ServerConnectionManager
import org.lain.engine.transport.network.ServerNetworkTransport
import org.lain.engine.transport.network.SessionId
import org.lain.engine.transport.packet.*
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer
import java.io.File
import java.util.*
import kotlin.collections.iterator

class SetupException(val exceptions: List<CompilationException>) : Exception()

class DedicatedServerEngineMod : DedicatedServerModInitializer {
    private lateinit var luaContext: LuaContext
    private var namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage())

    private fun createLuaContext(entrypointScript: File) = LuaContext(
        LuaDependencies(
            EngineLuaGlobals(),
            namespacedStorage,
            ENGINE_DIR.scripts.path,
            LuaDataStorage()
        ),
        FileScriptSource(entrypointScript),
    )

    override fun onInitializeServer() {
        val config = loadOrCreateServerConfig()
        val entrypointScript = getLuaEntrypointDir(config.server)
        if (!entrypointScript.exists()) {
            entrypointScript.createNewFile()
            entrypointScript.writeDefaultLuaEntrypointScript()
        }
        luaContext = createLuaContext(entrypointScript)
        luaContext.setup()
        val compilationResult = setupContents(entrypointScript)

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dependencies = EngineMinecraftServerDependencies(server, luaContext, compilationResult, config, namespacedStorage)
            registerMinecraftServer(
                DedicatedEngineMinecraftServer(dependencies)
            )
        }
    }

    fun setupContents(entrypointScript: File): CompilationResult {
        val scanner = Scanner(System.`in`)
        error@ while (true) {
            try {
                val result = compileContents(ENGINE_DIR.contents, luaContext)
                if (result.exceptions.isNotEmpty()) {
                    throw SetupException(result.exceptions)
                }
                return result
            } catch (e: Exception) {
                LOGGER.error("Не удалось скомпилировать ресурсы Engine!")

                if (e is SetupException) {
                    e.exceptions.forEach {
                        LOGGER.error(it.errorString)
                    }
                } else {
                    LOGGER.error(e.message)
                }

                LOGGER.info("Перекомпилировать заново? y - да, n - выключить сервер")
                while (true) {
                    when (scanner.nextLine().lowercase()) {
                        "y" -> {
                            luaContext = createLuaContext(entrypointScript)
                            continue@error
                        }
                        "n" -> throw ReportedException(CrashReport("Engine compilation", e))
                        else -> LOGGER.warn("y - да, n - выключить сервер")
                    }
                }
            }
        }
    }
}