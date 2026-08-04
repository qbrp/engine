package org.lain.engine.client.mc

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.level.ServerPlayer
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.mc.server.EngineMinecraftServerDependencies
import org.lain.engine.client.EngineClient
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.mc.server.SessionTicket
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.*
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.writeDefaultLuaEntrypointScript
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer
import org.luaj.vm2.Lua

class IntegratedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    private val client: EngineClient
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

    override fun onJoinPlayer(entity: ServerPlayer) {
        // загрузка камеры происходит в EngineMinecraftClient
//        if (dependencies.isReplay && !entity.isReplayViewer) {
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    val settings =
//                        withClientContext { engine.serverMinecraftPlayerLoadSettings(entity, entity.engineId) }
//                    engine.playerLoader.loadPreparing(
//                        settings = settings,
//                        account = PlayerLoadSettings.Account(null)
//                    )
//                } catch (e: Throwable) {
//                    client.infrastructure.disconnect("Не удалось настроить повтор: ${e.message ?: "Неизвестная ошибка"}")
//                    e.printStackTrace()
//                }
//            }
//        }
    }
}

fun EngineMinecraftClient.registerEngineIntegratedServerEvent(engineClient: EngineClient) {
    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        val config = loadOrCreateServerConfig()
        val serverId = config.server
        val entrypoint = getLuaEntrypointDir(serverId)
        if (!entrypoint.exists()) {
            entrypoint.createNewFile()
            runCatching {
                entrypoint.writeDefaultLuaEntrypointScript()
            }
                .onFailure { entrypoint.delete() }
                .getOrThrow()
        }
        val namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
        val context = LuaScriptEngine(
            LuaScriptEngine.Dependencies(
                LuaScriptEngine.globals(),
                namespacedStorage,
                ENGINE_DIR.scripts.path,
                engineClient.luaDataStorage,
            ), FileScriptSource(entrypoint)
        )
        context.setup()
        val compilationResult = compileContents(ENGINE_DIR.contents, context)

        val dependencies = EngineMinecraftServerDependencies(
            server,
            context,
            compilationResult,
            config,
            namespacedStorage
        )
        Injector.register<ClientTransportContext>(ClientSingleplayerTransport(engineClient))

        registerMinecraftServer(
            IntegratedEngineMinecraftServer(
                dependencies,
                engineClient
            ).also { this.server = it }
        )
    }

    ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
        this.server = null
    }
}