package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
import org.lain.engine.world.World

@Serializable
sealed class InputAction {
    object Base : InputAction()
    object Attack : InputAction()
    object TakeOff : InputAction()
}

data class PlayerInput(
    val actions: MutableSet<InputAction> = mutableSetOf(),
    val lastActions: MutableSet<InputAction> = mutableSetOf(),
    var action: Action? = null,
    var tick: Long = 0
) : Component

const val SOCIAL_INTERACTION_DISTANCE = 15

interface Action : Component

/** @return Отменить стандартное взаимодействие */
context(world: World)
fun processLeftClickInteraction(player: EnginePlayer, handItem: EngineItem? = player.handItem): Boolean {
    // стрельба
    return handItem?.isGun() == true
}

fun World.tickPlayerInput(playerId: PlayerId? = null, clientSide: Boolean = false) {
    iterate<PlayerInput, Player> { entity, input, (player) ->
        val (actions, lastActions) = input
        if (playerId != null && player.id != playerId) {
            return@iterate
        }

        if (actions != lastActions && !player.isSpectating) {
            input.lastActions.clear()
            input.lastActions.addAll(actions)
            val sightPlayer = player.whoSee(SOCIAL_INTERACTION_DISTANCE)
            val handItem = player.handItem
            val extendArm = player.extendArm
            val gun = handItem?.getComponent<Gun>()

            actions.forAction<InputAction.Attack> { action ->
                val gunSafety = gun?.mode == FireMode.SELECTOR

                // Первым делом - боевые взаимодействия
                if (gun != null && !gunSafety) {
                    input.action = StartShootAction
                    return@forAction
                }

                if (sightPlayer != null) {
                    if (!clientSide) {
                        // Последним делом - социальные взаимодействия
                        input.action = HailAction(sightPlayer.id)
                        if (handItem != null && extendArm) {
                            input.action = GiveAction(sightPlayer.id)
                        }
                    }
                }
            }

            actions.forAction<InputAction.Base>() { action ->
                val writable = handItem?.getComponent<Writable>()

                // Идём списочком по доступным действиям
                if (gun != null) {
                    input.action = GunModeToggleAction
                } else if (writable != null) {
                    input.action = WritableOpenAction
                }
            }
        }

        val intent = input.action ?: return@iterate
        EngineLogger.log(
            Log(
                LogMessages.PLAYER_INTERACTION,
                LogLevel.INFO,
                mapOf(
                    "action" to intent.toString(),
                    "input" to actions.joinToString()
                ),
                world = this@tickPlayerInput.id,
                tick = ticks.toULong()
            )
        )
        entity.setComponent<Action>(intent, componentTypeOfGeneral(intent) as ComponentType<Action>)
        input.action = null
    }
}
