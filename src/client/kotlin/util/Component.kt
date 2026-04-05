package org.lain.engine.client.util

import org.lain.engine.client.mc.BulletHit
import org.lain.engine.client.mc.render.world.RenderStateComponent
import org.lain.engine.util.component.ComponentTypeRegistry

fun ComponentTypeRegistry.registerComponentsClient() {
    registerComponent<BulletHit>()
    registerComponent<RenderStateComponent>()
}