package org.lain.engine.player.character

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.server.ServerPlatform
import org.lain.engine.data.PersistentCharacterSnapshot
import org.lain.engine.data.PlayerPersistence
import org.lain.engine.data.SavingComponentSnapshot
import org.lain.engine.data.SerializedInventory
import org.lain.engine.data.persistentId
import org.lain.engine.data.snapshot
import org.lain.engine.player.equipment

@Serializable
data class CharacterApplyEvent(val character: BackendCharacter, val playerId: PlayerId) : Component

fun EnginePlayer.unloadCharacter(
    platform: ServerPlatform,
    persistence: PlayerPersistence,
    serializedInventory: SerializedInventory? = null
) = with(world) {
    val applied = entity.getComponent<AppliedCharacter>() ?: return@with
    val snapshot = PersistentCharacterSnapshot(
        applied.character.id,
        entity.requireComponent<SelectedLook>().look.id,
        serializedInventory ?: platform.serializeInventory(this@unloadCharacter),
        equipment.mapValues { (_, entity) -> entity.persistentId() },
        listOfNotNull(
            entity.getComponent<CharacterPhysical>()
        )
            .map { SavingComponentSnapshot(it.snapshot(), componentTypeOf(it)) },
    )
    persistence.saveCharacter(applied.id, snapshot)
}
