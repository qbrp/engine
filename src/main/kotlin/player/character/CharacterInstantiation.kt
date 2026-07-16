package org.lain.engine.player.character

import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.character.initializeCharacterPhysicalComponents
import org.lain.engine.player.character.setCharacterDisplayComponents
import org.lain.engine.storage.PersistentPlayerData
import org.lain.engine.storage.copyComponentDtoState
import org.lain.engine.storage.toDomainWithoutRelationships

context(write: WriteComponentAccess)
fun EnginePlayer.setCharacterDisplayComponents(
    characterDisplay: CharacterDisplay
) {
    entity.setComponent(characterDisplay)
}

context(write: WriteComponentAccess)
fun EnginePlayer.initializeCharacterPhysicalComponents(
    physical: CharacterPhysical
) {
    entity.setComponent(physical)
}

context(write: WriteComponentAccess)
suspend fun EnginePlayer.applyCharacter(
    character: EngineCharacter,
    persistent: PersistentPlayerData.Character?,
) {
    if (persistent == null) {
        initializeCharacterPhysicalComponents(character.getPhysical())
    } else {
        entity.copyComponentDtoState(persistent.components) {
            toDomainWithoutRelationships(
                world.itemStorage,
                world.namespacedStorage
            )
        }
    }
    setCharacterDisplayComponents(character.getDisplay())
}