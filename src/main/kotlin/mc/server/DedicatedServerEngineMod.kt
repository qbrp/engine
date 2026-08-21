package org.lain.engine.mc.server

import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import org.lain.engine.script.*
import org.lain.engine.script.lua.*
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer
import java.io.File
import java.util.*

class SetupException(val exceptions: List<CompilationException>) : Exception()

class DedicatedServerEngineMod : DedicatedServerModInitializer {
    private lateinit var luaScriptEngine: LuaScriptEngine
    private var namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())

    private fun createLuaContext(entrypointScript: File) = LuaScriptEngine(
        LuaScriptEngine.Dependencies(
            LuaScriptEngine.globals(),
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
        luaScriptEngine = createLuaContext(entrypointScript)
        luaScriptEngine.setup()
        val compilationResult = setupContents(entrypointScript)

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dependencies = EngineMinecraftServer.Dependencies(
                server,
                luaScriptEngine,
                compilationResult,
                config,
                namespacedStorage
            )
            registerMinecraftServer(
                DedicatedEngineMinecraftServer(dependencies)
            )
        }
    }

    fun setupContents(entrypointScript: File): CompilationResult {
        val scanner = Scanner(System.`in`)
        error@ while (true) {
            try {
                val result = compileContents(ENGINE_DIR.contents, luaScriptEngine)
                if (result.exceptions.isNotEmpty()) {
                    throw SetupException(result.exceptions)
                }
                return result
            } catch (e: Exception) {
                SCRIPT_LOGGERRR.error("Не удалось скомпилировать ресурсы Engine!")

                if (e is SetupException) {
                    e.exceptions.forEach {
                        SCRIPT_LOGGERRR.error(it.errorString)
                    }
                } else {
                    SCRIPT_LOGGERRR.error(e.message)
                }

                SCRIPT_LOGGERRR.info("Перекомпилировать заново? y - да, n - выключить сервер")
                while (true) {
                    when (scanner.nextLine().lowercase()) {
                        "y" -> {
                            luaScriptEngine = createLuaContext(entrypointScript)
                            continue@error
                        }

                        "n" -> throw ReportedException(CrashReport("Engine compilation", e))
                        else -> SCRIPT_LOGGERRR.warn("y - да, n - выключить сервер")
                    }
                }
            }
        }
    }
}