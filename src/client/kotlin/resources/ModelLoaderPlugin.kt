package org.lain.engine.client.resources

import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.item.UNDEFINED_MODEL_ID
import org.slf4j.LoggerFactory

fun registerEngineModelLoading(client: EngineClient) {
    ModelLoadingPlugin.register { context ->
        val modelCatalog = runBlocking { client.assetCompilation?.modelCatalog?.await() }
            ?: return@register
        val models = modelCatalog.props.entries.associate { (_, propModel) ->
            val properties = propModel.properties
            properties.asset.registrationId to UnbakedMeshModel(properties, propModel.geometry)
        }
        val modelIds = buildSet {
            add(UNDEFINED_MODEL_ID)
            addAll(models.keys)
        }

        context.addModels(modelIds)
        context.resolveModel().register { resolverContext ->
            models[resolverContext.id()]
        }
    }
}
