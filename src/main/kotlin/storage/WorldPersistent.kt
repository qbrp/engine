package org.lain.engine.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.cyberia.ecs.Component
import org.lain.engine.server.EngineServer
import org.lain.engine.world.World
import java.io.File

@Serializable
data class WorldPersistent(
    val components: List<ComponentDto>
)

val File.worldData
    get() = this.resolve("engine-worlds")

private val WorldJson = Json {
    serializersModule = COMPONENT_SERIALIZERS_MODULE
}

fun EngineServer.worldSavePath(world: World) = globals.savePath.worldData.resolve(world.id.value)

fun EngineServer.saveWorld(world: World) = with(world) {
    val persistent = WorldPersistent(
        world.componentManager.getSavableComponents(world.worldState)
            .map { it.toCommonDto() }
    )
    worldSavePath(world).writeText(WorldJson.encodeToString(persistent))
}

fun EngineServer.loadWorldComponents(world: World): List<Component> {
    val file = worldSavePath(world)
    file.parentFile.mkdirs()
    if (!file.exists()) return emptyList()
    return WorldJson.decodeFromString<WorldPersistent>(file.readText()).components.map {
        it.toDomain(ComponentLoadSettings(null, namespacedStorage)) ?: error("Could not load component $it")
    }
}