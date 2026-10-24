package org.lain.engine.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.container.createContainer
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.player.character.UsedCharacters
import org.lain.engine.player.character.BackendCharacter
import org.lain.engine.player.character.CharacterManager
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.server.*
import org.lain.engine.data.*
import org.lain.engine.item.EngineItem
import org.lain.engine.player.character.PlayCharacterId
import org.lain.engine.player.interaction.HandType
import org.lain.engine.player.interaction.setupHandInteractorEntity
import org.lain.engine.script.dev.EntityInspectionComponent
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.server.replication.Networked
import org.lain.engine.server.replication.Interests
import org.lain.engine.server.replication.PlayerInstantiationConfirmation
import org.lain.engine.server.replication.PlayerReplicationState
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.math.Pos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import java.util.*
import kotlin.apply

data class PlayerComponent(val player: EnginePlayer) : Component

data class PlayerInstantiateSettings(
    val world: World,
    val pos: Pos,
    val displayName: DisplayName,
    val mode: PlayerModeComponent = PlayerModeComponent(PlayerMode.SPECTATOR),
    val movementStatus: MovementStatus = MovementStatus(),
    val attributes: PlayerAttributes = PlayerAttributes(),
    val developerModeStatus: DeveloperModeStatus,
    val skinEyeY: Float = 0f,
    val replayViewer: Boolean = false,
    val items: Set<EngineItem> = emptySet()
)

data class DefaultPlayerAttributes(
    val minVolume: Float = 0.2f,
    val maxVolume: Float = 1.3f,
    val baseVolume: Float = 5f,
    val gravity: Float = 0.087f,
    val tirednessMultiplier: Float = 1f,
) : Component

context(write: WriteComponentAccess)
fun commonPlayerInstance(
    settings: PlayerInstantiateSettings,
    id: PlayerId,
    entity: EntityId = settings.world.addEntity(),
): EnginePlayer {
    val pos = settings.pos
    entity
        .apply {
            setComponent(PersistentIdComponent(CustomPersistentId(id.toString())))
            setComponent(Location(pos))
            setComponent(Velocity())
            setComponent(Orientation())
            setComponent(EnginePlayerModel(skinEyeY = settings.skinEyeY))
            val mainHand = write.addEntity().apply {
                setComponent(PersistentIdComponent(CustomPersistentId("player/$id/hand/main")))
            }
            val offHand = write.addEntity().apply {
                setComponent(PersistentIdComponent(CustomPersistentId("player/$id/hand/offhand")))
            }
            setComponent(
                PlayerInventory(
                    settings.items.toMutableSet(),
                    mainHand.setupHandInteractorEntity(
                        entity, HandType.MAIN, offHand, CustomPersistentId("hand-main-$id"), pos
                    ),
                    offHand.setupHandInteractorEntity(
                        entity, HandType.OFFHAND, mainHand, CustomPersistentId("hand-off-$id"), pos
                    )
                )
            )
            setComponent(ArmStatus(false))
            setComponent(DeveloperMode(settings.developerModeStatus.enabled, settings.developerModeStatus.acoustic))
            setComponent(Hearing())
            setComponent(PlayerInput())
            setComponent(PlayerPhysics())
            setComponent(settings.displayName)
            setComponent(settings.movementStatus)
            setComponent(settings.mode)
            setComponent(settings.attributes)
            setComponent(CustomPlayerAttributes())
            if (settings.replayViewer) {
                setComponent(ReplayViewer)
            }
        }
    val player = EnginePlayer(id, entity, world = settings.world)
    entity.setComponent(PlayerComponent(player))
    return player
}

context(write: WriteComponentAccess)
fun serverPlayerInstance(
    settings: PlayerInstantiateSettings,
    persistent: PersistentPlayerData? = null,
    defaults: DefaultPlayerAttributes,
    id: PlayerId,
    entity: EntityId,
): EnginePlayer {
    val voiceApparatus = persistent?.voiceApparatus ?: VoiceApparatus(inputVolume = defaults.playerBaseInputVolume)
    val player = commonPlayerInstance(settings, id, entity)
    player.entity.apply {
        setComponent(MessageQueue())
        setComponent(voiceApparatus)
        persistent?.voiceLoose?.let { setComponent(it) }
        setComponent(defaults)
        setComponent(PlayerChatHeadsComponent(persistent?.chatHeads ?: true))
        setComponent(PlayerInstantiationConfirmation())
        setComponent(PlayerReplicationState())
        setComponent(Interests())
        //require<PlayerAttributes>().gravity.default = defaults.gravity
        setComponent(AcousticMessageQueue(LinkedList()))
        setComponent(UsedCharacters(persistent?.usedCharacters.orEmpty().toMutableSet()))
        setComponent(EntityInspectionComponent(mutableMapOf()))
        setComponent(Networked)
    }
    return player
}

//TODO: Equipment-контейнер
context(componentAccess: WriteComponentAccess)
fun EnginePlayer.prepareContainers(
    location: Location,
) {
    val playerUuid = this@prepareContainers.id
    val void = componentAccess.createContainer(
        location,
        networked = true,
        persistentId = persistentId("inventory-$playerUuid"),
    )
    void.entity.setComponent(PlayerContainerTag)
    entity.setComponent(PlayerContainer(void.entity))
}


data class PlayerLoadSettings(
    val playerId: PlayerId,
    val notifications: List<Notification>,
    val initialPosition: Pos,
    val username: String,
    val developerModeStatus: DeveloperModeStatus,
    val world: World,
    val isReplayViewer: Boolean = false,
    val playerMode: PlayerMode,
) {
    data class Account(val character: BackendCharacter?) // название Account может слегка путать, ибо выбор не привязан к аккаунту
}

class PlayerLoader(
    private val server: EngineServer,
    private val itemLoader: ItemLoader,
    private val characterManager: CharacterManager,
) {
    private val platform = server.platform
    private val persistence = server.playerPersistence

    suspend fun loadPreparing(
        settings: PlayerLoadSettings,
        account: PlayerLoadSettings.Account,
        beforeCommit: () -> Unit = {},
        onCreated: (EnginePlayer) -> Unit = {},
    ): EnginePlayer = withContext(Dispatchers.IO) {
        if (server.playerStorage.get(settings.playerId) != null) {
            friendlyError("Игрок уже находится на сервере")
        }

        val world = settings.world
        val playerId = settings.playerId
        val transaction = server.createTransactionContext(world)

        val prepared = try {
            val persistentRecord = persistence.loadRecord(playerId)
            val persistentData = persistentRecord?.data

            val playerEntity = persistentRecord?.let {
                transaction.withChild { child ->
                    persistence.loadPersistentEntity(
                        playerId,
                        child,
                    )
                }
            }

            val character = account.character
            val preparedCharacter = character?.let {
                val characterId = PlayCharacterId.player(playerId, character.profile.id)
                val persistentData = if (characterId in persistentData?.usedCharacters.orEmpty()) {
                    persistence.loadPersistentCharacter(
                        transaction,
                        world.componentReviveSettings,
                        playerId,
                        characterId,
                    )
                } else {
                    null
                }
                PreparedCharacter(character, persistentData, characterId)
            }

            val player = with(transaction.commands) {
                serverPlayerInstance(
                    world,
                    settings,
                    persistentData,
                    preparedCharacter?.persistentData?.items.orEmpty(),
                    playerEntity ?: transaction.commands.addEntity(),
                ).also {
                    it.prepareContainers(Location(settings.initialPosition))
                }
            }

            PreparedPlayer(player, preparedCharacter)
        } catch (exception: Throwable) {
            transaction.rollbackIfActive(exception)
            throw exception
        }

        try {
            withContext(server.dataPrepareDispatcher) {
                beforeCommit()
                if (server.playerStorage.get(playerId) != null) {
                    friendlyError("Игрок уже авторизован")
                }

                val player = prepared.player
                platform.beforePlayerInstantiation(player)
                prepared.character?.let { character ->
                    transaction.withChild { childContext ->
                        characterManager.apply(
                            childContext,
                            player,
                            character.backend,
                            character.persistentData,
                            character.id,
                            Location(settings.initialPosition)
                        )
                    }
                }
                transaction.commit()
                server.instantiatePlayer(
                    player,
                    settings.notifications,
                )

                onCreated(prepared.player)

                prepared.player
            }
        } catch (exception: Throwable) {
            transaction.rollbackIfActive(exception)
            throw exception
        }
    }

    private data class PreparedPlayer(
        val player: EnginePlayer,
        val character: PreparedCharacter?
    )

    private data class PreparedCharacter(
        val backend: BackendCharacter,
        val persistentData: PlayCharacterData?,
        val id: PlayCharacterId
    )

    context(write: WriteComponentAccess)
    private fun serverPlayerInstance(
        world: World,
        settings: PlayerLoadSettings,
        persistentPlayerData: PersistentPlayerData?,
        items: Set<EngineItem>,
        entity: EntityId,
    ): EnginePlayer {
        return serverPlayerInstance(
            PlayerInstantiateSettings(
                world,
                settings.initialPosition,
                DisplayName(
                    Username(settings.username.filter { !it.isWhitespace() }),
                    persistentPlayerData?.customName?.toDomain(settings.username)
                ),
                PlayerModeComponent(settings.playerMode),
                MovementStatus(
                    intention = persistentPlayerData?.speedIntention ?: MovementStatus.DEFAULT_INTENTION,
                    stamina = persistentPlayerData?.stamina ?: MovementStatus.DEFAULT_STAMINA
                ),
                PlayerAttributes(),
                settings.developerModeStatus,
                persistentPlayerData?.skinEyeY ?: 0f,
                settings.isReplayViewer,
                items
            ),
            persistentPlayerData,
            server.globals.defaultPlayerAttributes,
            settings.playerId,
            entity
        )
    }
}
