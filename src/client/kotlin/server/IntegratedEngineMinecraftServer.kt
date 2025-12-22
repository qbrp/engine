package org.lain.engine.client.server

import org.lain.engine.EngineMinecraftServer
import org.lain.engine.EngineMinecraftServerDependencies
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.util.Injector

class IntegratedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    transportContext: ServerSingleplayerTransport
) : EngineMinecraftServer(dependencies, transportContext)