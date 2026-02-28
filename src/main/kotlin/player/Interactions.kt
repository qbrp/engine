package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.item.*
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.*
import org.lain.engine.world.SoundContext
import kotlin.reflect.KClass

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
    var raycastPlayer: EnginePlayer? = null

    fun raycastPlayer(player: EnginePlayer, distance: Int): EnginePlayer? {
        raycastPlayer = player.whoSee(distance)
        return raycastPlayer
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : InputAction> forAction(
        actionClass: KClass<T>,
        override: Boolean = false,
        statement: (T) -> VerbType?
    ) {
        for (action in actions) {
            if (actionClass.isInstance(action)) {
                if (!override && verbs.any { it.action == action }) continue
                val verbType = statement(action as T) ?: continue
                verbs += VerbVariant(verbType, action)
            }
        }
    }

    inline fun <reified T : InputAction> forAction(
        override: Boolean = false,
        noinline statement: (T) -> VerbType?,
    ) {
        forAction(T::class, override, statement)
    }

    inline fun <reified T : InputAction> forAction(
        verbType: VerbType,
        override: Boolean = false,
    ) {
        forAction<T>(override) { verbType }
    }
}

data class VerbVariant(
    val verb: VerbType,
    val action: InputAction,
) : Component

data class ProgressionType(
    val duration: Int,
    val animation: ProgressionAnimation
)

data class ProgressionAnimation(
    val frames: List<String>,
    val progressionText: String,
    val successText: String
) {
    companion object {
        val DEFAULT = ProgressionAnimation(
            listOf(),
            "Выполнение действия...",
            "Действие выполнено"
        )
    }
}

@JvmInline
@Serializable
value class ProgressionAnimationId(val value: String) {
    override fun toString() = value
}

data class InteractionComponent(
    val id: InteractionId,
    val type: VerbType,
    val handItem: EngineItem?,
    val raycastPlayer: EnginePlayer?,
    val action: InputAction,
    var occupied: Boolean = false,
    var timeElapsed: Int = 0
) : Component {
    var sounds = 0
    var progression: ProgressionType? = null
    val progress: Float
        get() = (timeElapsed.toFloat() / (this.progression?.duration ?: error("Прогрессия не начата"))).coerceIn(0f, 1f)
    val progressionFinished
        get() = progress >= 1f
    var text: String? = null

    val slotAction
        get() = action as InputAction.SlotClick

    context(contents: ContentStorage)
    fun attachProgression(
        id: ProgressionAnimationId?,
        duration: Int
    ) {
        if (progression != null) return
        val animation = contents.progressionAnimations[id] ?: ProgressionAnimation.DEFAULT
        progression = ProgressionType(duration, animation)
    }

    context(contents: ContentStorage)
    fun attachHandItemProgression(
        key: String,
        duration: Int
    ) {
        attachProgression(handItem?.get<ItemProgressionAnimations>()?.animations[key], duration)
    }

    fun failureProgression(text: String) {
        this.text = text
    }

    fun occupy() {
        occupied = true
    }

    fun emitItemInteractionSoundEvent(item: EngineItem, key: String, player: EnginePlayer? = null) {
        item.emitPlaySoundEvent(key, player = player, context = SoundContext(sounds++, id))
    }
}

fun EnginePlayer.handleInteraction(verb: VerbType, statement: InteractionComponent.() -> Unit) {
    handle<InteractionComponent> {
        if (type.id == verb.id) {
            statement(this)
        }
    }
}

fun EnginePlayer.completeInteraction() {
    this.remove<InteractionComponent>()
}

fun EnginePlayer.completeInteractionIfFinished() = this.handle<InteractionComponent>() {
    val progression = progression
    if (progression != null && timeElapsed <= progression.duration) return@handle
    completeInteraction()
}

@Serializable
data class VerbType(
    val id: VerbId,
    val name: String,
    val priority: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        return other is VerbType && other.id == id
    }
}

fun VerbType(id: String, name: String, priority: Int = 0) = VerbType(VerbId(id), name, priority)

@Serializable
@JvmInline
value class VerbId(val value: String)

@Serializable
@JvmInline
value class InteractionId(val value: Long) {
    companion object {
        fun next(): InteractionId = InteractionId(nextId())
    }
}

/** @return Отменить стандартное взаимодействие */
fun processLeftClickInteraction(player: EnginePlayer, handItem: EngineItem? = player.handItem): Boolean {
    // стрельба
    return handItem?.has<Gun>() == true
}

fun appendVerbs(player: EnginePlayer) {
    appendWriteableVerbs(player)
    appendGunVerbs(player)
    appendPlayerInventoryVerbs(player)
    appendPlayerEquipmentVerbs(player)
    appendSocialVerbs(player)
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
        if (!actionSimilar || !itemsSimilar || (!interaction.occupied && interaction.progression == null)) {
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
            InteractionId.next(),
            invoke.verb,
            handItem,
            lookup.raycastPlayer,
            invoke.action
        )
        player.set(component)
        handler?.onPlayerInteraction(player, component)
    }
}