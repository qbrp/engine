package org.lain.engine.client.util

import org.lain.engine.client.mc.BulletHit
import org.lain.engine.client.mc.render.world.RenderStateComponent
import org.lain.engine.util.component.ComponentWorld

fun ComponentWorld.registerComponentsClient() {
    registerComponent<BulletHit>()
    registerComponent<RenderStateComponent>()
}