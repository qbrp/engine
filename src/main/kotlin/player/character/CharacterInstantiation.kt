package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.data.*
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.equipment
import org.lain.engine.player.items
import org.lain.engine.server.ServerPlatform
import org.lain.engine.world.location
import org.lain.engine.world.pos

@Serializable
data class CharacterApplyEvent(val character: ServerCharacterData, val playerId: PlayerId) : Component

@Serializable
data class CharacterUnloadEvent(val character: ServerCharacterData, val player: PlayerId) : Component

fun EnginePlayer.unloadCharacter(
    platform: ServerPlatform,
    persistence: PlayerPersistence,
    serializedInventory: SerializedInventory? = null
) {
    with(world) {
        val applied = entity.getComponent<AppliedCharacter>() ?: return
        val snapshot = PersistentCharacterSnapshot(
            applied.character.id,
            entity.requireComponent<SelectedLook>().look.id,
            serializedInventory ?: platform.serializeInventory(this@unloadCharacter),
            items.map { it.persistentId() },
            equipment.mapValues { (_, entity) -> entity.persistentId() },
            listOfNotNull(
                entity.getComponent<CharacterPhysical>()
            )
                .map { SavingComponentSnapshot(it.snapshot(), componentTypeOf(it)) },
        )
        persistence.saveCharacter(applied.id, snapshot)
    }
}
