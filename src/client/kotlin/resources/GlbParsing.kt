package org.lain.engine.client.resources

import de.javagl.jgltf.model.GltfModel
import de.javagl.jgltf.model.MaterialModel
import de.javagl.jgltf.model.impl.DefaultPbrMaterialModel
import de.javagl.jgltf.model.io.GltfModelReader
import java.nio.ByteBuffer

data class ParsedGltf(
    val model: GltfModel,
    val textures: List<GlbTexture>,
    val materials: Map<MaterialModel, GltfMaterial>
)

data class GlbTexture(
    val name: String,
    val atlas: String,
    val textureView: TextureView
)

data class GltfMaterial(
    val name: String,
    val baseColorTexture: GlbTexture,
)

fun Asset.parseGlb(): ParsedGltf {
    val gltf = GltfModelReader().read(source.toPath())

    val texturesByModel = gltf.textureModels.associateWith { texture ->
        val name = texture.name ?: error("Не указано имя GLTF-текстуры")
        GlbTexture(
            name,
            assetsModule.spriteAtlasRules.resolve(this),
            GlbTextureView(
                texture.imageModel.imageData,
                id.toMinecraftId().withPath("texture/${texture.name}")
            ),
        )
    }

    val materials = gltf.materialModels
        .associateWith { model ->
            require(model is DefaultPbrMaterialModel)
            GltfMaterial(
                model.name ?: error("Не указано имя GLTF-материала"),
                model.baseColorTextureModel?.let(texturesByModel::getValue)
                    ?: error("Не найдена GLTF-текстура"),
            )
        }

    return ParsedGltf(gltf, texturesByModel.values.toList(), materials)
}