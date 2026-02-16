package org.lain.engine.client

import org.lain.engine.client.chat.ChatBubbleList
import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.MovementManager
import org.lain.engine.client.handler.*
import org.lain.engine.client.render.handleBulletFireShakes
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.item.handleGunShotTags
import org.lain.engine.item.supplyPlayerInventoryItemsLocation
import org.lain.engine.item.updateGunState
import org.lain.engine.player.*
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.handle
import org.lain.engine.util.has
import org.lain.engine.util.remove
import org.lain.engine.world.*

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

    init {
        instantiatePlayer(mainPlayer)
        client.eventBus.onMainPlayerInstantiated(client, this, mainPlayer)
        client.renderer.setupGameSession(this)
        setup.playerList.players.forEach { instantiateLowDetailedPlayer(it) }
        player.items.forEach { itemStorage.add(it.uuid, clientItem(world, it)) }
    }

    fun tick() {
        chatManager.tick()
        val players = playerStorage.getAll()

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        for (player in players) {
            if (player.isLowDetailed) continue

            val playerItems = player.items
            supplyPlayerInventoryItemsLocation(player, playerItems)
            updatePlayerInteractions(player, false)
            updateGunState(playerItems)
            updatePlayerMovement(player, movementDefaultAttributes, movementSettings, true)

            if (player.pos.squaredDistanceTo(mainPlayer.pos) > playerSynchronizationRadius * playerSynchronizationRadius) {
                player.isLowDetailed = true
            }

            handleBulletFireShakes(client.camera, world, playerItems)
            handleGunShotTags(player, playerItems)

            player.handle<ShakeScreenComponent> {
                client.camera.stress(stress)
                player.remove<ShakeScreenComponent>()
            }

            player.remove<InteractionComponent>()?.let {
                if (player == mainPlayer && it.interaction !is Interaction.SlotClick) {
                    handler.onInteraction(it.interaction)
                }
            }
        }

        chatBubbleList.cleanup()
        world.events<WorldSoundPlayRequest>().clear()
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