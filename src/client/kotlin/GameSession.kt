package org.lain.engine.client

import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.MovementManager
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.chat.ChatBubbleList
import org.lain.engine.client.mc.updateBulletsVisual
import org.lain.engine.client.transport.clientItem
import org.lain.engine.client.transport.isLowDetailed
import org.lain.engine.client.transport.lowDetailedClientPlayerInstance
import org.lain.engine.client.transport.mainClientPlayerInstance
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.item.supplyPlayerInventoryItemsLocation
import org.lain.engine.item.updateGunState
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.ShakeScreenComponent
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.items
import org.lain.engine.player.stamina
import org.lain.engine.player.updatePlayerMovement
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.handle
import org.lain.engine.util.has
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.lain.engine.world.VoxelPos
import org.lain.engine.world.World
import org.lain.engine.world.WorldSoundsComponent
import org.lain.engine.world.pos

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

    var admin = false
    var acousticDebugVolumes = listOf<Pair<VoxelPos, Float>>()
    val playerStorage = ClientPlayerStorage()
    val itemStorage = ClientItemStorage()
    val movementManager = MovementManager(handler)
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

    init {
        instantiatePlayer(mainPlayer)
        client.eventBus.onMainPlayerInstantiated(client, this, mainPlayer)
        client.renderer.setupGameSession(this)
        setup.playerList.players.forEach { instantiateLowDetailedPlayer(it) }
        player.items.forEach { itemStorage.add(it.uuid, clientItem(world, it)) }
    }

    fun tick() {
        val players = playerStorage.getAll()

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        for (player in players) {
            if (player.isLowDetailed) continue

            val playerItems = player.items
            supplyPlayerInventoryItemsLocation(player, playerItems)
            updateGunState(playerItems, true)
            updatePlayerMovement(player, movementDefaultAttributes, movementSettings)

            if (player.pos.squaredDistanceTo(mainPlayer.pos) > playerSynchronizationRadius * playerSynchronizationRadius) {
                player.isLowDetailed = true
            }

            player.handle<ShakeScreenComponent> {
                client.camera.stress(stress)
                player.remove<ShakeScreenComponent>()
            }

            world.require<WorldSoundsComponent>().events.clear()
        }

        chatBubbleList.cleanup()
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