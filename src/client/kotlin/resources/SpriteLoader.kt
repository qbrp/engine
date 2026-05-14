package org.lain.engine.client.resources

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.serialization.MapCodec
import kotlinx.serialization.Serializable
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.renderer.texture.atlas.SpriteSource
import net.minecraft.client.resources.metadata.animation.FrameSize
import net.minecraft.resources.Identifier
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
    val default: String = "items",
    val directories: Map<String, String> = mapOf()
)

class EngineAtlasSource(val textures: List<EngineTexture>) : SpriteSource {
    override fun run(
        resourceManager: net.minecraft.server.packs.resources.ResourceManager,
        output: SpriteSource.Output
    ) {
        val start = Timestamp()
        textures.forEach { texture ->
            val id = texture.registrationId
            output.add(id) { openSprite(id, texture.asset) }
        }
        LOGGER.info("Ассеты загружены за {} мл.", start.timeElapsed())
    }

    override fun codec(): MapCodec<out SpriteSource> = CODEC

    companion object {
        // Этот источник создаётся непосредственно в рантайме и нужен только для кастомных текстур Engine
        // Не поддерживает JSON
        val CODEC = MapCodec.unit { throw NotImplementedError() }

        fun openSprite(id: Identifier, path: Asset): SpriteContents? {
            val file = path.source.file
            if (!file.exists()) return null
            val input = file.inputStream()
            val nativeImage = NativeImage.read(input)
            val width = nativeImage.width
            val height = nativeImage.height
            val spriteDimensions = FrameSize(width, height)
            input.close()
            return SpriteContents(id, spriteDimensions, nativeImage)
        }
    }
}