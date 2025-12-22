package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.apply

import org.lain.engine.util.Component
import org.lain.engine.util.lerp
import org.lain.engine.util.require
import org.lain.engine.util.smootherstep
import org.lain.engine.util.smoothstep
import org.lain.engine.world.velocity
import kotlin.math.abs
import kotlin.math.max

/**
 * # Движение
 * @param isSprinting Бежит ли игрок. Если да, скорость удваивается
 * @param intention Регулируемый игроком модификатор скорости (от 0 до 1, изначально стоит 0.5)
 * @param stamina Запас энергии на скорость (от 0 до 1). Тратится в зависимости от скорости.
 * Если доходит до 0, игрок не может бежать, скорость минимальная.
 */
@Serializable
data class MovementStatus(
    var isSprinting: Boolean = false,
    var intention: Float = DEFAULT_INTENTION,
    var stamina: Float = DEFAULT_STAMINA,
) : Component {
    companion object {
        const val DEFAULT_INTENTION = 0.5f
        const val DEFAULT_STAMINA = 0.5f
    }
}

const val SPRINT_MODIFIER = 1.5f
const val MIN_SPEED = 0.05f
const val LOW_STAMINA = 0.3f
const val TICK_STAMINA_CONSUME = 0.0033f // стамина на предельной скорости потратится за 15 секунд
// полной скоростью считается 81% от максимальной (0.0027 / 0.0033 = 81%)
const val TICK_STAMINA_REGEN = 0.0030f
const val INTENTION_MOD = 0.7f

fun Player.intentSpeed(value: Float) {
    require<MovementStatus>().intention = value
    markUpdate(PlayerUpdate.SpeedIntention(value))
}

val Player.stamina
    get() = this.require<MovementStatus>().stamina

fun speedMul(
    intention: Float,
    stamina: Float,
    isSprint: Boolean
): Float {
    val sprintMul = if (isSprint) SPRINT_MODIFIER else 1f
    val staminaMul = if (stamina < LOW_STAMINA) smoothstep(stamina / LOW_STAMINA) else 1f

    val minSpeedIntentionMul = (1f - INTENTION_MOD)
    val maxSpeedIntentionMul = (INTENTION_MOD * 2 - 1f)

    val intentionMul = minSpeedIntentionMul + maxSpeedIntentionMul * smootherstep(intention)
    return intentionMul * staminaMul * sprintMul
}

fun maxSpeedMul() = speedMul(1f, 1f, true)

fun updatePlayerMovement(player: Player, primaryAttributes: MovementDefaultAttributes) {
    val primarySpeed = primaryAttributes.getPrimarySeed(player) ?: 0.055f
    val attributes = player.require<PlayerAttributes>()
    val baseSpeed = attributes.speed.default
    val speed = player.velocity.horizontal().length()

    player.apply<MovementStatus> {
        val isSpectating = player.isSpectating

        val minSpeed = primarySpeed * MIN_SPEED
        val maxSpeed = primarySpeed * maxSpeedMul()

        attributes.speed.default = if (isSpectating) {
            primarySpeed
        } else {
            if (!player.isInGameMasterMode && !player.isSpectating) {
                stamina = (stamina + (TICK_STAMINA_REGEN - abs(speed) / maxSpeed * TICK_STAMINA_CONSUME)).coerceIn(0f, 1f)
            } else {
                stamina = 1f
            }

            val target = max(minSpeed, primarySpeed * speedMul(intention, stamina, isSprinting))
            lerp(baseSpeed, target, 0.2f)
        }
    }
}