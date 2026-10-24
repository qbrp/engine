package org.lain.engine.client.resources

import net.minecraft.client.resources.model.Material
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.mc.missingSpriteId
import org.lain.engine.mc.engineId
import org.lain.engine.script.EngineId
import org.lain.engine.script.EngineIdentifierException
import org.lain.engine.script.Module
import org.lain.engine.script.resolveId
import org.slf4j.LoggerFactory
import java.io.File

class AssetCatalog(
    val source: SourceFile,
    val module: Module
) {
    val directory = source.file.resolve("assets")
    val spriteAtlases =
        source.resolve("atlases.yml")?.yaml<SpriteAtlasRules>() ?: SpriteAtlasRules.empty()
    val assetsModule = AssetModule(module, spriteAtlases)

    fun browse(block: (Asset) -> Unit) {
        if (!directory.isDirectory) return

        directory.walk().forEach { file ->
            if (!file.isFile) return@forEach
            val relative = file.relativeTo(directory)
            val relativeParentPath = relative.parentFile
                ?.path
                ?.normalizeSlashes()
                .orEmpty()

            val namespace = if (relativeParentPath.isEmpty()) {
                module.namespace
            } else {
                module.namespace.append(relativeParentPath)
            }
            val local = file.nameWithoutExtension

            val engineId = try {
                EngineId.of(namespace, local)
            } catch (e: EngineIdentifierException) {
                LOGGER.error("Ассет ${file.path} имеет неправильное название: ${e.message}")
                return@forEach
            }

            val asset = Asset(engineId, assetsModule, relative, file.toSourceFile())
            block(asset)
        }
    }
}

data class Asset(
    val id: EngineId,
    val assetsModule: AssetModule,
    val fileRelative: File,
    val source: SourceFile
) {
    fun resolve(reference: String): EngineId =
        if (reference.startsWith("~/")) {
            EngineId(
                "${
                    id.namespace
                }/${reference.removePrefix("~/")}"
            )
        } else {
            assetsModule.module.resolveId(reference)
        }
}

fun String.normalizeSlashes() = replace('\\', '/')

val Asset.registrationId: ResourceLocation
    get() = id.toMinecraftId()

fun EngineId.toMinecraftId(): ResourceLocation = engineId(full)

internal val LOGGER = LoggerFactory.getLogger("Engine Model Loader")
internal val MISSING_SPRITE = Material(ITEMS_ATLAS, missingSpriteId)
