package org.lain.engine.client.resources

import com.mojang.blaze3d.platform.NativeImage
import de.javagl.jgltf.model.TextureModel
import de.javagl.obj.Obj
import de.javagl.obj.ObjSplitting
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.resources.metadata.animation.FrameSize
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceMetadata
import org.lain.engine.script.EngineId
import java.nio.ByteBuffer

data class ModelCatalog(
    val props: Map<EngineId, LinkedPropModel>,
    val textureViews: Map<String, List<TextureView>>
)

data class LinkedObjGroup(
    val mesh: Obj,
    val material: ParsedMtlMaterial
)

data class LinkedObj(val groups: List<LinkedObjGroup>)

class GlbTextureView(
    val buffer: ByteBuffer,
    override val registrationId: ResourceLocation
) : TextureView {
    override fun open(): SpriteContents {
        val nativeImage = NativeImage.read(buffer)
        return SpriteContents(
            registrationId,
            FrameSize(nativeImage.width, nativeImage.height),
            nativeImage,
            ResourceMetadata.EMPTY
        )
    }
}

fun Assets.catalogizeModels(): ModelCatalog {
    val textureViews: MutableMap<String, MutableList<TextureView>> =
        textureAssets.mapValuesTo(mutableMapOf()) { (_, textureAssets) ->
            textureAssets.mapTo(mutableListOf()) { (_, asset) -> AssetTextureView(asset) }
        }

    val parsedGlbBundles = buildMap {
        glbBundles.map { (id, glbAsset) ->
            try {
                val parsed = glbAsset.parseGlb()
                put(id, parsed)
                parsed.textures.forEach { texture ->
                    textureViews.getOrPut(texture.atlas) { mutableListOf() } += texture.textureView                }
            } catch (e: Exception) {
                LOGGER.error("Не удалось загружить glb-геометрию: ${e.message}")
            }
        }
    }

    val linkedPropModels = buildMap {
        propModels.forEach { (id, propModel) ->
            try {
                val parsedProp = propModel.parsePropModel()
                val linkedGeometry = when(val geometry = parsedProp.geometry) {
                    is ParsedPropModel.Geometry.Json -> LinkedPropModel.Geometry.Json(geometry.blockModel)
                    is ParsedPropModel.Geometry.Obj -> {
                        val objGeometry = objModels[geometry.model] ?: error("Геометрия не найдена: ${geometry.model}")
                        val linkedObjGeometry = try {
                            val parsedObjGeometry = objGeometry.parseObjModel()
                            val materials = parsedObjGeometry.materialLibrariesRefs
                                .flatMap { mtlRef ->
                                    try {
                                        val mtlLibraryId = when(mtlRef) {
                                            is ParsedObj.MtlRef.Direct -> mtlRef.id
                                            is ParsedObj.MtlRef.Slot -> geometry.mtlOverride[mtlRef.name]
                                                ?: error("Слот mtl не разрешён: ${mtlRef.name}")
                                        }
                                        val mtlAsset = mtlFiles[mtlLibraryId] ?: error("Библиотека mtl не найдена: $mtlLibraryId")
                                        mtlAsset.parseMtlLibrary().materials
                                    } catch (e: Exception) {
                                        throw IllegalStateException("Не удалось загрузить MTL-файл: ${e.message}", e)
                                    }
                                }
                                .associateBy { it.source.name }

                            LinkedObj(
                                ObjSplitting.splitByMaterialGroups(parsedObjGeometry.model)
                                    .map { (materialName, objGroup) -> LinkedObjGroup(objGroup, materials[materialName]!!) }
                            )
                        } catch (e: Exception) {
                            LOGGER.error("Не удалось слинковать obj-геометрию $id: ${e.message}")
                            return@forEach
                        }

                        LinkedPropModel.Geometry.Obj(linkedObjGeometry, geometry.flipV)
                    }
                }
                put(
                    id,
                    LinkedPropModel(parsedProp.properties, linkedGeometry)
                )
            } catch (e: Exception) {
                LOGGER.error("Не удалось загрузить модель пропа $id: ${e.message}")
            }
        }
    }

    return ModelCatalog(linkedPropModels, textureViews)
}