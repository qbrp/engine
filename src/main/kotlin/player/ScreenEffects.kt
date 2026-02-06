package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.remove
import org.lain.engine.util.set

// Обрабатывать исключительно на клиенте

data class ShakeScreenComponent(val stress: Float) : Component

fun EnginePlayer.shake(stress: Float) {
    val component = this.get<ShakeScreenComponent>()
    if (component != null) {
        this.removeComponent(component)
        this.set(ShakeScreenComponent(component.stress + stress))
    } else {
        this.set(ShakeScreenComponent(stress))
    }
}