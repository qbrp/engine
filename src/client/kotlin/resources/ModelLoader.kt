package org.lain.engine.client.resources

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelState
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.JsonMc
import org.lain.engine.item.UNDEFINED_MODEL_ID
import org.lain.engine.util.Timestamp
import org.slf4j.LoggerFactory
import java.util.function.Function

var MC_LOGGER = LoggerFactory.getLogger("Hacked Minecraft Model Loader")

fun registerEngineModelLoading() {
    ModelLoadingPlugin.register { context ->
        val resources = ClientMixin.getResourceList()
        val models = buildMap {
            putAll(autogenerateModels(resources.generatedItemAssets))
            putAll(parseEngineItemModels(resources.itemModels, resources.objModels))
        }
        val itemDefinitions = parseEngineItemAssets(resources.allItemAssets)
        val modelIds = buildSet {
            add(UNDEFINED_MODEL_ID)
            addAll(models.keys)
            addAll(itemDefinitions.keys)
            addAll(itemDefinitions.values.map { it.model })
        }

        context.addModels(modelIds)
        context.resolveModel().register { resolverContext ->
            val id = resolverContext.id()
            models[id] ?: itemDefinitions[id]
                ?.takeIf { it.model != id }
                ?.let { ReferencedUnbakedModel(it.model) }
        }
        context.modifyModelAfterBake().register { model, bakingContext ->
            val id = bakingContext.resourceId()
            val definition = id?.let(itemDefinitions::get)
            if (id != null && definition != null && model != null) {
                EngineItemModel(
                    id,
                    definition.asset,
                    model,
                    definition.disableCulling,
                    definition.markers,
                    definition.outfitTransformation
                )
            } else {
                model
            }
        }
    }
}

fun parseEngineItemModels(
    models: List<EngineItemJsonModel>,
    objFiles: Map<String, EngineObjModel>
): Map<ResourceLocation, UnbakedModel> {
    val start = Timestamp()
    val parsed = mutableMapOf<ResourceLocation, UnbakedModel>()
    for (model in models) {
        val asset = model.asset
        val id = model.registrationId
        try {
            parsed[id] = when (model.type) {
                ModelType.JSON -> BlockModel.fromString(model.json.toString())
                ModelType.OBJ -> parseObjUnbakedModel(
                    objFiles,
                    asset.source.file.parentFile,
                    model.json
                )
            }
        } catch (e: Throwable) {
            LOGGER.error("Couldn't load Engine model $id", e)
        }
    }
    LOGGER.info("Engine item models loaded in {} ms.", start.timeElapsed())
    return parsed
}

fun autogenerateModels(
    models: List<EngineItemAsset.Generated>,
): Map<ResourceLocation, UnbakedModel> {
    val start = Timestamp()
    return models.associate { model ->
        val id = model.registrationId
        val texture = model.texture.registrationId
        val json = """{
            "parent": "minecraft:item/${model.type}",
            "textures": {
                "layer0": "$texture",
                "particle": "$texture"
            }
        }""".trimIndent()
        id to BlockModel.fromString(json)
    }.also {
        LOGGER.info("Generated Engine item models created in {} ms.", start.timeElapsed())
    }
}

private class ReferencedUnbakedModel(
    private val model: ResourceLocation
) : UnbakedModel {
    override fun getDependencies(): Collection<ResourceLocation> = listOf(model)

    override fun resolveParents(resolver: Function<ResourceLocation, UnbakedModel>) = Unit

    override fun bake(
        baker: ModelBaker,
        textureGetter: Function<Material, TextureAtlasSprite>,
        state: ModelState
    ): BakedModel? = baker.bake(model, state)
}
