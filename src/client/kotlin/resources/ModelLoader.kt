package org.lain.engine.client.resources

import net.minecraft.client.render.model.ModelTextures
import net.minecraft.client.render.model.UnbakedModel
import net.minecraft.client.render.model.json.JsonUnbakedModel
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.client.util.SpriteIdentifier
import net.minecraft.util.Identifier
import net.minecraft.util.JsonHelper
import org.lain.engine.client.mixin.resource.JsonUnbakedModelAccessor
import org.lain.engine.util.Timestamp
import org.slf4j.LoggerFactory

var MC_LOGGER = LoggerFactory.getLogger("Hacked Minecraft Model Loader")

// Открыть модели для ассетов предметов
fun parseEngineModels(
    models: List<EngineItemJsonModel>,
    objFiles: Map<String, EngineObjModel>
): Map<Identifier, UnbakedModel> {
    val start = Timestamp()
    val map = mutableMapOf<Identifier, UnbakedModel>()
    val gson = JsonUnbakedModelAccessor.`engine$getGson`()
    for (model in models) {
        val asset = model.asset
        val text = model.json.toString()
        val id = model.registrationId
        try {
            val unbakedModel = when(model.type){
                ModelType.JSON -> JsonHelper.deserialize(
                    gson,
                    text,
                    JsonUnbakedModel::class.java
                )
                ModelType.OBJ -> parseObjUnbakedModel(
                    objFiles,
                    asset.source.file.parentFile,
                    id,
                    model.json,
                )
            }
            map[id] = unbakedModel
        } catch (e: Throwable) {
            LOGGER.error("При загрузке модели $id возникла ошибка", e)
        }
    }
    LOGGER.info("Модели предметов загружены за {} мл.", start.timeElapsed())
    return map
}

// Генерировать модели для ассетов предметов
fun autogenerateModels(
    models: List<EngineItemAsset.Generated>,
): Map<Identifier, UnbakedModel> {
    val start = Timestamp()
    return models.associate { model ->
        val id = model.registrationId
        val textureId = model.texture.registrationId
        val sprite = SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, textureId)
        id to JsonUnbakedModel(
            Identifier.ofVanilla(model.type),
            listOf(),
            ModelTextures.Textures.Builder()
                .addSprite("layer0", sprite)
                .build(),
            null,
            null,
            null
        )
    }.also {
        LOGGER.info("Генерируемые модели предметов созданы за {} мл.", start.timeElapsed())
    }
}
