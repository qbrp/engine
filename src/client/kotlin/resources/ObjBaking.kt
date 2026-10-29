package org.lain.engine.client.resources

import de.javagl.obj.Obj
import de.javagl.obj.ObjFace
import net.fabricmc.fabric.api.renderer.v1.Renderer
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.Material
import org.joml.Vector3f
import org.lain.engine.client.mc.compat.emitWithVanillaQuad
import java.util.function.Function

fun MeshBuilder.buildObj(
    renderer: Renderer,
    textureGetter: Function<Material, TextureAtlasSprite>,
    obj: LinkedObj,
    flipV: Boolean,
    bakedQuads: MutableList<BakedQuad>? = null,
) {
    obj.groups.forEach { group ->
        val mtl = group.material.source
        val mesh = group.mesh
        val spriteMaterial = Material(
            ITEMS_ATLAS,
            group.material.diffuseTexture.toMinecraftId()
        )
        val sprite = textureGetter.apply(spriteMaterial)
        val renderMaterial = renderer.materialFinder()
            .emissive(mtl.name == "emissive")
            .find()

        for (faceIndex in 0 until mesh.numFaces) {
            emitFace(
                emitter,
                mesh,
                mesh.getFace(faceIndex),
                sprite,
                renderMaterial,
                flipV,
                bakedQuads,
            )
        }
    }
}

private fun emitFace(
    emitter: QuadEmitter,
    model: Obj,
    face: ObjFace,
    sprite: TextureAtlasSprite,
    material: RenderMaterial,
    flipV: Boolean,
    bakedQuads: MutableList<BakedQuad>?,
) {
    for (vertexIndex in 0 until face.numVertices) {
        emitVertex(vertexIndex, vertexIndex, emitter, model, face)
    }
    if (face.numVertices == 3) {
        emitVertex(3, 2, emitter, model, face)
    }

    emitter
        .material(material)
        .spriteBake(
            sprite,
            MutableQuadView.BAKE_NORMALIZED or
                    (if (flipV) MutableQuadView.BAKE_FLIP_V else 0)
        )
        .color(-1, -1, -1, -1)
    emitter.emitWithVanillaQuad(sprite, bakedQuads)
}

private fun emitVertex(
    index: Int,
    sourceIndex: Int,
    emitter: QuadEmitter,
    model: Obj,
    face: ObjFace,
) {
    val sourceVertex = model.getVertex(face.getVertexIndex(sourceIndex))

    val vertex = Vector3f(
        sourceVertex.x,
        sourceVertex.y,
        sourceVertex.z
    )

    vertex.add(0.5f, 0.5f, 0.5f)

    val sourceNormal = model.getNormal(face.getNormalIndex(sourceIndex))
    val normal = Vector3f(
        sourceNormal.x,
        sourceNormal.y,
        sourceNormal.z
    )

    val uv = model.getTexCoord(face.getTexCoordIndex(sourceIndex))

    emitter
        .pos(index, vertex)
        .normal(index, normal)
        .uv(index, uv.x, uv.y)
}
