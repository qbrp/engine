package org.lain.engine.client

import org.lain.engine.client.chat.ChatBubbleList
import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.MovementManager
import org.lain.engine.client.handler.*
import org.lain.engine.client.render.handleBulletFireShakes
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.client.util.processSoundPlayKeys
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.apply
import org.lain.engine.util.file.compileContents
import org.lain.engine.util.file.loadContentsCompileResult
import org.lain.engine.util.has
import org.lain.engine.world.*
import java.util.*

class GameSession(
    val server: ServerId,
    val world: World,
    setup: ClientboundSetupData,
    player: ServerPlayerData,
    val handler: ClientHandler,
    val client: EngineClient,
) {
    val renderer = client.renderer
    val chatEventBus = client.chatEventBus
    var playerSynchronizationRadius: Int = setup.settings.playerSynchronizationRadius
    var playerDesynchronizationThreshold: Int = setup.settings.playerDesynchronizationThreshold

    var extendArm = false
    set(value) {
        handler.onArmStatusUpdate(value)
        mainPlayer.extendArm = value
        field = value
    }

    var admin = false
    var acousticDebugVolumes = listOf<Pair<VoxelPos, Float>>()
    val playerStorage = ClientPlayerStorage()
    val itemStorage = ClientItemStorage()
    val movementManager = MovementManager(this)
    val chatBubbleList = ChatBubbleList(client.options, client.fontRenderer)
    val chatManager = ClientEngineChatManager(
        chatEventBus,
        client,
        this,
        setup.settings.chat,
    )

    var movementDefaultAttributes = setup.settings.defaultAttributes.movement
    var movementSettings = setup.settings.movement
    val vocalRegulator = PlayerVocalRegulator(
        PlayerVolume(player.volume, player.maxVolume, player.baseVolume),
        this
    )
    val mainPlayer = mainClientPlayerInstance(player.id, world, player)
    var ticks = 0L
        private set
    val namespacedStorage = NamespacedStorage()
    var soundsToBroadcast = LinkedList<SoundBroadcast>()

    init {
        instantiatePlayer(mainPlayer)
        client.eventBus.onMainPlayerInstantiated(client, this, mainPlayer)
        client.renderer.setupGameSession(this)
        setup.playerList.players.forEach { instantiateLowDetailedPlayer(it) }
        player.items.forEach { itemStorage.add(it.uuid, clientItem(world, it)) }
        namespacedStorage.loadContentsCompileResult(compileContents(client.resources.contents.file))
    }

    fun tick() {
        ticks++
        chatManager.tick()
        val players = playerStorage.getAll()
        world.apply<ScenePlayers> {
            this.players.clear()
            this.players.addAll(players)
        }

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        for (player in players) {
            if (player.pos.squaredDistanceTo(mainPlayer.pos) > playerSynchronizationRadius * playerSynchronizationRadius) {
                player.isLowDetailed = true
                return
            } else {
                player.isLowDetailed = false
            }

            val playerItems = player.items
            updatePlayerMovement(player, movementDefaultAttributes, movementSettings, true)
            supplyPlayerInventoryItemsLocation(player, playerItems)

            updatePlayerVerbLookup(player)
            updatePlayerInteractions(player)

            handlePlayerInventoryInteractions(player)
            handleWriteableInteractions(player)
            handleGunInteractions(player, true)
            finishPlayerInteraction(player)

            tickInventoryGun(playerItems)
            handleItemRecoil(player, playerItems, false)
        }

        tickNarrations(mainPlayer)

        val items = itemStorage.getAll()
        handleBulletFireShakes(mainPlayer, client.camera, world, items)

        chatBubbleList.cleanup()
        val sounds = processWorldSounds(namespacedStorage, world)
        processSoundPlayKeys(LinkedList(sounds + soundsToBroadcast), handler, client.audioManager)
        soundsToBroadcast.clear()
    }

    fun loadChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        world.chunkStorage.setChunk(pos, chunk)
        client.eventBus.onChunkLoad(pos, chunk)
    }

    fun instantiateLowDetailedPlayer(data: GeneralPlayerData): EnginePlayer {
        val player = lowDetailedClientPlayerInstance(data.playerId, world, data)
        instantiatePlayer(player)
        return player
    }

    fun instantiatePlayer(player: EnginePlayer) {
        playerStorage.add(player.id, player)
    }

    fun destroy() {
        playerStorage.clear()
        client.renderer.invalidate()
        handler.disable()
    }

    fun getPlayer(id: PlayerId) = playerStorage.get(id)
}