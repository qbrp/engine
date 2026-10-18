package org.lain.engine.client.resources

import org.lain.engine.client.mc.JsonMc
import org.lain.engine.script.EngineId
import org.lain.engine.script.Modules

fun Modules.compileAssets(): Assets {
    val propModels = mutableMapOf<EngineId, Asset>()
    val objGeometryModels = mutableMapOf<EngineId, Asset>()
    val mtlFiles = mutableMapOf<EngineId, Asset>()
    val sounds = mutableMapOf<EngineId, Asset>()
    val textures = mutableMapOf<String, MutableMap<EngineId, Asset>>()

    locationed.forEach { (location, module) ->
        val catalog = AssetCatalog(location.directory.toSourceFile(), module)
        val spriteAtlasRules = catalog.spriteAtlases
        val spriteAtlasDirectories = spriteAtlasRules.directories.mapValues { (_, atlasName) -> legacyAtlasName(atlasName) }
        val defaultAtlas = legacyAtlasName(spriteAtlasRules.default)

        fun textureAtlasList(atlas: String) = textures.getOrPut(atlas) { mutableMapOf() }

        catalog.browse { asset ->
            val file = asset.fileRelative
            val ext = file.extension

            try {
                when(ext) {
                    "json" -> propModels[asset.id] = asset
                    "obj" -> objGeometryModels[asset.id] = asset
                    "mtl" -> mtlFiles[asset.id] = asset
                    "ogg" -> sounds[asset.id] = asset
                    "png" -> {
                        val firstSegment = file.toPath()
                            .firstOrNull()
                            ?.toString()
                        val atlas = spriteAtlasDirectories[firstSegment]
                        textureAtlasList(atlas ?: defaultAtlas)[asset.id] = asset
                    }
                }
            } catch (e: Throwable) {
                LOGGER.error("При загрузке ресурса ${file.path} возникла ошибка", e)
            }
        }
    }

    return Assets(
        textures,
        propModels,
        objGeometryModels,
        mtlFiles,
        sounds
    )
}