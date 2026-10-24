package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerMode
import org.lain.engine.player.PlayerModeComponent
import org.lain.engine.script.ScriptComponent
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.ecs.componentType
import org.lain.engine.world.World

@Serializable
@JvmInline
value class VerbId(val value: String)

data class VerbLookup(
    val input: Set<InputAction>,
    val verbs: MutableSet<Verb> = mutableSetOf()
) : Component {
    companion object {
        val TYPE = componentType<VerbLookup>()
    }
}

fun World.tickVerbLookupStart() {
    iterate<PlayerInput, PlayerInventory, PlayerModeComponent>() { entity, input, inventory, (mode) ->
        val capturedAction = entity.getComponent<UsingInput>()?.input
        val lookupActions = input.actions.filter { it != capturedAction }.toSet()
        if (lookupActions.isNotEmpty() && mode != PlayerMode.SPECTATOR) {
            inventory.mainHandInteractor.setComponent(VerbLookup(lookupActions))
            if (inventory.offHandItem != null) {
                inventory.offHandInteractor.setComponent(VerbLookup(lookupActions))
            }
            entity.setComponent(VerbLookup(lookupActions))
        }
    }
}

data class InteractorVerb(val entity: EntityId, val verb: Verb)

context(world: World)
private fun EntityId.interactionVerb(): InteractorVerb? {
    val lookup = removeComponent<VerbLookup>() ?: return null
    val verb = lookup.verbs.minByOrNull { it.priority } ?: return null
    return InteractorVerb(this, verb)
}

fun World.tickVerbLookupApply() {
    iterate<PlayerComponent, PlayerInventory, PlayerInput>() { _, (player), inventory, input ->
        val handVerb = inventory.mainHandInteractor.interactionVerb()
        val offHandVerb = inventory.offHandInteractor.interactionVerb()
        val playerVerb = player.entity.interactionVerb()

        val (entity, verb) = listOfNotNull(handVerb, offHandVerb, playerVerb)
            .minByOrNull { it.verb.priority }
            ?: return@iterate
        val command = verb.createCommand()
        if (verb.holdsInput != null) {
            player.entity.setComponent(UsingInput(verb.holdsInput))
        }
        val interactionId = InteractionId(player.id, input.tick)
        entity.setComponent(InteractionExecution(interactionId))
        if (command is ScriptComponent) {
            entity.setComponent(command, command.type)
        } else {
            entity.setComponent(command, componentTypeOf(command) as ComponentType<Component>)
        }
    }
}
