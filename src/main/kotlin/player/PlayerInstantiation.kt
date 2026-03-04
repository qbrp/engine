package org.lain.engine.player

import org.lain.engine.item.EngineItem
import org.lain.engine.server.*
import org.lain.engine.storage.PersistentPlayerData
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.util.component.Component
import org.lain.engine.util.math.Pos
import org.lain.engine.util.component.set
import org.lain.engine.util.component.setNullable
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
    val developerModeStatus: DeveloperModeStatus,
    val items: Set<EngineItem> = setOf()
)

data class DefaultPlayerAttributes(
    var movement: MovementDefaultAttributes = MovementDefaultAttributes(),
    var minVolume: Float = 0.2f,
    var maxVolume: Float = 1.3f,
    var baseVolume: Float = 5f,
    var tirednessMultiplier: Float = 1f,
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
        set(PlayerInventory(settings.items.toMutableSet()))
        set(ArmStatus(false))
        set(PlayerInput(mutableSetOf(), setOf()))
        set(Narration(mutableListOf()))
        set(DeveloperMode(settings.developerModeStatus.enabled, settings.developerModeStatus.acoustic))
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
        set(ServerPlayerInputMeta(false))
        set(MessageQueue())
        set(voiceApparatus)
        setNullable(persistent?.voiceLoose)
        set(PlayerUpdates())
        set(defaults)
        set(PlayerChatHeadsComponent(persistent?.chatHeads ?: true))
        set(PlayerNetworkState(false))
        set(Synchronizations<EnginePlayer>(mutableMapOf()))
            .also { it.initializeSynchronizers() }
    }
}

private fun Synchronizations<EnginePlayer>.initializeSynchronizers() {
    submit(PLAYER_ARM_STATUS_SYNCHRONIZER)
    submit(PLAYER_CUSTOM_NAME_SYNCHRONIZER)
    submit(PLAYER_SPEED_INTENTION_SYNCHRONIZER)
    submit(PLAYER_NARRATION_SYNCHRONIZER)
}