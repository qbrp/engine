package org.lain.engine.client.resources

import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.renderer.block.model.TextureSlots
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.UnbakedGeometry
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.Identifier
import org.lain.engine.client.mc.JsonMc
import org.lain.engine.client.mixin.resource.JsonUnbakedModelAccessor
import org.lain.engine.mc.vanillaId
import org.lain.engine.util.Timestamp
import org.slf4j.LoggerFactory

var MC_LOGGER = LoggerFactory.getLogger("Hacked Minecraft Model Loader")

// Открыть модели для ассетов предметов
fun parseEngineItemModels(
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
                ModelType.JSON -> JsonMc.fromJson(
                    gson,
                    text,
                    BlockModel::class.java
                )
                ModelType.OBJ -> parseObjUnbakedModel(
                    objFiles,
                    asset.source.file.parentFile,
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
        val sprite = Material(ITEMS_ATLAS, textureId)
        id to BlockModel(
            UnbakedGeometry.EMPTY,
            UnbakedModel.GuiLight.FRONT,
            false,
            null,
            TextureSlots.Data.Builder()
                .addTexture("layer0", sprite)
                .build(),
            vanillaId(model.type)
        )
    }.also {
        LOGGER.info("Генерируемые модели предметов созданы за {} мл.", start.timeElapsed())
    }
}
