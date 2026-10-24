package org.lain.engine.client.resources

import org.lain.engine.script.EngineId
import org.lain.engine.script.Module
import org.lain.engine.script.Modules

data class AssetModule(
    val module: Module,
    val spriteAtlasRules: SpriteAtlasRules
)

fun Modules.compileAssets(): Assets {
    val propModels = mutableMapOf<EngineId, Asset>()
    val objGeometryModels = mutableMapOf<EngineId, Asset>()
    val mtlFiles = mutableMapOf<EngineId, Asset>()
    val sounds = mutableMapOf<EngineId, Asset>()
    val glb = mutableMapOf<EngineId, Asset>()
    val textures = mutableMapOf<String, MutableMap<EngineId, Asset>>()

    locationed.forEach { (location, module) ->
        val catalog = AssetCatalog(location.directory.toSourceFile(), module)
        val spriteAtlasRules = catalog.spriteAtlases

        fun textureAtlasList(atlas: String) = textures.getOrPut(atlas) { mutableMapOf() }

        catalog.browse { asset ->
            val file = asset.fileRelative
            val ext = file.extension

            try {
                when(ext) {
                    "json" -> propModels[asset.id] = asset
                    "obj" -> objGeometryModels[asset.id] = asset
                    "glb" -> glb[asset.id] = asset
                    "mtl" -> mtlFiles[asset.id] = asset
                    "ogg" -> sounds[asset.id] = asset
                    "png" -> {
                        val atlas = spriteAtlasRules.resolve(asset)
                        textureAtlasList(atlas)[asset.id] = asset
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
        sounds,
        glb,
    )
}