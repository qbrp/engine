package org.lain.engine.player.character

import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerHandler
import org.lain.engine.storage.PersistentPlayerData
import org.lain.engine.storage.copyComponentDtoState
import org.lain.engine.storage.toDomainWithoutRelationships
import org.lain.engine.storage.toSnapshotDto
import org.lain.engine.world.World

context(write: WriteComponentAccess)
fun EnginePlayer.setCharacterDisplayComponents(
    characterDisplay: CharacterDisplay
) {
    entity.setComponent(characterDisplay)
}

context(write: WriteComponentAccess)
fun EnginePlayer.initializeCharacterComponents(
    look: Look,
    physical: CharacterPhysical
) {
    entity.setComponent(physical)
    entity.setComponent(SelectedLook(look))
}

context(world: World)
fun EnginePlayer.removeCharacter() {
    val character = entity.getComponent<AppliedCharacter>()?.character ?: error("Персонаж не применён")
    val charactersMap = entity.requireComponent<AppliedCharacters>().characters
    charactersMap[character.profile.id] = AppliedCharacters.State(
        listOf(
            entity.getComponent<CharacterPhysical>()
        )
            .mapNotNull { it?.toSnapshotDto() }
    )
}

context(write: WriteComponentAccess)
suspend fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: PersistentPlayerData.Character?,
) {
    if (persistent == null) {
        val baseLook = character.looks.first { it.base }
        initializeCharacterComponents(baseLook, character.getPhysical())
    } else {
        entity.copyComponentDtoState(persistent.components) {
            toDomainWithoutRelationships(
                world.itemStorage,
                world.namespacedStorage
            )
        }
    }
    setCharacterDisplayComponents(character.getDisplay())
    entity.setComponent(AppliedCharacter(character))
}