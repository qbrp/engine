package org.lain.engine.player

import org.lain.engine.item.EngineItem
import org.lain.engine.server.*
import org.lain.engine.storage.PersistentPlayerData
import org.lain.engine.util.Component
import org.lain.engine.util.math.Pos
import org.lain.engine.util.set
import org.lain.engine.util.setNullable
import org.lain.engine.world.Location
import org.lain.engine.world.World

data class PlayerInstantiateSettings(
    val world: World,
    val pos: Pos,
    val displayName: DisplayName,
    val movementStatus: MovementStatus = MovementStatus(),
    val attributes: PlayerAttributes = PlayerAttributes(),
    val spectating: Spectating = Spectating(),
    val gameMaster: GameMaster = GameMaster(),
    val items: Set<EngineItem> = setOf()
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
): EnginePlayer {
    return EnginePlayer(id).apply {
        set(Location(settings.world, settings.pos))
        set(Velocity())
        set(Orientation())
        set(PlayerModel())
        set(OrientationTranslation(0f, 0f))
        set(DeveloperMode(false))
        set(PlayerInventory(settings.items.toMutableSet()))
        set(ArmStatus(false))
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
): EnginePlayer {
    val voiceApparatus = persistent?.voiceApparatus ?: VoiceApparatus(inputVolume = defaults.playerBaseInputVolume)

    return commonPlayerInstance(settings, id).apply {
        set(MessageQueue())
        set(voiceApparatus)
        setNullable(persistent?.voiceLoose)
        set(PlayerUpdates())
        set(defaults)
        set(PlayerChatHeadsComponent(persistent?.chatHeads ?: true))
        set(PlayerSynchronizationComponent(false))
        set(Synchronizations<EnginePlayer>(mutableMapOf()))
            .also { it.initializeSynchronizers() }
    }
}

fun Synchronizations<EnginePlayer>.initializeSynchronizers() {
    submit(PLAYER_ARM_STATUS_SYNCHRONIZER)
    submit(PLAYER_CUSTOM_NAME_SYNCHRONIZER)
}