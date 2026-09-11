package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component

/**
 * # Движение
 * @param intention Регулируемый игроком модификатор скорости (от 0 до 1, изначально стоит 0.5)
 * @param stamina Запас энергии на скорость (от 0 до 1). Тратится в зависимости от скорости.
 * Если доходит до 0, игрок не может бежать, скорость минимальная.
 */
@Serializable
data class MovementStatus(
    var intention: Float = DEFAULT_INTENTION,
    var stamina: Float = DEFAULT_STAMINA,
) : Component {
    companion object {
        const val DEFAULT_INTENTION = 0.5f
        const val DEFAULT_STAMINA = 1f
    }
}

fun EnginePlayer.intentSpeed(value: Float) {
    require<MovementStatus>().intention = value
    markUpdated<MovementStatus>()
}

val EnginePlayer.stamina
    get() = this.require<MovementStatus>().stamina
