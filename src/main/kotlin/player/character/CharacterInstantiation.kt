package org.lain.engine.player.character

import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerEventListener
import org.lain.engine.server.ServerHandler
import org.lain.engine.storage.PersistentCharacterData
import org.lain.engine.storage.PersistentPlayerData
import org.lain.engine.storage.copyComponentDtoState
import org.lain.engine.storage.toDomainWithoutRelationships
import org.lain.engine.storage.toSnapshotDto
import org.lain.engine.world.World

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
    appliedCharacters: AppliedCharacters = entity.requireComponent<AppliedCharacters>()
) {
    val character = entity.getComponent<AppliedCharacter>()?.character ?: return
    val charactersMap = appliedCharacters.characters
    charactersMap[character.profile.id] = PersistentCharacterData(
        listOf(
            entity.getComponent<CharacterPhysical>()
        )
            .mapNotNull { it?.toSnapshotDto() },
        entity.requireComponent<SelectedLook>().look.id
    )
}

context(write: WriteComponentAccess)
suspend fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: PersistentCharacterData?,
    listener: ServerEventListener
) {
    if (persistent == null) {
        initializeCharacterComponents(character.getPhysical())
    } else {
        entity.copyComponentDtoState(persistent.components) {
            toDomainWithoutRelationships(
                world.itemStorage,
                world.namespacedStorage
            )
        }
    }
    setCharacterDisplayComponents(
        character.getDisplay(),
        persistent?.look?.let { lookId -> character.looks.firstOrNull { it.id == lookId } } ?: character.baseLook
    )
    entity.setComponent(AppliedCharacter(character))
    listener.onCharacterApplied(this, character)
}