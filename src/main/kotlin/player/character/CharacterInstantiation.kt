package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.server.ServerPlatform
import org.lain.engine.storage.PersistentCharacterData
import org.lain.engine.storage.copyComponentDtoState
import org.lain.engine.storage.toDomainWithoutRelationships
import org.lain.engine.storage.toSnapshotDto
import org.lain.engine.world.World
import org.lain.engine.world.location

@Serializable
data class CharacterApplyEvent(val character: EngineCharacter, val playerId: PlayerId) : Component

context(write: WriteComponentAccess)
fun EnginePlayer.setCharacterDisplayComponents(
    characterDisplay: CharacterDisplay,
    look: Look
) {
    entity.setComponent(characterDisplay)
    entity.setComponent(SelectedLook(look))
}

context(write: WriteComponentAccess)
fun EnginePlayer.initializeCharacterComponents(
    physical: CharacterPhysical
) {
    entity.setComponent(physical)
}

context(world: World)
fun EnginePlayer.removeCharacter(
    platform: ServerPlatform,
    appliedCharacters: AppliedCharacters = entity.requireComponent<AppliedCharacters>(),
) {
    val character = entity.getComponent<AppliedCharacter>()?.character ?: return
    val charactersMap = appliedCharacters.characters
    charactersMap[character.profile.id] = PersistentCharacterData(
        listOf(
            entity.getComponent<CharacterPhysical>()
        )
            .mapNotNull { it?.toSnapshotDto() },
        entity.requireComponent<SelectedLook>().look.id,
        platform.serializeInventory(this)
    )
    platform.clearInventory(this)
}


context(write: WriteComponentAccess)
suspend fun EnginePlayer.prepareCharacter(persistent: PersistentCharacterData) {
    entity.copyComponentDtoState(persistent.components) {
        toDomainWithoutRelationships(
            world.itemStorage,
            world.namespacedStorage
        )
    }
}

context(write: WriteComponentAccess)
fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: PersistentCharacterData?,
    platform: ServerPlatform
) {
    if (persistent == null) {
        initializeCharacterComponents(character.getPhysical())
    }
    setCharacterDisplayComponents(
        character.getDisplay(),
        persistent?.look?.let { lookId -> character.looks.firstOrNull { it.id == lookId } } ?: character.baseLook
    )
    entity.setComponent(AppliedCharacter(character))
    persistent?.items?.let { platform.openInventory(this, it) }
    world.emitEvent(CharacterApplyEvent(character, id), true)
        .apply { setComponent(location) }
    platform.onCharacterApplied(this, character)
}