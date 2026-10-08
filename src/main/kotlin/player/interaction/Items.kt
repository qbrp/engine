package org.lain.engine.player.interaction

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.WRITEABLE_OPEN_SOUND
import org.lain.engine.item.Writable
import org.lain.engine.item.OpenWritable
import org.lain.engine.item.emitPlaySoundEvent
import org.lain.engine.world.World

val WRITEABLE_OPEN_VERB = VerbId("writable_open")

@Serializable
object OpenWritableCommand : Component {
    val VERB = Verb(
        id = WRITEABLE_OPEN_VERB,
        name = "Открыть для чтения",
        priority = 10,
    ) { OpenWritableCommand }
}

fun World.collectWritableVerbs() {
    iterate<VerbLookup, Hand>() { _, lookup, hand ->
        if (hand.item?.hasComponent<Writable>() == true && InputAction.Base in lookup.input) {
            lookup.verbs += OpenWritableCommand.VERB
        }
    }
}

fun World.tickWritableActionSystem() {
    iterate<OpenWritableCommand, Hand>() { interactor, action, hand ->
        interactor.removeComponent<OpenWritableCommand>()
        val handItem = hand.item ?: return@iterate
        val owner = hand.owner
        owner.emitPlaySoundEvent(WRITEABLE_OPEN_SOUND)
        owner.setComponent(OpenWritable(handItem.getComponent<Writable>() ?: return@iterate))
    }
}
