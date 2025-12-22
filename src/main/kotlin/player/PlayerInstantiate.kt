package org.lain.engine.player

import org.lain.engine.util.Component
import org.lain.engine.util.Pos
import org.lain.engine.util.PersistentPlayerData
import org.lain.engine.util.set
import org.lain.engine.util.setNullable
import org.lain.engine.world.Location
import org.lain.engine.world.Orientation
import org.lain.engine.world.Velocity
import org.lain.engine.world.World

data class PlayerInstantiateSettings(
    val world: World,
    val pos: Pos,
    val displayName: DisplayName,
    val movementStatus: MovementStatus = MovementStatus(),
    val attributes: PlayerAttributes = PlayerAttributes(),
    val spectating: Spectating = Spectating(),
    val gameMaster: GameMaster = GameMaster(),
)

data class DefaultPlayerAttributes(
    var movement: MovementDefaultAttributes = MovementDefaultAttributes(),
    var minVolume: Float = 0.2f,
    var maxVolume: Float = 1.3f,
    var baseVolume: Float = 5f,
) : Component

fun commonPlayerInstance(
    settings: PlayerInstantiateSettings,
    id: PlayerId
): Player {
    return Player(id).apply {
        set(Location(settings.world, settings.pos))
        set(Velocity())
        set(Orientation())
        set(PlayerModel())
        set(DeveloperMode(false))
        set(settings.displayName)
        set(settings.movementStatus)
        set(settings.spectating)
        set(settings.gameMaster)
        set(settings.attributes)
    }
}

fun serverPlayerInstance(
    settings: PlayerInstantiateSettings,
    persistent: PersistentPlayerData? = null,
    defaults: DefaultPlayerAttributes,
    id: PlayerId,
): Player {
    val voiceApparatus = persistent?.voiceApparatus ?: VoiceApparatus(inputVolume = defaults.playerBaseInputVolume)

    return commonPlayerInstance(settings, id).apply {
        set(CommandQueue())
        set(MessageQueue())
        set(voiceApparatus)
        setNullable(persistent?.voiceLoose)
        set(PlayerUpdatesComponent())
        set(defaults)
    }
}