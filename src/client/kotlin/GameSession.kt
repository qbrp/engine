package org.lain.engine.client

import org.lain.engine.client.chat.ClientEngineChatManager
import org.lain.engine.client.chat.PlayerVocalRegulator
import org.lain.engine.client.chat.PlayerVolume
import org.lain.engine.client.control.MovementManager
import org.lain.engine.client.render.ChatBubbleManager
import org.lain.engine.client.util.SPECTATOR_NOTIFICATION
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.MovementDefaultAttributes
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.stamina
import org.lain.engine.player.updatePlayerMovement
import org.lain.engine.server.ServerId
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.has
import org.lain.engine.world.World
import org.lain.engine.world.pos

class GameSession(
    val server: ServerId,
    val world: World,
    setup: ClientboundSetupData,
    player: ServerPlayerData,
    val handler: ClientHandler,
    val client: EngineClient,
) {
    val chatEventBus = client.chatEventBus
    var playerSynchronizationRadius: Int = setup.settings.playerSynchronizationRadius

    val playerStorage = ClientPlayerStorage()
    val movementManager = MovementManager(handler)
    val chatBubbleManager = ChatBubbleManager(client.options)
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
    }

    fun tick() {
        val players = playerStorage.getAll()

        movementManager.stamina = mainPlayer.stamina
        if (mainPlayer.has<SpawnMark>()) {
            client.removeLittleNotification(SPECTATOR_NOTIFICATION)
        }

        for (player in players) {
            if (player.isLowDetailed) continue

            updatePlayerMovement(player, movementDefaultAttributes, movementSettings)

            if (player.pos.squaredDistanceTo(mainPlayer.pos) > playerSynchronizationRadius * playerSynchronizationRadius) {
                player.isLowDetailed = true
            }
        }
    }

    fun instantiatePlayer(player: Player) {
        playerStorage.add(player.id, player)
        client.onPlayerInstantiate(player)
    }

    fun destroy() {
        playerStorage.clear()
        client.renderer.invalidate()
        handler.disable()
    }

    fun getPlayer(id: PlayerId) = playerStorage.get(id)
}