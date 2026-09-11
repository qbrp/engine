package org.lain.engine.client.mc

import com.charleskorn.kaml.YamlException
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.client.EngineClient
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.handler.disconnectText
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.*
import org.lain.engine.script.compilation.CompilationFailedException
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.writeDefaultLuaEntrypointScript
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.util.file.FileSystem
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer

class IntegratedEngineMinecraftServer(
    dependencies: Dependencies,
    client: EngineClient
) : EngineMinecraftServer(dependencies) {
    override val transportContext: ServerTransportContext = ServerSingleplayerTransport(client, engine)

    override suspend fun validateCharacter(
        player: EnginePlayer,
        characterId: String,
        character: EngineCharacter?,
        sessionTicket: SessionTicket?
    ): EngineCharacter {
        return character ?: error("Не указаны данные персонажа с клиента")
    }

    companion object {
        var serverStartFail: Exception? = null

        fun registerEvent(client: EngineMinecraftClient) {
            ServerLifecycleEvents.SERVER_STARTING.register { server ->
                val dependencies = try {
                    val config = loadOrCreateServerConfig()
                    val entrypoint = FileSystem.compilationEntrypoint
                    if (!entrypoint.exists()) {
                        entrypoint.createNewFile()
                        runCatching {
                            entrypoint.writeDefaultLuaEntrypointScript()
                        }
                            .onFailure { entrypoint.delete() }
                            .getOrThrow()
                    }
                    val namespacedStorage =
                        ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
                    val context = LuaScriptEngine(
                        LuaScriptEngine.Dependencies(
                            LuaScriptEngine.globals(),
                            namespacedStorage,
                            client.engine.luaDataStorage,
                            client.engine.moduleManager,
                            writeCompilationManifest = config.writeCompilationManifest,
                        ), FileScriptSource(entrypoint)
                    )
                    context.setup()
                    val build = context.compileContents().successOrThrow()
                    Dependencies(
                        server,
                        context,
                        client.engine.moduleManager,
                        build,
                        config,
                        namespacedStorage
                    )
                } catch (e: Exception) {
                    if (e is CompilationFailedException || e is YamlException) {
                        serverStartFail = e
                        return@register
                    } else {
                        throw e
                    }
                }
                Injector.register<ClientTransportContext>(ClientSingleplayerTransport(client.engine))

                registerMinecraftServer(
                    IntegratedEngineMinecraftServer(
                        dependencies,
                        client.engine
                    ).also { client.server = it }
                )
            }

            ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
                client.server = null
            }
        }
    }
}
