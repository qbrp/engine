package org.lain.engine.client.resources

import com.mojang.serialization.MapCodec
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.SpriteContents
import net.minecraft.client.texture.SpriteDimensions
import net.minecraft.client.texture.atlas.AtlasSource
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.metadata.ResourceMetadata
import net.minecraft.util.Identifier
import org.lain.engine.client.mc.ClientMixinAccess
import org.lain.engine.util.EngineId
import org.lain.engine.util.Timestamp

/**
 * Гарантируется, что текстура из ассета загружена и существует по `textureId`
 * */
data class EngineTexture(val asset: Asset) {
    val registrationId = asset
        .prepareIdentifier()
        .toEngineIdentifier()
}

class EngineAtlasSource(val resources: ResourceList) : AtlasSource {
    override fun load(
        resourceManager: ResourceManager,
        regions: AtlasSource.SpriteRegions
    ) {
        val start = Timestamp()
        resources.textureAssets.forEach { texture ->
            val id = texture.registrationId
            regions.add(id) { openSprite(id, texture.asset) }
        }
        LOGGER.info("Ассеты загружены за {} мл.", start.timeElapsed())
    }

    override fun getCodec(): MapCodec<out AtlasSource> = CODEC

    companion object {
        val CODEC = MapCodec.unit { TODO() }

        fun openSprite(id: Identifier, path: Asset): SpriteContents? {
            val file = path.source.file
            if (!file.exists()) return null
            val input = file.inputStream()
            val nativeImage = NativeImage.read(input)
            val width = nativeImage.width
            val height = nativeImage.height
            val spriteDimensions = SpriteDimensions(width, height)
            return SpriteContents(id, spriteDimensions, nativeImage)
        }
    }
}