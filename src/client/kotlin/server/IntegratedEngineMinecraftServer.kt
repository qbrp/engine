package org.lain.engine.client.server

import org.lain.engine.EngineMinecraftServer
import org.lain.engine.EngineMinecraftServerDependencies
import org.lain.engine.client.EngineClient
import org.lain.engine.transport.ServerTransportContext

class IntegratedEngineMinecraftServer(
    dependencies: EngineMinecraftServerDependencies,
    client: EngineClient,
) : EngineMinecraftServer(dependencies) {
    override val transportContext: ServerTransportContext = ServerSingleplayerTransport(client, engine)
}