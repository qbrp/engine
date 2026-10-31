package org.lain.engine.client.render.item

import org.lain.cyberia.ecs.getComponent
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.scene.PropModelReference
import org.lain.engine.client.render.scene.ResourcePath
import org.lain.engine.client.resources.MeshModel
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.UNDEFINED_MODEL
import org.lain.engine.mc.engineId
import org.lain.engine.world.World

context(world: World)
fun EngineItem.extractModel(): ResourcePath {
    val assetsComponent = getComponent<ItemAssets>()
    val propModelId = assetsComponent?.default?.full
        ?: assetsComponent?.assets?.entries?.firstOrNull()?.value?.full
    return propModelId ?: UNDEFINED_MODEL
}

context(world: World)
fun EngineItem.extractModelReference(): PropModelReference {
    return PropModelReference(extractModel())
}