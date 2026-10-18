package org.lain.engine.client.resources

import de.javagl.jgltf.model.GltfModel
import de.javagl.obj.Obj
import de.javagl.obj.ObjSplitting
import org.lain.engine.script.EngineId

data class ModelCatalog(val props: Map<EngineId, PropModelProperties>)

data class ParsedGlb(val model: GltfModel)

data class LinkedObjGroup(
    val mesh: Obj,
    val material: ParsedMtlMaterial
)

data class LinkedObj(val groups: List<LinkedObjGroup>)

fun Assets.catalogizeModels(): ModelCatalog {
    val parsedObjModels = buildMap {
        objModels.forEach { (id, asset) ->
            try {
                put(id, asset.parseObjModel())
            } catch (e: Exception) {
                LOGGER.error("Не удалось загрузить obj-модель: ${e.message}")
            }
        }
    }
    val linkedObjs = buildMap {
        parsedObjModels.forEach { (id, parsedObj) ->
            try {
                val materials = parsedObj.materialLibraries
                    .flatMap { referencedMtlId ->
                        try {
                            val asset =
                                mtlFiles[referencedMtlId] ?: error("Библиотека mtl не найдена: $referencedMtlId")
                            asset.parseMtlLibrary().materials
                        } catch (e: Exception) {
                            throw IllegalStateException("Не удалось загрузить MTL-файл: ${e.message}", e)
                        }
                    }
                    .associateBy { it.source.name }
                put(
                    id,
                    LinkedObj(
                        ObjSplitting.splitByMaterialGroups(parsedObj.model)
                            .map { (materialName, objGroup) -> LinkedObjGroup(objGroup, materials[materialName]!!) }
                    )
                )
            } catch (e: Exception) {
                LOGGER.error("Не удалось слинковать obj-модель $id: ${e.message}")
            }
        }
    }
    val parsedPropModels = buildMap {
        propModels.forEach { (id, propModel) ->
            try {
                put(id, propModel.parsePropModel(linkedObjs))
            } catch (e: Exception) {
                LOGGER.error("Не удалось загрузить модель пропа $id: ${e.message}")
            }
        }
    }

    return ModelCatalog(parsedPropModels)
}