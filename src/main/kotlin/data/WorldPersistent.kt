package org.lain.engine.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptComponent
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ReplicationSnapshot
import org.lain.engine.util.file.FileSystem
import org.lain.engine.world.World
import java.io.File

@Serializable
data class WorldPersistent(val components: List<PersistentComponentDto.Script>)

val File.worldData
    get() = this.resolve("engine-worlds")

fun EngineServer.worldSavePath(world: World) = FileSystem.ensureDirectory(globals.savePath.worldData)
    .resolve(
        world.id.value.replace(":", "-") + ".json"
    )

fun EngineServer.saveWorld(world: World) = with(world) {
    val persistent = WorldPersistent(
        world.componentManager.getSavableComponents(world.state)
            .mapNotNull { (type, component) ->
                when(component) {
                    is ScriptComponent -> component.snapshot().toPersistentDto()
                    else -> {
                        LOGGER.warn("Движковый компонент $component ($type) проигнорирован при сохранении мира ${world.id}")
                        null
                    }
                }
            }
    )
    FileSystem.ensureFile(worldSavePath(world))
        .writeText(Json.encodeToString(persistent))
}

fun EngineServer.loadWorldComponents(world: World): List<Component> {
    val file = worldSavePath(world)
    file.parentFile.mkdirs()
    if (!file.exists()) return emptyList()
    return Json.decodeFromString<WorldPersistent>(file.readText()).components.mapNotNull {
        try {
            it.decode().revive(EntityResolver.EMPTY, world.componentReviveSettings)
        } catch (e: Exception) {
            LOGGER.error("Не удалость загрузить компонент $it состояния мира ${world.id}", e)
            null
        }
    }
}