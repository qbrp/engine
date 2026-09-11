package org.lain.engine.mc.server

import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.CrashReport
import net.minecraft.ReportedException
import org.lain.engine.script.*
import org.lain.engine.script.compilation.CompilationReport
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.compilation.CompilationFailedException
import org.lain.engine.script.compilation.CompilationOutcome
import org.lain.engine.script.compilation.logTo
import org.lain.engine.script.lua.*
import org.lain.engine.util.file.FileSystem
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer
import java.io.File
import java.util.*

class DedicatedServerEngineMod : DedicatedServerModInitializer {
    private lateinit var luaScriptEngine: LuaScriptEngine
    private val moduleManager = ModuleManager()
    private var namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())

    private fun createLuaContext(
        entrypointScript: File,
        writeCompilationManifest: Boolean,
    ) = LuaScriptEngine(
        LuaScriptEngine.Dependencies(
            LuaScriptEngine.globals(),
            namespacedStorage,
            LuaDataStorage(),
            moduleManager,
            writeCompilationManifest = writeCompilationManifest,
        ),
        FileScriptSource(entrypointScript),
    )

    override fun onInitializeServer() {
        val config = loadOrCreateServerConfig()
        val entrypointScript = FileSystem.compilationEntrypoint
        if (!entrypointScript.exists()) {
            entrypointScript.createNewFile()
            entrypointScript.writeDefaultLuaEntrypointScript()
        }
        luaScriptEngine = createLuaContext(
            entrypointScript,
            config.writeCompilationManifest,
        )
        luaScriptEngine.setup()
        val compilationResult = setupContents(
            entrypointScript,
            config.writeCompilationManifest,
        )

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dependencies = EngineMinecraftServer.Dependencies(
                server,
                luaScriptEngine,
                moduleManager,
                compilationResult,
                config,
                namespacedStorage
            )
            registerMinecraftServer(
                DedicatedEngineMinecraftServer(dependencies)
            )
        }
    }

    fun setupContents(
        entrypointScript: File,
        writeCompilationManifest: Boolean,
    ): Build {
        val scanner = Scanner(System.`in`)
        error@ while (true) {
            try {
                return luaScriptEngine.compileContents().successOrThrow()
            } catch (e: CompilationFailedException) {
                ScriptEngine.LOGGER.error("Не удалось скомпилировать ресурсы Engine!")
                e.log()
                ScriptEngine.LOGGER.info("Перекомпилировать заново? y - да, n - выключить сервер")
                while (true) {
                    when (scanner.nextLine().lowercase()) {
                        "y" -> {
                            luaScriptEngine = createLuaContext(
                                entrypointScript,
                                writeCompilationManifest,
                            )
                            luaScriptEngine.setup()
                            continue@error
                        }

                        "n" -> throw ReportedException(CrashReport("Engine compilation", e))
                        else -> ScriptEngine.LOGGER.warn("y - да, n - выключить сервер")
                    }
                }
            }
        }
    }
}
