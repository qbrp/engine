package org.lain.engine.client.resources

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.SimpleBakedModel
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import org.lain.engine.client.mc.compat.AcceleratedMeshModels
import org.lain.engine.client.render.item.AdditionalTransformationsBank
import org.lain.engine.client.render.item.minecraft
import java.util.function.Supplier

val ITEMS_ATLAS: ResourceLocation = TextureAtlas.LOCATION_BLOCKS

class MeshModel(
    private val mesh: Mesh,
    private val particle: TextureAtlasSprite,
    val properties: PropModelProperties,
    bakedQuads: List<BakedQuad> = emptyList(),
) : SimpleBakedModel(
    bakedQuads,
    Direction.entries.associateWith { emptyList() },
    properties.useAmbientOcclusion,
    !properties.flatLight,
    true,
    particle,
    properties.transform.minecraft(),
    ItemOverrides.EMPTY,
) {
    val engineTransform
        get() = properties.transform
    val disableCulling
        get() = properties.disableCulling
    val itemTransform = properties.transform.minecraft()
    val outfitTransform = engineTransform.outfit.minecraft()

    // AR's SimpleBakedModel mixin caches these quads as one mesh per tint layer.
    // The vanilla adapter path lets ItemRenderer reach AR instead of Fabric's emitter.
    override fun isVanillaAdapter(): Boolean = AcceleratedMeshModels.enabled

    // майнкрафт не использует AO для предметов
    override fun useAmbientOcclusion(): Boolean = properties.useAmbientOcclusion

    override fun isGui3d(): Boolean = true

    // объемное освещение
    override fun usesBlockLight(): Boolean = !properties.flatLight

    override fun isCustomRenderer(): Boolean = false

    // вообще нам не нужны частички, но приходится передавать missing-текстуру для сохранения совместимости
    override fun getParticleIcon(): TextureAtlasSprite = particle

    override fun getTransforms(): ItemTransforms {
        return AdditionalTransformationsBank.get(properties.model)?.minecraft() ?: itemTransform
    }

    // нам эта механика не нужна
    override fun getOverrides(): ItemOverrides = ItemOverrides.EMPTY

    override fun emitItemQuads(
        stack: ItemStack,
        randomSupplier: Supplier<RandomSource>,
        context: RenderContext
    ) {
        mesh.outputTo(context.emitter)
    }
}
