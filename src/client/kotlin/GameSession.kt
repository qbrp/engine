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
import org.lain.engine.container.clearAssignItemsOperations
import org.lain.engine.container.updateContainedPlayerInventoryItems
import org.lain.engine.container.updateContainerOperations
import org.lain.engine.container.updateSlotContainers
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.prepareContainers
import org.lain.engine.script.LuaContext
import org.lain.engine.script.compileContents
import org.lain.engine.script.loadContentsCompileResult
import org.lain.engine.script.scripts
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.*
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.component.get
import org.lain.engine.util.component.has
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
    val mainPlayer = mainClientPlayerInstance(player.id, world, player, DeveloperModeStatus(client.developerMode, client.acousticDebug))
    var ticks = 0L
        private set
    val namespacedStorage = NamespacedStorage()
    var soundsToBroadcast = LinkedList<SoundBroadcast>()

    init {
        val equipmentItems = player.equipment.mapValues { (_, item) -> instantiateItem(item) }
        player.items.forEach { item -> instantiateItem(item) }
        instantiatePlayer(mainPlayer, player.general, equipmentItems)
        client.eventBus.onMainPlayerInstantiated(client, this, mainPlayer)
        client.renderer.setupGameSession(this)
        setup.playerList.players.forEach { instantiateLowDetailedPlayer(it) }
        recompileContents()
    }

    private fun instantiateItem(clientboundItemData: ClientboundItemData): EngineItem {
        return instantiateItem(clientItem(world, clientboundItemData), itemStorage)
    }

    fun recompileContents() {
        val serverDirectory = client.resources.serverDirectory
        val scriptsPath = serverDirectory.file.scripts
        namespacedStorage.loadContentsCompileResult(
            compileContents(
                serverDirectory.file,
                LuaContext(scriptsPath, scriptsPath.resolve("$server.lua"))
            )
        )
        onContentsUpdated()
    }

    fun onContentsUpdated() {
        client.audioManager.invalidateCache()
        client.eventBus.onContentsUpdate()
    }

    fun tick() = with(namespacedStorage) {
        ticks++
        chatManager.tick()

        val players = playerStorage.getAll()
        world.apply {
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
                continue
            } else {
                player.isLowDetailed = false
            }

            val playerItems = player.items
            updatePlayerMovement(player, movementDefaultAttributes, movementSettings, true)

            updatePlayerVerbLookup(player, false)
            val interaction = player.get<InteractionComponent>()

            handlePlayerInventoryInteractions(player)
            handleWriteableInteractions(player)
            handleGunInteractions(player, true)
            handleFlashlightInteractions(player)
            handlePlayerEquipmentInteractionProgression(player)
            finishPlayerInteraction(player)
            val processedInteraction = player.get<InteractionComponent>()
            if (interaction != processedInteraction && interaction != null) {
                handler.processedInteractions.add(interaction.id)
            }

            tickInventoryGun(playerItems)
            handleItemRecoil(player, playerItems, false)
            updateHearing(player)
        }

        tickNarrations(mainPlayer)

        val items = itemStorage.getAll()
        handleBulletFireShakes(mainPlayer, client.camera, world, items)

        chatBubbleList.cleanup()
        chatBubbleList.tick()
        val sounds = processWorldSounds(namespacedStorage, world)
        processSoundPlayKeys(LinkedList(sounds + soundsToBroadcast), handler, client.audioManager)
        soundsToBroadcast.clear()
        updateSlotContainers(world)
        updateContainerOperations(world, itemStorage)
        updateContainedPlayerInventoryItems(world)
        clearAssignItemsOperations(world)
        world.updateVoxelEvents(null)
    }

    fun loadChunk(pos: EngineChunkPos, chunk: EngineChunk) {
        world.chunkStorage.setChunk(pos, chunk)
        client.eventBus.onChunkLoad(pos, chunk)
    }

    fun instantiateLowDetailedPlayer(data: GeneralPlayerData): EnginePlayer {
        val player = lowDetailedClientPlayerInstance(data.playerId, world, data)
        instantiatePlayer(player, data)
        return player
    }

    fun instantiatePlayer(
        player: EnginePlayer,
        data: GeneralPlayerData,
        equipment: Map<EquipmentSlot, EngineItem> = emptyMap(),
    ) {
        playerStorage.add(player.id, player)
        player.prepareContainers(data.equipmentContainer, world, player.location, equipment)
    }

    fun destroy() {
        playerStorage.clear()
        client.renderer.invalidate()
        handler.disable()
    }

    fun getPlayer(id: PlayerId) = playerStorage.get(id)
}