package org.lain.engine.server

import kotlinx.coroutines.*
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.markDirty
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.chat.*
import org.lain.engine.item.Item
import org.lain.engine.item.Writable
import org.lain.engine.item.getOwner
import org.lain.engine.player.*
import org.lain.engine.player.character.*
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.script.*
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.backupBookContent
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.packet.*
import org.lain.engine.util.*
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.let
import kotlin.math.pow

class ServerHandler(
    private val engineServer: EngineServer,
) {
    private var running = false
    private val transportContext by injectServerTransportContext()
    private val playerStorage: PlayerStorage get() = engineServer.playerStorage
    private val globals: ServerGlobals get() = engineServer.globals
    val playerSynchronizationRadius get() = globals.playerSynchronizationRadius
    private val playerDesynchronizationThreshold get() = globals.playerDesynchronizationThreshold

    private var squaredSynchronizationRadius = 0f
    private var squaredDesynchronizationRadius = 0f
    private val taskQueue = ConcurrentLinkedQueue<() -> Unit>()
    private val connections = mutableMapOf<PlayerId, Connection>()

    private fun updatePlayer(id: PlayerId, update: EnginePlayer.() -> Unit) {
        val player = engineServer.playerStorage.get(id) ?: protocolError("Игрок не находится на сервере")
        player.update()
    }

    private fun updatePlayerWithContext(
        id: PlayerId,
        update: context(World) EnginePlayer.(world: World) -> Unit
    ) {
        val player = engineServer.playerStorage.get(id) ?: protocolError("Игрок не находится на сервере")
        with(player.world) { player.update(player.world) }
    }

    private fun EnginePlayer.hasPermission(permission: String) = engineServer.hasPermission(this, permission)

    private fun getPlayer(id: PlayerId): EnginePlayer? {
        return engineServer.playerStorage.get(id)
    }

    fun execute(r: () -> Unit) = taskQueue.add(r)

    fun onServerSettingsUpdate() {
        squaredSynchronizationRadius =
            (playerSynchronizationRadius * playerSynchronizationRadius).toFloat()
        squaredDesynchronizationRadius =
            (playerSynchronizationRadius + playerDesynchronizationThreshold).toFloat().pow(2)
        CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT.broadcast {
            ServerSettingsUpdatePacket(
                ClientboundServerSettings.of(engineServer, it)
            )
        }
    }

    fun run() {
        running = true
        registerEndpoints()
    }

    fun invalidate() {
        if (!running) return
        transportContext.unregisterAll()
        taskQueue.clear()
        running = false
    }

    fun openConnection(playerId: PlayerId) {
        connections[playerId] = Connection(
            playerId,
            ConnectionState.Authorization(playerId)
        )
    }

    fun closeConnection(playerId: PlayerId) {
        connections.remove(playerId)
    }

    internal fun onLookApply(playerId: PlayerId, lookId: String, requestId: Long) =
        updatePlayer(playerId) {
            val character = require<AppliedCharacter>().character
            val look =
                character.looks.find { it.id == lookId } ?: protocolError("Образ $lookId не существует")
            set(SelectedLook(look))
            onCharacterApplyConfirmation(this@updatePlayer, requestId)
        }

    internal fun onCharacterApply(
        playerId: PlayerId,
        characterId: CharacterId,
        character: EngineCharacter?,
        sessionTicket: SessionTicket?,
        requestId: Long,
    ) {
        val persistence = engineServer.playerPersistence
        val player = getPlayer(playerId) ?: protocolError("Игрок не существует")
        val world = player.world
        with(world) { player.unloadCharacter(engineServer.platform, persistence) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eventListener = engineServer.platform
                val validatedCharacter = eventListener.validateCharacter(
                    player,
                    characterId,
                    character,
                    sessionTicket
                )
                val characterRecord = persistence.loadPersistentCharacter(world.componentReviveSettings, playerId, characterId)
                withContext(engineServer.dispatcher) {
                    engineServer.platform.clearInventory(player)
                    player.applyCharacter(validatedCharacter, characterRecord, eventListener)
                    onCharacterApplyConfirmation(player, requestId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "Не удалось применить персонажа"
                withContext(engineServer.dispatcher) {
                    onCharacterApplyConfirmation(player, requestId, message)
                }
                e.printStackTrace()
            }
        }
    }

    internal fun onEntityComponentRpcPacket(
        sender: PlayerId,
        entityPersistentId: PersistentId,
        delta: List<ScriptValue>
    ) = updatePlayerWithContext(sender) {
        val entity =
            world.persistentIdToEntity[entityPersistentId]
                ?: protocolError("Сущности $entityPersistentId не существует")
        entity.requireComponent<EntityRpcReceiver>().values.addAll(
            delta.map { EntityRpcReceiver.Message(this, it) }
        )
    }

    internal fun onScriptBindings(player: PlayerId, bindings: ScriptBindings) =
        updatePlayer(player) {
            fun <C : ScriptContext> ScriptId.ensureExists() =
                require(engineServer.namespacedStorage.getVoidScript<C>(this) != null)
            bindings.base?.ensureExists<ScriptContext.Player>()
            bindings.attack?.ensureExists<ScriptContext.Player>()
            set(bindings)
        }

    internal fun onEntityDebugView(player: PlayerId, persistentId: PersistentId) =
        updatePlayer(player) {
            if (!engineServer.platform.hasPermission(this, "entity_debug")) return@updatePlayer
            val entity = world.persistentIdToEntity[persistentId]
                ?: protocolError("Сущность $persistentId не существует")
            with(world) {
                this@updatePlayer.entity.setComponent(
                    EntityDebugViewComponent(
                        entity,
                        entity.snapshotDebugData()
                    )
                )
            }
        }

    internal fun onEntityDebugViewStop(player: PlayerId) = updatePlayer(player) {
        remove<EntityDebugViewComponent>()
    }

    internal fun onVoxelBlockHint(
        player: PlayerId,
        pos: VoxelPos,
        action: VoxelBlockHintPacket.Action
    ) =
        updatePlayer(player) {
            when (action) {
                is VoxelBlockHintPacket.Action.Add -> {
                    if (hasPermission("blockhint.set")) {
                        world.singleBlockVoxelEvent(pos, VoxelUpdate.AddHint(action.text))
                    }
                }

                is VoxelBlockHintPacket.Action.Remove -> {
                    if (hasPermission("blockhint.remove")) {
                        val hint = world.chunkStorage.getBlockHint(pos)
                            ?: protocolError("Описание блока не существует")
                        if (hint.texts.size - 1 < action.index || action.index < 0) {
                            protocolError("Невалидный индекс")
                        }
                        world.singleBlockVoxelEvent(pos, VoxelUpdate.RemoveHint(action.index))
                    }
                }
            }
        }

    internal fun onPlayerInput(playerId: PlayerId, tick: Long, input: Set<InputAction>) =
        updatePlayerWithContext(playerId) {
            this.entity.requireComponent<PlayerSyncState>().enqueueInput(tick, input)
        }

    internal fun onReplicationResyncRequest(playerId: PlayerId, target: ReplicationTarget) =
        updatePlayerWithContext(playerId) {
            val frame = when (target) {
                ReplicationTarget.World -> ReplicationFrameSnapshot(
                    world = world.state.fullNetworkSnapshot(),
                    entities = emptyMap(),
                )

                is ReplicationTarget.Entity -> {
                    val persistentId = target.persistentId
                    val syncState = entity.requireComponent<PlayerSyncState>()
                    if (persistentId !in syncState.entities.synced) {
                        return@updatePlayerWithContext
                    }
                    val networkedEntity = world.persistentIdToEntity[persistentId]
                        ?: return@updatePlayerWithContext
                    ReplicationFrameSnapshot(
                        world = null,
                        entities = mapOf(
                            persistentId to networkedEntity.fullNetworkSnapshot(),
                        ),
                    )
                }
            }
            sendReplicationFrame(this, frame)
        }

    internal fun onWriteableContentsUpdate(
        playerId: PlayerId,
        persistentId: PersistentId,
        contents: List<String>
    ) =
        updatePlayerWithContext(playerId) {
            val item = this.handItem
            val writable = item?.getComponent<Writable>()
            if (item?.requireComponent<PersistentIdComponent>()?.id != persistentId || writable == null) protocolError(
                "Предмет для сохранения написанного контента не найден или им не является"
            )
            if (contents.count() > writable.pages) protocolError("Страниц написано больше, чем возможно")

            if (writable.contents != contents) {
                writable.contents = contents.map { it.trim() }
                item.markDirty<Writable>()
                backupBookContent(username, item.requireComponent<Item>().id, contents)
            }
        }

    internal fun onPlayerArmStatus(playerId: PlayerId, extend: Boolean) =
        updatePlayerWithContext(playerId) {
            extendArm = extend
            entity.markUpdated<ArmStatus>()
        }

    internal fun onPlayerChatTypingStart(player: PlayerId, channelId: ChannelId) =
        updatePlayer(player) {
            val player = this
            val channel = engineServer.chat.getChannel(channelId)
            val acoustic = channel.acoustic

            if (!channel.typeIndicator || acoustic == null) {
                return@updatePlayer
            }

            val range = channel.typeIndicatorRange
            val nearestPlayers = range?.let { player.filterNearestPlayers(it) }
            val players = nearestPlayers ?: when (acoustic) {
                is Acoustic.Global -> playerStorage.all
                is Acoustic.Distance -> player.filterNearestPlayers(acoustic.radius)
                is Acoustic.Realistic -> {
                    val radius = engineServer.chat.settings.defaultChannel.typeIndicatorRange ?: 16
                    CHAT_LOGGER.warn("Акустическая симуляция не работает, чтобы подсчитать, каким игрокам отображать индикатор ввода сообщения. Используется стандартный радиус $radius блоков.")
                    player.filterNearestPlayers(radius)
                }
            }.filter { it.isChannelAvailableToRead(channel) }
            val packet = ChatTypingPlayerPacket(player.id)
            players.forEach { CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT.sendS2C(packet, it.id) }
        }

    internal fun onPlayerChatTypingEnd(player: PlayerId) {
        CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT.broadcast(ChatTypingPlayerPacket(player))
    }

    internal fun onPlayerVolume(player: PlayerId, volume: Float) = updatePlayer(player) {
        val settings = require<DefaultPlayerAttributes>()
        if (volume > settings.maxVolume || volume < 0) {
            protocolError("Недопустимый уровень громкости")
        }
        require<VoiceApparatus>().inputVolume = volume
    }

    internal fun onPlayerSpeedIntentionSet(player: PlayerId, value: Float) = updatePlayer(player) {
        intentSpeed(value.coerceIn(0f, 1f))
    }

    internal fun onChatMessage(player: PlayerId, content: String, channelId: ChannelId) =
        updatePlayer(player) {
            val content = content.trim()
            if (content.isEmpty()) return@updatePlayer
            speak(content, channelId)
        }

    internal fun onDeveloperModeEnabled(playerId: PlayerId, enabled: Boolean, acoustic: Boolean) =
        updatePlayer(playerId) {
            developerMode = enabled
            acousticDebug = acoustic
        }

    // FIXME: Искать предметы по инвентарю игрока, а не глобально
    internal fun onPlayerCursorItem(playerId: PlayerId, itemId: PersistentId?) =
        updatePlayerWithContext(playerId) {
            val itemStorage = it.itemStorage
            val item = itemId?.let {
                val result = itemStorage.get(it)
                    ?: protocolError("Установленный курсором предмет $itemId не найден")
                val owner = result.getOwner()
                if (!hasPermission("invsee") && owner != null && owner.id != playerId) {
                    protocolError("Захвачен чужой предмет")
                }
                result
            }
            require<PlayerInventory>().cursorItem = item
        }

    internal fun onChatMessageDelete(by: PlayerId, messageId: MessageId) {
        val player = playerStorage.get(by) ?: return
        val chat = engineServer.chat
        val outcomingMessage = chat.outcomingMessageHistory[messageId] ?: return
        if (outcomingMessage.source.author.player?.id != player.id) return
        val packet = DeleteChatMessagePacket(messageId)
        playerStorage.all.forEach {
            if (it.id == player.id) return@forEach
            CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT.sendS2C(packet, it.id)
        }
        CHAT_LOGGER.info("Удалено сообщение игроком $player: $outcomingMessage")
    }

    fun processHandlerTasks() {
        taskQueue.flush { it() }
    }

    fun sendReplicationFrame(player: EnginePlayer, frame: ReplicationFrameSnapshot) {
        CLIENTBOUND_REPLICATION_ENDPOINT.sendS2C(ReplicationPacket(frame), player.id)
    }

    context(world: World)
    fun sendFullPlayerState(player: EnginePlayer, playerToSync: EnginePlayer) {
        CLIENTBOUND_FULL_PLAYER_ENDPOINT
            .sendS2C(
                FullPlayerPacket(
                    playerToSync.id,
                    FullPlayerData.of(playerToSync)
                ),
                player.id
            )
    }

    fun onCharacterApplyConfirmation(
        player: EnginePlayer,
        requestId: Long,
        errorMessage: String? = null
    ) {
        CLIENTBOUND_CHARACTER_APPLY_CONFIRMATION_ENDPOINT.sendS2C(
            CharacterApplyConfirmationPacket(requestId, errorMessage),
            player.id
        )
    }

    fun onPlayerOperation(context: ScriptContext.OperationExecution, operation: Operation) {
        CLIENTBOUND_OPERATION_ENDPOINT.broadcastInRadius(
            context.actor.player,
            playerSynchronizationRadius,
            OperationPacket(operation.id, context.toDto())
        )
    }

    fun onScriptsCompiled() {
        CLIENTBOUND_SCRIPT_RECOMPILE_ENDPOINT.broadcast(ScriptsRecompileEndpoint(null))
    }

    fun onScriptReloaded(script: String) {
        CLIENTBOUND_SCRIPT_RECOMPILE_ENDPOINT.broadcast(ScriptsRecompileEndpoint(script))
    }

    fun playSoundLocal(play: SoundPlay, ignorePhysics: Boolean, receivers: List<EnginePlayer>) {
        val packet = SoundPlayPacket(play, ignorePhysics)
        receivers.forEach {
            CLIENTBOUND_SOUND_PLAY_ENDPOINT.sendS2C(packet, it.id)
        }
    }

    fun onOutcomingMessage(player: MessageSource.Player, message: OutcomingMessage) {
        CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
            .sendS2C(
                OutcomingChatMessagePacket(message),
                player.id
            )
    }

    fun onEntityDebugSnapshot(player: EnginePlayer, data: EntityDebugData.Dto) {
        CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT.sendS2C(
            EntityDebugDataPacket(data),
            player.id
        )
    }

    fun onServerNotification(player: EnginePlayer, notification: Notification, once: Boolean) {
        CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
            .sendS2C(
                PlayerNotificationPacket(
                    notification,
                    once
                ),
                player.id
            )
    }

    fun sendChunk(player: EnginePlayer, chunk: EngineChunk, pos: EngineChunkPos) {
        CLIENTBOUND_CHUNK_ENDPOINT.sendS2C(
            EngineChunkPacket(
                EngineChunkDto(
                    pos,
                    chunk.decals.mapKeys { (k, v) -> ImmutableVoxelPos(k) },
                    chunk.hints.mapKeys { (k, v) -> ImmutableVoxelPos(k) }
                )
            ),
            player.id
        )
        player.require<PlayerSyncState>().sentChunks[pos] = chunk
    }

    fun sendOrQueueChunk(playerId: PlayerId, chunk: EngineChunk, chunkPos: EngineChunkPos) {
        connections[playerId]!!.state.sendChunk(chunk, chunkPos)
    }

    fun onChunkDropped(player: PlayerId, chunkPos: EngineChunkPos) = playerStorage.get(player)?.let {
        it.require<PlayerSyncState>().sentChunks -= chunkPos
    }

    fun onServerNotification(player: PlayerId, notification: Notification, once: Boolean) {
        engineServer.playerStorage.get(player)?.let { onServerNotification(it, notification, once) }
    }

    fun onPlayerInstantiation(
        player: EnginePlayer,
        notifications: List<Notification> = listOf(),
    ) =
        with(player.world) {
            val packet = PlayerJoinServerPacket(GeneralPlayerData.of(player))

            playerStorage.all.forEach {
                if (it == player) return@forEach
                CLIENTBOUND_PLAYER_JOIN_ENDPOINT.sendS2C(
                    packet,
                    it.id
                )
            }

            val joinGamePacket = JoinGamePacket(
                ServerPlayerData.of(player),
                ClientboundWorldData.of(this),
                ClientboundSetupData.create(engineServer, player),
                notifications,
            )
            CLIENTBOUND_JOIN_GAME_ENDPOINT.sendS2C(joinGamePacket, player.id)
        }

    fun onPlayerInstantiationConfirm(playerId: PlayerId) = updatePlayer(playerId) {
        connections[playerId]!!.onAuthorized(this@ServerHandler, this)
        remove<PlayerInstantiationConfirmation>() ?: protocolError("Invalid player state")
    }

    fun onPlayerDestroy(player: EnginePlayer) {
        CLIENTBOUND_PLAYER_DESTROY_ENDPOINT.broadcast(
            PlayerDestroyPacket(player.id)
        )
    }

    fun onPersonalVolumeAcousticDebug(
        player: EnginePlayer,
        volumes: List<Pair<ImmutableVoxelPos, Float>>
    ) {
        CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET.sendS2C(
            AcousticDebugVolumesPacket(volumes),
            player.id
        )
    }

    fun onVoxelEvent(world: World, event: VoxelEvent, players: Collection<EnginePlayer>) {
        players.forEach {
            if (it.world != world) return@forEach
            CLIENTBOUND_VOXEL_EVENT_PACKET.sendS2C(
                VoxelEventPacket(event),
                it.id
            )
        }
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        world: World,
        center: Location,
        radius: Int,
        exclude: List<EnginePlayer> = emptyList(),
        packet: P
    ) {
        for (player in world.players) {
            if (player !in exclude && player.location.position.squaredDistanceTo(center.position) <= radius * radius) {
                sendS2C(packet, player.id)
            }
        }
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        radius: Int = playerSynchronizationRadius,
        packet: P
    ) {
        broadcastInRadius(player.world, player.location, radius, packet = packet)
    }
}
