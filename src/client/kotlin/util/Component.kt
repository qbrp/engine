package org.lain.engine.client.util

import org.lain.engine.client.mc.BulletHit
import org.lain.engine.client.render.player.RenderStateComponent
import org.lain.engine.util.ecs.ComponentTypeRegistry

fun ComponentTypeRegistry.registerComponentsClient() {
    registerComponent<BulletHit>()
    registerComponent<RenderStateComponent>()
}