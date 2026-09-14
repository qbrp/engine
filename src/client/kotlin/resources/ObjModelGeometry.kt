package org.lain.engine.client.resources

import com.mojang.math.Transformation
import de.javagl.obj.Mtl
import de.javagl.obj.Obj
import de.javagl.obj.ObjFace
import de.javagl.obj.ObjSplitting
import net.fabricmc.fabric.api.renderer.v1.RendererAccess
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelState
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix3f
import org.joml.Vector3f
import org.lain.engine.mc.engineId
import java.util.function.Function
import java.util.function.Supplier

val ITEMS_ATLAS: ResourceLocation = TextureAtlas.LOCATION_BLOCKS

class ObjUnbakedModel(
    private val obj: Obj,
    private val mtl: Map<String, Mtl>,
    val options: ObjModelOptions
) : UnbakedModel {
    override fun getDependencies(): Collection<ResourceLocation> = emptyList()

    override fun resolveParents(resolver: Function<ResourceLocation, UnbakedModel>) = Unit

    override fun bake(
        baker: ModelBaker,
        textureGetter: Function<Material, TextureAtlasSprite>,
        state: ModelState
    ): BakedModel {
        val renderer = requireNotNull(RendererAccess.INSTANCE.renderer) {
            "Fabric renderer is unavailable while baking an Engine OBJ model"
        }
        val builder = renderer.meshBuilder()
        val emitter = builder.emitter
        val transform = state.rotation
        val normalMatrix = Matrix3f(transform.matrix)
        val materialGroups = ObjSplitting.splitByMaterialGroups(obj)
        var particle = options.particle?.let { textureGetter.apply(Material(ITEMS_ATLAS, it)) }

        materialGroups.forEach { (name, model) ->
            val materialDefinition = options.mtlOverride?.let(mtl::get) ?: mtl[name]
            val spriteMaterial = materialDefinition?.mapKd
                ?.let { Material(ITEMS_ATLAS, engineId(it)) }
                ?: MISSING_SPRITE
            val sprite = textureGetter.apply(spriteMaterial)
            if (particle == null) particle = sprite
            val renderMaterial = renderer.materialFinder()
                .emissive(name == "emissive")
                .find()

            for (faceIndex in 0 until model.numFaces) {
                emitFace(
                    emitter,
                    model,
                    model.getFace(faceIndex),
                    sprite,
                    renderMaterial,
                    transform,
                    normalMatrix
                )
            }
        }

        return ObjBakedModel(
            builder.build(),
            particle ?: textureGetter.apply(MISSING_SPRITE),
            options
        )
    }

    private fun emitFace(
        emitter: QuadEmitter,
        model: Obj,
        face: ObjFace,
        sprite: TextureAtlasSprite,
        material: net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial,
        transform: Transformation,
        normalMatrix: Matrix3f
    ) {
        for (vertexIndex in 0 until face.numVertices) {
            emitVertex(vertexIndex, vertexIndex, emitter, model, face, transform, normalMatrix)
        }
        if (face.numVertices == 3) {
            emitVertex(3, 2, emitter, model, face, transform, normalMatrix)
        }

        emitter
            .material(material)
            .spriteBake(
                sprite,
                MutableQuadView.BAKE_NORMALIZED or
                    (if (options.flipV) MutableQuadView.BAKE_FLIP_V else 0)
            )
            .color(-1, -1, -1, -1)
            .emit()
    }

    private fun emitVertex(
        index: Int,
        sourceIndex: Int,
        emitter: QuadEmitter,
        model: Obj,
        face: ObjFace,
        transform: Transformation,
        normalMatrix: Matrix3f
    ) {
        val sourceVertex = model.getVertex(face.getVertexIndex(sourceIndex))
        val vertex = Vector3f(sourceVertex.x, sourceVertex.y, sourceVertex.z)
        if (options.offset) vertex.add(0.5f, 0.5f, 0.5f)
        if (transform !== Transformation.identity()) {
            vertex.sub(0.5f, 0.5f, 0.5f)
            transform.matrix.transformPosition(vertex)
            vertex.add(0.5f, 0.5f, 0.5f)
        }

        val sourceNormal = model.getNormal(face.getNormalIndex(sourceIndex))
        val normal = Vector3f(sourceNormal.x, sourceNormal.y, sourceNormal.z)
        normalMatrix.transform(normal).normalize()
        val uv = model.getTexCoord(face.getTexCoordIndex(sourceIndex))

        emitter
            .pos(index, vertex)
            .normal(index, normal)
            .uv(index, uv.x, uv.y)
    }
}

private class ObjBakedModel(
    private val mesh: Mesh,
    private val particle: TextureAtlasSprite,
    private val options: ObjModelOptions
) : BakedModel, FabricBakedModel, EngineModelMetadata {
    override val disableCulling: Boolean = options.disableCulling

    override fun getQuads(
        state: BlockState?,
        direction: Direction?,
        random: RandomSource
    ): List<BakedQuad> = emptyList()

    override fun useAmbientOcclusion(): Boolean = options.useAmbientOcclusion

    override fun isGui3d(): Boolean = true

    override fun usesBlockLight(): Boolean = options.guiLight != BlockModel.GuiLight.FRONT

    override fun isCustomRenderer(): Boolean = false

    override fun getParticleIcon(): TextureAtlasSprite = particle

    override fun getTransforms(): ItemTransforms = options.transforms

    override fun getOverrides(): ItemOverrides = ItemOverrides.EMPTY

    override fun isVanillaAdapter(): Boolean = false

    override fun emitItemQuads(
        stack: ItemStack,
        randomSupplier: Supplier<RandomSource>,
        context: RenderContext
    ) {
        mesh.outputTo(context.emitter)
    }
}

data class ObjModelOptions(
    val useAmbientOcclusion: Boolean,
    val guiLight: BlockModel.GuiLight?,
    val particle: ResourceLocation?,
    val transforms: ItemTransforms,
    val flipV: Boolean,
    val mtlOverride: String?,
    val disableCulling: Boolean,
    val offset: Boolean
)
