package org.lain.engine.client.resources

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.serialization.MapCodec
import kotlinx.serialization.Serializable
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.renderer.texture.atlas.SpriteSource
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceMetadata
import net.minecraft.server.packs.resources.ResourceManager
import org.lain.engine.util.Timestamp
import java.io.File

/**
 * Правила загрузки атласов - по каким директорием в какие атласы загружать текстуры.
 */
@Serializable
class SpriteAtlasRules private constructor(
    val default: String = "blocks",
    val directories: Map<String, String> = mapOf()
) {
    fun resolve(asset: Asset): String = directories[asset.fileRelative.toPath().firstOrNull()?.toString()] ?: default

    companion object {
        fun empty() = SpriteAtlasRules()

        fun create(directories: Map<String, String>) = SpriteAtlasRules(
            directories = directories.mapValues { (_, atlasName) -> legacyAtlasName(atlasName) }
        )
    }
}

interface TextureView {
    val registrationId: ResourceLocation

    fun open(): SpriteContents
}

class AssetTextureView(private val asset: Asset) : TextureView {
    override val registrationId: ResourceLocation
        get() = asset.registrationId

    override fun open(): SpriteContents {
        val imageFile = asset.source.file
        if (!imageFile.isFile) error("чет не так")

        val metadata = loadMetadata(imageFile)
        val animationMetadata = metadata
            .getSection(AnimationMetadataSection.SERIALIZER)
            .orElse(AnimationMetadataSection.EMPTY)!!
        val nativeImage = imageFile.inputStream().use(NativeImage::read)
        val spriteDimensions = animationMetadata.calculateFrameSize(nativeImage.width, nativeImage.height)

        return try {
            SpriteContents(
                registrationId,
                spriteDimensions,
                nativeImage,
                metadata,
            )
        } catch (exception: Throwable) {
            nativeImage.close()
            throw exception
        }
    }

    private fun loadMetadata(imageFile: File): ResourceMetadata {
        val metadataFile = imageFile.resolveSibling("${imageFile.name}.mcmeta")
        return if (metadataFile.isFile) {
            metadataFile.inputStream().use(ResourceMetadata::fromJsonStream)
        } else {
            ResourceMetadata.EMPTY
        }
    }
}

class EngineAtlasSource(
    val textures: Collection<TextureView>,
) : SpriteSource {
    override fun run(
        resourceManager: ResourceManager,
        output: SpriteSource.Output
    ) {
        val start = Timestamp()
        textures.forEach { texture ->
            val id = texture.registrationId
            output.add(id) { texture.open() }
        }
        LOGGER.info("Текстуры загружены за {} мл.", start.timeElapsed())
    }

    override fun type(): SpriteSourceType = TYPE

    companion object {
        // Этот источник создаётся непосредственно в рантайме и нужен только для кастомных текстур Engine
        // Не поддерживает JSON
        val CODEC: MapCodec<Nothing> = MapCodec.unit { throw NotImplementedError() }
        val TYPE = SpriteSourceType(CODEC)
    }
}

fun legacyAtlasName(name: String): String = if (name == "items") "blocks" else name