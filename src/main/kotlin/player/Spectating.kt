package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.has
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.lain.engine.util.set

object StartSpectatingMark : Component

object SpawnMark : Component

/**
 * # Наблюдение
 * Значение `isSpectating` устанавливается на стороне Minecraft исходя из режима игры.
 * Управление и вызов режима спектатора производится через компоненты `SpawnMark` и `StartSpectatingMark`
 * **Режим спектатора, в отличие от ГМа, доступен только в одном режиме игры.**
 */

@Serializable
data class Spectating(var isSpectating: Boolean = false) : Component

fun Player.startSpectating() {
    this.set(StartSpectatingMark)
}

fun Player.stopSpectating() {
    this.set(SpawnMark)
}

var Player.isSpectating
    get() = this.require<Spectating>().isSpectating
    set(value) { this.require<Spectating>().isSpectating = value }