package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.server.ServerPlatform
import org.lain.engine.data.PersistentCharacterRecord
import org.lain.engine.data.PersistentCharacterSnapshot
import org.lain.engine.data.PlayerPersistence
import org.lain.engine.data.SavingComponentSnapshot
import org.lain.engine.data.snapshot
import org.lain.engine.player.require
import org.lain.engine.player.set
import org.lain.engine.world.World
import org.lain.engine.world.location

@Serializable
data class CharacterApplyEvent(val character: EngineCharacter, val playerId: PlayerId) : Component

context(world: World)
fun EnginePlayer.unloadCharacter(
    platform: ServerPlatform,
    persistence: PlayerPersistence,
) {
    val character = entity.getComponent<AppliedCharacter>()?.character ?: return
    val snapshot = PersistentCharacterSnapshot(
        character.id,
        entity.requireComponent<SelectedLook>().look.id,
        platform.serializeInventory(this),
        listOfNotNull(
            entity.getComponent<CharacterPhysical>()
        )
            .map { SavingComponentSnapshot(it.snapshot(), componentTypeOf(it)) },
    )
    persistence.saveCharacter(id, snapshot)
}

context(write: WriteComponentAccess)
fun EntityId.setCharacterComponents(
    physical: CharacterPhysical?,
    display: CharacterDisplay,
    look: Look,
    character: EngineCharacter
) {
    physical?.let { setComponent(it) }
    setComponent(display)
    setComponent(SelectedLook(look))
    setComponent(AppliedCharacter(character))
}

fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: PersistentCharacterRecord?,
    platform: ServerPlatform
) {
    val lastLook = persistent?.look?.let { lookId ->
        character.looks.firstOrNull { it.id == lookId }
    }

    with(world) {
        entity.setCharacterComponents(
            if (persistent == null) character.getPhysical() else null,
            character.getDisplay(),
            lastLook ?: character.baseLook,
            character
        )
        persistent?.components?.applyResolved(entity)
    }

    persistent?.items?.let {
        platform.openInventory(this, it)
    }

    val usedCharacters = require<UsedCharacters>()
    usedCharacters.characters += character.id

    world.emitEvent(CharacterApplyEvent(character, id), true)
        .apply { set(location) }

    platform.onCharacterApplied(this, character)
}
