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
import org.lain.engine.data.CharacterData
import org.lain.engine.data.PersistentCharacterSnapshot
import org.lain.engine.data.PlayerPersistence
import org.lain.engine.data.SavingComponentSnapshot
import org.lain.engine.data.SerializedInventory
import org.lain.engine.data.persistentId
import org.lain.engine.data.snapshot
import org.lain.engine.player.Equipment
import org.lain.engine.player.equipment
import org.lain.engine.player.require
import org.lain.engine.player.set
import org.lain.engine.world.location

@Serializable
data class CharacterApplyEvent(val character: EngineCharacter, val playerId: PlayerId) : Component

fun EnginePlayer.unloadCharacter(
    platform: ServerPlatform,
    persistence: PlayerPersistence,
    serializedInventory: SerializedInventory? = null
) = with(world) {
    val character = entity.getComponent<AppliedCharacter>()?.character ?: return@with
    val snapshot = PersistentCharacterSnapshot(
        character.id,
        entity.requireComponent<SelectedLook>().look.id,
        serializedInventory ?: platform.serializeInventory(this@unloadCharacter),
        equipment.mapValues { (_, entity) -> entity.persistentId() },
        listOfNotNull(
            entity.getComponent<CharacterPhysical>()
        )
            .map { SavingComponentSnapshot(it.snapshot(), componentTypeOf(it)) },
    )
    persistence.saveCharacter(this@unloadCharacter, snapshot)
}

context(write: WriteComponentAccess)
fun EntityId.setCharacterComponents(
    physical: CharacterPhysical?,
    display: CharacterDisplay,
    look: Look,
    character: EngineCharacter,
    equipment: Equipment
) {
    physical?.let { setComponent(it) }
    setComponent(display)
    setComponent(SelectedLook(look))
    setComponent(AppliedCharacter(character))
    setComponent(equipment)
}

fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: CharacterData?,
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
            character,
            Equipment(persistent?.equipment?.toMutableMap() ?: mutableMapOf())
        )
        persistent?.components?.applyResolved(entity)
    }

    platform.clearInventory(this)
    persistent?.inventory?.let {
        platform.openInventory(this, it)
    }

    val usedCharacters = require<UsedCharacters>()
    usedCharacters.characters += character.id

    world.emitEvent(CharacterApplyEvent(character, id), true)
        .apply { set(location) }

    platform.onCharacterApplied(this, character)
}
