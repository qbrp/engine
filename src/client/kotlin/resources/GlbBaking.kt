package org.lain.engine.client.resources

import com.mojang.blaze3d.vertex.PoseStack
import de.javagl.jgltf.model.NodeModel
import net.fabricmc.fabric.api.renderer.v1.Renderer
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.Material
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.render.item.EngineTransformation
import java.util.function.Function

fun buildGltf(
    renderer: Renderer,
    textureGetter: Function<Material, TextureAtlasSprite>,
    gltf: ParsedGltf,
    flipV: Boolean
) {
    gltf.model.materialModels.forEach { material ->
        material
    }
    renderer.materialFinder().find()

    //gltf.model.nodeModels.forEach { node -> node.bake(gltf, textureGetter) }
}

private fun NodeModel.bake(
    renderer: Renderer,
    gltf: ParsedGltf,
    textureGetter: Function<Material, TextureAtlasSprite>,
) {
    val meshBuilder = renderer.meshBuilder()
    val quadEmitter = meshBuilder.emitter
    meshModels.forEach { mesh ->
        mesh.meshPrimitiveModels.forEach { primitive ->
            val material = gltf.materials[primitive.materialModel]!!
            val minecraftMaterial = Material(
                ITEMS_ATLAS,
                material.baseColorTexture.textureView.registrationId
            )
            val indices = primitive.indices ?: error("Нода $name содержит неиндексированную геометрию")
            for (i in 0 until indices.accessorData.numElements) {
            }
        }
    }
}

data class MeshModelNode(
    val name: String,
    val mesh: Mesh,
    val children: List<MeshModelNode>,
    val transformation: EngineTransformation,
    val material: MeshModelMaterial
)

data class MeshModelMaterial(
    val name: String,
    val texture: ResourceLocation
)