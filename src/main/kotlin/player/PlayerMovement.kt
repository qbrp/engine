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

/**
 * @param sprintMultiplier Множитель скорости в режиме бега
 * @param minSpeedFactor Множитель к показателю аттрибута скорости, определяющий самую низкую возможную скорость (от 0 до 1)
 * @param slowdownStaminaThreshold Порог стамины, при котором персонаж начинает замедляться
 * @param staminaConsumption Уменьшение стамины за 1 тик при максимальной скорости
 * @param staminaRegen **Постоянное** восстановление стамины за 1 тик. Стамина тратиться, когда `staminaConsumption` становится больше `staminaRegen`
 * @param intentionEffect Как сильно `intention` игрока влияет на скорость. Ограничивает множитель скорости.
 */
@Serializable
data class MovementSettings(
    val sprintMultiplier: Float = 1.5f,
    val minSpeedFactor: Float = 0.05f,
    val slowdownStaminaThreshold: Float = 0.3f,
    val staminaConsumption: Float = 0.0033f,
    val staminaRegen: Float = 0.003f,
    val sprintMinIntentionEffect: Float = 0.5f,
    val intentionEffect: Float = 0.7f
)

fun Player.intentSpeed(value: Float) {
    require<MovementStatus>().intention = value
    markUpdate(PlayerUpdate.SpeedIntention(value))
}

val Player.stamina
    get() = this.require<MovementStatus>().stamina

fun speedMul(
    intention: Float,
    stamina: Float,
    isSprint: Boolean,
    settings: MovementSettings,
): Float = with(settings) {
    val (sprintMul, minSpeedAddition) = if (isSprint) {
        sprintMultiplier to sprintMinIntentionEffect
    } else {
        1f to 0f
    }
    val staminaMul = if (stamina < slowdownStaminaThreshold) smoothstep(stamina / slowdownStaminaThreshold) else 1f

    val minSpeedIntentionMul = (1f - intentionEffect + minSpeedAddition)
    val maxSpeedIntentionMul = (intentionEffect * 2 - 1f)

    val intentionMul = minSpeedIntentionMul + maxSpeedIntentionMul * smootherstep(intention)
    intentionMul * staminaMul * sprintMul
}

fun maxSpeedMul(settings: MovementSettings) = speedMul(1f, 1f, true, settings)

fun updatePlayerMovement(
    player: Player,
    primaryAttributes: MovementDefaultAttributes,
    settings: MovementSettings
) {
    val defaultSpeed = primaryAttributes.getPrimarySeed(player) ?: 0.055f
    val attributes = player.require<PlayerAttributes>()
    val speedAttribute = attributes.speed.default
    val velocityHorizontal = player.velocity.horizontal().length()

    val minSpeed = settings.minSpeedFactor
    val staminaRegen = settings.staminaRegen
    val staminaConsume = settings.staminaConsumption

    attributes.jumpStrength.default = primaryAttributes.getPrimaryJumpStrength(player) ?: 0.55f

    player.apply<MovementStatus> {
        val isSpectating = player.isSpectating

        val minSpeed = defaultSpeed * minSpeed
        val maxSpeed = defaultSpeed * maxSpeedMul(settings)

        attributes.speed.default = if (isSpectating) {
            defaultSpeed
        } else {
            if (!player.isInGameMasterMode && !player.isSpectating) {
                stamina = (stamina + (staminaRegen - abs(velocityHorizontal) / maxSpeed * staminaConsume)).coerceIn(0f, 1f)
            } else {
                stamina = 1f
            }

            val target = max(minSpeed, defaultSpeed * speedMul(intention, stamina, isSprinting, settings))
            lerp(speedAttribute, target, 0.2f)
        }
    }
}