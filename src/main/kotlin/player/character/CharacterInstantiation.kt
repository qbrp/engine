package org.lain.engine.player.character

import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.EnginePlayer

context(componentAccess: WriteComponentAccess)
fun EnginePlayer.setCharacterDisplayComponents(
    characterDisplay: CharacterDisplay
) {
    entity.setComponent(characterDisplay)
}

context(componentAccess: WriteComponentAccess)
fun EnginePlayer.initializeCharacterPhysicalComponents(
    physical: CharacterPhysical
) {
    entity.setComponent(physical)
}
