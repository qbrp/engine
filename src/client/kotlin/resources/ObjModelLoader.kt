package org.lain.engine.client.resources

import de.javagl.obj.Mtl
import de.javagl.obj.Obj
import de.javagl.obj.ObjFace
import de.javagl.obj.ObjSplitting
import dev.felnull.specialmodelloader.SpecialModelLoader
import net.fabricmc.fabric.api.renderer.v1.Renderer
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer
import net.minecraft.block.BlockState
import net.minecraft.client.model.SpriteGetter
import net.minecraft.client.render.model.*
import net.minecraft.client.render.model.UnbakedModel.GuiLight
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.texture.Sprite
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.client.util.SpriteIdentifier
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.BlockRenderView
import org.joml.Vector3f
import org.lain.engine.util.EngineId
import java.util.function.Predicate
import java.util.function.Supplier

class MeshModel(
    private val useAmbientOcclusion: Boolean,
    private val isSideLit: Boolean,
    private val particleSprite: Sprite?,
    private val transforms: ModelTransformation?,
    private val mesh: Mesh,
    val disableCulling: Boolean
) : BakedModel, FabricBakedModel {
    private val quadCache: Array<MutableList<BakedQuad>> by lazy { ModelHelper.toQuadLists(mesh) }

    override fun emitBlockQuads(
        emitter: QuadEmitter?,
        blockView: BlockRenderView?,
        state: BlockState?,
        pos: BlockPos?,
        randomSupplier: Supplier<Random?>?,
        cullTest: Predicate<Direction?>?
    ) {
        mesh.outputTo(emitter)
    }

    override fun emitItemQuads(emitter: QuadEmitter?, randomSupplier: Supplier<Random?>?) {
        mesh.outputTo(emitter)
    }

    override fun getQuads(
        blockState: BlockState?,
        direction: Direction?,
        randomSource: Random
    ): MutableList<BakedQuad> {
        return this.quadCache[ModelHelper.toFaceIndex(direction)]
    }

    override fun useAmbientOcclusion(): Boolean = useAmbientOcclusion

    override fun hasDepth(): Boolean = true

    override fun isSideLit(): Boolean = isSideLit

    override fun getParticleSprite(): Sprite? = particleSprite

    override fun getTransformation(): ModelTransformation? = transforms
}


class ObjUnbakedModel(
    private val location: Identifier,
    private val obj: Obj,
    private val mtl: Map<String, Mtl>,
    private val options: ObjModelOptions
): UnbakedModel {
    private val particleLocation = options.particle?.let { particle ->
        SpriteIdentifier(
            SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
            particle
        )
    } ?: MISSING_SPRITE


    override fun resolve(resolver: ResolvableModel.Resolver?) {}

    override fun bake(
        textureSlots: ModelTextures?,
        baker: Baker,
        modelState: ModelBakeSettings,
        hasAmbientOcclusion: Boolean,
        useBlockLight: Boolean,
        transforms: ModelTransformation?
    ): BakedModel {
        var renderer = Renderer.get()

        if (renderer == null) {
            SpecialModelLoader.LOGGER.warn("IndigoRenderer is used since the Renderer cannot be obtained. ({})", location)
            renderer = IndigoRenderer.INSTANCE
        }

        val builder = renderer.mutableMesh()
        val emitter = builder.emitter()
        val spriteGetter = baker.spriteGetter
        val materialGroups = ObjSplitting.splitByMaterialGroups(obj)

        materialGroups.forEach { (name: String, model: Obj) ->
            for (i in 0..<model.numFaces) {
                emitFace(emitter, modelState, spriteGetter, name, model, model.getFace(i))
            }
        }

        return MeshModel(
            options.useAmbientOcclusion,
            guiLight?.isSide ?: true,
            particleLocation.let { spriteGetter.get(it) },
            options.transforms,
            builder.immutableCopy(),
            options.disableCulling
        )
    }

    private fun emitFace(
        emitter: QuadEmitter,
        modelState: ModelBakeSettings,
        spriteGetter: SpriteGetter,
        materialName: String?,
        fObj: Obj,
        face: ObjFace
    ) {
        for (i in 0..<face.numVertices) {
            emitVertex(i, i, emitter, modelState, fObj, face)
        }

        if (face.numVertices == 3) emitVertex(3, 2, emitter, modelState, fObj, face)

        val smtl = mtl.get(materialName)

        var flg = MutableQuadView.BAKE_NORMALIZED

        if (options.flipV) flg = flg or MutableQuadView.BAKE_FLIP_V

        if (modelState.isUvLocked) flg = flg or MutableQuadView.BAKE_LOCK_UV

        val textureId: Identifier? = smtl?.mapKd?.let { EngineId(it) }

        val sprite = textureId?.let {
            SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, textureId)
        } ?: MISSING_SPRITE

        emitter.spriteBake(spriteGetter.get(sprite), flg)
        emitter.color(-1, -1, -1, -1)
        emitter.emit()
    }

    private fun emitVertex(
        index: Int,
        vertexNum: Int,
        emitter: QuadEmitter,
        modelState: ModelBakeSettings,
        fObj: Obj,
        face: ObjFace
    ) {
        val vt = fObj.getVertex(face.getVertexIndex(vertexNum))
        val vertex = Vector3f(vt.x, vt.y, vt.z)

        vertex.add(-0.5f, -0.5f, -0.5f)
        vertex.rotate(modelState.rotation.leftRotation)
        vertex.add(0.5f, 0.5f, 0.5f)

        val normal = fObj.getNormal(face.getNormalIndex(vertexNum))
        val tex = fObj.getTexCoord(face.getTexCoordIndex(vertexNum))

        emitter
            .pos(index, vertex.x, vertex.y, vertex.z)
            .normal(index, normal.x, normal.y, normal.z)
            .uv(index, tex.x, tex.y)
    }
}

data class ObjModelOptions(
    val useAmbientOcclusion: Boolean,
    val guiLight: GuiLight?,
    val particle: Identifier?,
    val transforms: ModelTransformation,
    val flipV: Boolean,
    val mtlOverride: String?,
    val disableCulling: Boolean
)