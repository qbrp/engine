package org.lain.engine.client.util

import org.lain.engine.client.handler.LowDetail
import org.lain.engine.client.mc.BulletHit
import org.lain.engine.client.mc.compat.LamdLightSource
import org.lain.engine.client.render.item.CompiledItemTooltip
import org.lain.engine.client.render.player.EnginePlayerSkin
import org.lain.engine.client.render.player.PlayerExtraction
import org.lain.engine.client.render.player.PlayerRenderNode
import org.lain.engine.client.render.scene.LightSample
import org.lain.engine.client.render.scene.PropModel
import org.lain.engine.client.render.scene.TransformationEditorTarget
import org.lain.engine.util.ecs.ComponentTypeRegistry

fun ComponentTypeRegistry.registerComponentsClient() {
    registerComponent<BulletHit>(id = "core/client/event/bullet_hit")
    registerSceneComponents()
    registerComponent<LowDetail>(id = "core/client/player/low_detail")
    registerComponent<LamdLightSource>(id = "core/client/minecraft/dynamic_light")
    registerComponent<CompiledItemTooltip>(id = "core/client/item/compiled_tooltip")
    registerComponent<EnginePlayerSkin>(id = "core/client/player/skin")
}

fun ComponentTypeRegistry.registerSceneComponents() {
    registerComponent<PlayerExtraction>(id = "core/client/player/extraction")
    registerComponent<PlayerRenderNode>(id = "core/client/player/render_node", isSavable = true)

    registerComponent<LightSample>(id = "core/client/render/light_sample", isSavable = true)
    registerComponent<PropModel>(id = "core/client/render/prop_model", isSavable = true)
    registerComponent<TransformationEditorTarget>(id = "core/client/render/transformation_editor_target")
}
