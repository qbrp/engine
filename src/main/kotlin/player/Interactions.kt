package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Gun
import org.lain.engine.item.appendGunVerbs
import org.lain.engine.item.appendWriteableVerbs
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.*

data class PlayerInput(
    val actions: MutableSet<InputAction>,
    var lastActions: Set<InputAction>
) : Component

data class ServerPlayerInputMeta(var updatedThisTick: Boolean, ) : Component

val EnginePlayer.input
    get() = this.require<PlayerInput>().actions

sealed class InputAction {
    object Base : InputAction()
    object Attack : InputAction()
    data class SlotClick(
        val cursorItem: EngineItem,
        val item: EngineItem
    ) : InputAction()
}

data class VerbLookup(
    val handItem: EngineItem?,
    val actions: Set<InputAction>,
    val verbs: MutableSet<VerbVariant>
) : Component {
    val slotClick
        get() = actions.find { it is InputAction.SlotClick } as InputAction.SlotClick?
}

data class VerbVariant(
    val verb: VerbType,
    val action: InputAction,
) : Component {}

data class InteractionComponent(
    val type: VerbType,
    val handItem: EngineItem?,
    val action: InputAction,
    var timeElapsed: Int = 0,
    var occupied: Boolean = false,
) : Component {
    fun occupy() {
        occupied = true
    }

    val slotAction get() = action as InputAction.SlotClick

    val isFinished: Boolean
        get() = timeElapsed > type.time
}

fun EnginePlayer.handleInteraction(id: VerbId, statement: InteractionComponent.() -> Unit) {
    handle<InteractionComponent> {
        if (type.id == id) {
            statement(this)
        }
    }
}

fun EnginePlayer.finishInteraction() {
    this.remove<InteractionComponent>()
}

data class VerbType(
    val id: VerbId,
    val name: String,
    val time: Int, // в тиках,
    val target: Target,
    val priority: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        return other is VerbType && other.id == id
    }

    @Serializable
    enum class Target {
        PLAYER, ITEM
    }
}

@Serializable
@JvmInline
value class VerbId(val value: String)

/** @return Отменить стандартное взаимодействие */
fun processLeftClickInteraction(player: EnginePlayer, handItem: EngineItem? = player.handItem): Boolean {
    // стрельба
    return handItem?.has<Gun>() == true
}

fun ItemVerb(
    id: VerbId,
    name: String,
    time: Int = 0,
) = VerbType(id, name, time, VerbType.Target.ITEM)

fun appendVerbs(player: EnginePlayer) {
    appendWriteableVerbs(player)
    appendGunVerbs(player)
    appendPlayerInventoryVerbs(player)
}

fun updatePlayerVerbLookup(
    player: EnginePlayer,
    lookup: Boolean = true,
    input: PlayerInput = player.require<PlayerInput>()
) {
    val actions = input.actions
    val lastActions = input.lastActions.toSet()
    if (actions != lastActions) {
        if (lookup) {
            player.set(VerbLookup(player.handItem, actions, mutableSetOf()))
        }
        input.lastActions = actions.toSet()
    }
}

fun finishPlayerInteraction(player: EnginePlayer) {
    val interaction = player.get<InteractionComponent>()
    val input = player.require<PlayerInput>()
    val actions = input.actions

    if (interaction != null) {
        val item = interaction.handItem
        val actionSimilar = interaction.action in actions
        val itemsSimilar = item == null || item.uuid == player.handItem?.uuid
        if (!actionSimilar || !itemsSimilar) {
            player.removeComponent(interaction)
        }

        interaction.timeElapsed++
    }
}

fun updatePlayerInteractions(player: EnginePlayer, handler: ServerHandler? = null) {
    val handItem = player.handItem
    val interaction = player.get<InteractionComponent>()
    val lookup = player.get<VerbLookup>()

    val invoke = lookup?.verbs?.minByOrNull { it.verb.priority }
    player.remove<VerbLookup>()
    if (interaction == null && invoke != null) {
        val component = InteractionComponent(
            invoke.verb,
            if (invoke.verb.target == VerbType.Target.ITEM) {
                handItem
            } else {
                null
            },
            invoke.action,
            0
        )
        player.set(component)
        handler?.onPlayerInteraction(player, component)
    }
}