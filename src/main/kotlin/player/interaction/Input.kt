package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.replication.tickQueuedPlayerInputsSystem
import org.lain.engine.world.World

@Serializable
sealed class InputAction {
    @Serializable
    object Base : InputAction()
    @Serializable
    object Attack : InputAction()
    @Serializable
    object TakeOff : InputAction()
}

data class PlayerInput(
    val actions: MutableSet<InputAction> = mutableSetOf(),
    val lastActions: MutableSet<InputAction> = mutableSetOf(),
    var tick: Long = 0,
    var isSprinting: Boolean = false,
) : Component

const val SOCIAL_INTERACTION_DISTANCE = 4

/** @return Отменить стандартное взаимодействие */
context(world: World)
fun processLeftClickInteraction(player: EnginePlayer, handItem: EngineItem? = player.handItem): Boolean {
    // стрельба
    return handItem?.isGun() == true
}

fun World.tickPlayerInputSystem(callbacks: Callbacks) {
    if (!isClient) {
        tickQueuedPlayerInputsSystem()
    }
    iterate<PlayerInput, PlayerComponent> { _, input, (player) ->
        val (actions, lastActions) = input
        callbacks.of(CallbackType.PLAYER_INPUT_TICK)?.execute(ScriptContext.PlayerInputTick(player, input))

        if (actions != lastActions && !player.isSpectating) {
            input.lastActions.clear()
            input.lastActions.addAll(actions)
        }
    }
}