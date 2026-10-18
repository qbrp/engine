package org.lain.engine.client.resources

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.item.UNDEFINED_MODEL_ID
import org.slf4j.LoggerFactory

var MC_LOGGER = LoggerFactory.getLogger("Hacked Minecraft Model Loader")

fun registerEngineModelLoading() {
    ModelLoadingPlugin.register { context ->
        val assets = ClientMixin.indexedAssets
        val modelCatalog = assets.catalogizeModels()
        val models = modelCatalog.props.entries.associate { (_, propModelProperties) ->
            propModelProperties.asset.registrationId to UnbakedMeshModel(propModelProperties)
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
