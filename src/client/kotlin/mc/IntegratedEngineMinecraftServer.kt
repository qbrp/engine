package org.lain.engine.client.mc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.EngineMinecraftServerDependencies
import org.lain.engine.client.EngineClient
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.script.*
import org.lain.engine.script.lua.FileScriptSource
import org.lain.engine.script.lua.LuaContext
import org.lain.engine.script.lua.writeDefaultLuaEntrypointScript
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.util.Injector
import org.lain.engine.util.file.ENGINE_DIR
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.registerMinecraftServer

class IntegratedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    client: EngineClient
) : EngineMinecraftServer(dependencies) {
    override val transportContext: ServerTransportContext = ServerSingleplayerTransport(client, engine)
}

fun EngineMinecraftClient.registerEngineIntegratedServerEvent(engineClient: EngineClient) {
    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        val config = loadOrCreateServerConfig()
        val serverId = config.server
        val entrypoint = getLuaEntrypointDir(serverId)
        if (!entrypoint.exists()) {
            entrypoint.createNewFile()
            entrypoint.writeDefaultLuaEntrypointScript()
        }
        val context = LuaContext(engineClient.createLuaDependencies(ENGINE_DIR.scripts), FileScriptSource(entrypoint))
        context.setup()
        val compilationResult = compileContents(ENGINE_DIR.contents, context)

        runBlocking {
            withContext(MinecraftClientDispatcher) {
                engineClient.createLuaContext(serverId)
                engineClient.compileScripts()
            }
        }

        val dependencies = EngineMinecraftServerDependencies(
            server,
            context,
            compilationResult,
            config,
            ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage())
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