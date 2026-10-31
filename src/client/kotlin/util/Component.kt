package org.lain.engine.client.util

import org.lain.engine.client.mc.BulletHit
import org.lain.engine.client.render.player.ArmPoses
import org.lain.engine.client.render.player.DetachedEquipment
import org.lain.engine.client.render.player.EyeLevel
import org.lain.engine.client.render.player.PlayerExtraction
import org.lain.engine.client.render.scene.LightSample
import org.lain.engine.client.render.scene.PropModel
import org.lain.engine.client.render.scene.TransformationEditorTarget
import org.lain.engine.util.ecs.ComponentTypeRegistry

fun ComponentTypeRegistry.registerComponentsClient() {
    registerComponent<BulletHit>()
    registerSceneComponents()
}

fun ComponentTypeRegistry.registerSceneComponents() {
    registerComponent<ArmPoses>()
    registerComponent<EyeLevel>(isSavable = true)
    registerComponent<PlayerExtraction>()
    registerComponent<DetachedEquipment>(isSavable = true)

    registerComponent<LightSample>(isSavable = true)
    registerComponent<PropModel>(isSavable = true)
    registerComponent<TransformationEditorTarget>()
}