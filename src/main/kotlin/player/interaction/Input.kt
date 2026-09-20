package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.tickQueuedPlayerInputsSystem
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
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
    var action: Component? = null,
    var tick: Long = 0,
    var isSprinting: Boolean = false
) : Component

const val SOCIAL_INTERACTION_DISTANCE = 4

/** @return Отменить стандартное взаимодействие */
context(world: World)
fun processLeftClickInteraction(player: EnginePlayer, handItem: EngineItem? = player.handItem): Boolean {
    // стрельба
    return handItem?.isGun() == true
}

sealed interface PlayerInputMode {
    data object Authoritative : PlayerInputMode

    data class Predictive(
        val controlledPlayerIds: Set<PlayerId>,
        val predictionSink: PredictionSink,
    ) : PlayerInputMode
}

fun interface PredictionSink {
    fun begin(world: World, entity: EntityId, interactionId: InteractionId)
}

class PlayerInputSystem(
    private val mode: PlayerInputMode,
) {
    fun tick(world: World, callbacks: Callbacks) {
        if (mode is PlayerInputMode.Authoritative) {
            world.tickQueuedPlayerInputsSystem()
        }
        world.iterate<PlayerInput, PlayerComponent> { entity, input, (player) ->
            val (actions, lastActions) = input
            if (mode is PlayerInputMode.Predictive && !mode.controlledPlayerIds.contains(player.id)) {
                return@iterate
            }

            callbacks.of(CallbackType.PLAYER_INPUT_TICK)?.execute(ScriptContext.PlayerInputTick(player, input))

            run {
                if (actions != lastActions && !player.isSpectating) {
                    input.lastActions.clear()
                    input.lastActions.addAll(actions)
                    val sightPlayer = player.whoSee(SOCIAL_INTERACTION_DISTANCE)
                    val playerInventory = player.require<PlayerInventory>()
                    val mainHandItem = playerInventory.mainHandItem
                    val offHandItem = playerInventory.offHandItem
                    val extendArm = player.extendArm
                    val gun = mainHandItem?.getComponent<Gun>()
                    val gunFireState = mainHandItem?.getComponent<GunFireState>()
                    val barrel = mainHandItem?.getComponent<Barrel>()

                    if (entity.hasComponent<Shooting>() && (gun == null || InputAction.Attack !in actions)) {
                        entity.setComponent(StopShootAction)
                        return@run
                    }

                    actions.forAction<InputAction.Attack> { action ->
                        val gunSafety = gunFireState?.mode == FireMode.SELECTOR

                        // Первым делом - боевые взаимодействия
                        if (gun != null && gunSafety == false) {
                            input.action = StartShootAction
                            return@forAction
                        }

                        if (sightPlayer != null) {
                            // Последним делом - социальные взаимодействия
                            input.action = HailAction
                        }
                    }

                    actions.forAction<InputAction.Base>() { action ->
                        val writable = mainHandItem?.getComponent<Writable>()
                        val magazine = mainHandItem?.getComponent<Magazine>()
                        val gunBarrelSupportsDirectAmmoLoad = barrel?.ammunition == (offHandItem?.getComponent<Item>()?.id ?: false)
                                && !mainHandItem.hasComponent<GunMagazines>()

                        // Идём списочком по доступным действиям
                        val give = mainHandItem != null && sightPlayer != null && extendArm
                        if (give) {
                            input.action = GiveAction
                        } else if (gun != null) {
                            if (offHandItem != null && (gunBarrelSupportsDirectAmmoLoad || offHandItem.hasComponent<Magazine>())) {
                                input.action = GunLoadAction(mainHandItem, offHandItem)
                            } else {
                                input.action = GunModeToggleAction
                            }
                        } else if (magazine != null && magazine.ammunition == offHandItem?.getComponent<Item>()?.id) {
                            input.action = MagazineLoadAction(offHandItem)
                        } else if (writable != null) {
                            input.action = WritableOpenAction
                        }
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
                    world = world.id,
                    tick = world.simulation.ticks
                )
            )
            val execution = ActionExecution(
                InteractionId(player.id, input.tick),
            )
            entity.setComponent(execution)
            if (mode is PlayerInputMode.Predictive) {
                mode.predictionSink.begin(world, entity, execution.interactionId)
            }
            entity.setComponent(intent, componentTypeOf(intent) as ComponentType<Component>)
            input.action = null
        }
    }
}
