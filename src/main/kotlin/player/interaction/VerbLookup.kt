package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World

@Serializable
@JvmInline
value class VerbId(val value: String)

@Serializable
data class VerbType(val id: VerbId, val name: String)

fun VerbType(id: String, name: String) = VerbType(VerbId(id), name)

data class VerbLookup(
    val input: Set<InputAction>,
    val verbs: MutableSet<Verb> = mutableSetOf()
) : Component

fun World.tickVerbLookupStart() {
    iterate<PlayerInput, PlayerInventory>() { entity, input, inventory ->
        val capturedAction = entity.getComponent<UsingInput>()?.input
        val lookupActions = input.actions.filter { it != capturedAction }.toSet()
        if (lookupActions.isNotEmpty()) {
            inventory.mainHandInteractor.setComponent(VerbLookup(lookupActions))
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
        val playerVerb = player.entity.interactionVerb()

        val (entity, verb) = listOfNotNull(handVerb, playerVerb)
            .minByOrNull { it.verb.priority }
            ?: return@iterate
        val command = verb.createCommand()
        if (verb.holdsInput != null) {
            player.entity.setComponent(UsingInput(verb.holdsInput))
        }
        val interactionId = InteractionId(player.id, input.tick)
        entity.setComponent(InteractionExecution(interactionId))
        entity.setComponent(command, componentTypeOf(command) as ComponentType<Component>)
    }
}