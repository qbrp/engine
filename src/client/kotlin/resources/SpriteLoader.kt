package org.lain.engine.client.resources

import com.mojang.serialization.MapCodec
import kotlinx.serialization.Serializable
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.SpriteContents
import net.minecraft.client.texture.SpriteDimensions
import net.minecraft.client.texture.atlas.AtlasSource
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import org.lain.engine.util.Timestamp

/**
 * Гарантируется, что текстура из ассета загружена и существует по `textureId`
 * */
data class EngineTexture(val asset: Asset) {
    val registrationId = asset
        .prepareIdentifier()
        .toEngineIdentifier()
}

/**
 * Правила загрузки атласов - по каким директорием в какие атласы загружать текстуры.
 */
@Serializable
data class SpriteAtlasRules(
    val default: String = "blocks",
    val directories: Map<String, String> = mapOf()
)

class EngineAtlasSource(val textures: List<EngineTexture>) : AtlasSource {
    override fun load(
        resourceManager: ResourceManager,
        regions: AtlasSource.SpriteRegions
    ) {
        val start = Timestamp()
        textures.forEach { texture ->
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
            input.close()
            return SpriteContents(id, spriteDimensions, nativeImage)
        }
    }
}