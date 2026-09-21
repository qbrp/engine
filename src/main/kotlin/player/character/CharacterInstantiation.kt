package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
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

fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: PersistentCharacterRecord?,
    platform: ServerPlatform
) {
    if (persistent == null) {
        set(character.getPhysical())
    }
    val lastLook = persistent?.look?.let { lookId ->
        character.looks.firstOrNull { it.id == lookId }
    }
    set(character.getDisplay())
    set(SelectedLook(lastLook ?: character.baseLook))
    set(AppliedCharacter(character))
    persistent?.items?.let {
        platform.openInventory(this, it)
    }

    val usedCharacters = require<UsedCharacters>()
    usedCharacters.characters += character.id

    world.emitEvent(CharacterApplyEvent(character, id), true)
        .apply { set(location) }

    platform.onCharacterApplied(this, character)
}