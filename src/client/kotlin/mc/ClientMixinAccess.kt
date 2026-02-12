package org.lain.engine.client.mc

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.chat.LiteralSystemEngineChatMessage
import org.lain.engine.client.getClientItem
import org.lain.engine.client.mc.render.TransformationsEditorScreen
import org.lain.engine.client.render.CD
import org.lain.engine.client.render.VOICE_WARNING
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.item.*
import org.lain.engine.mc.engine
import org.lain.engine.mc.engineItem
import org.lain.engine.player.Interaction
import org.lain.engine.player.PlayerId
import org.lain.engine.player.processLeftClickInteraction
import org.lain.engine.player.setInteraction
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT
import org.lain.engine.util.Timestamp
import org.lain.engine.util.get
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectValue
import org.lain.engine.util.math.VEC3_ZERO
import org.lain.engine.util.math.roundToInt
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.util.text.parseMiniMessageLegacy
import org.lwjgl.glfw.GLFW

object ClientMixinAccess {
    private val client by injectClient()
    private var resources: ResourceList? = null
    private var developerModeKeyPressedTick = 0L
    val typingPlayers = mutableSetOf<PlayerListEntry>()
    var chatClipboardCopyTicksElapsed = 0

    fun tick() {
        chatClipboardCopyTicksElapsed += 1
    }

    fun registerEndpoints() {
        CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT.registerClientReceiver {
            val player = getPlayerListEntry(player) ?: return@registerClientReceiver
            if (player.profile.id != MinecraftClient.networkHandler?.profile?.id || client.developerMode) {
                typingPlayers.add(player)
            }
        }

        CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT.registerClientReceiver {
            val player = getPlayerListEntry(player) ?: run {
                typingPlayers.clear() // Если что-то сломалось
                return@registerClientReceiver
            }
            typingPlayers.remove(player)
        }
    }

    private fun getPlayerListEntry(id: PlayerId): PlayerListEntry? {
        return MinecraftClient.networkHandler?.playerList?.find { it.profile.id == id.value }
    }

    fun getDisplayName(player: PlayerListEntry): Text {
        return client.gameSession?.getPlayer(PlayerId(player.profile.id)).let {
            it?.displayNameMiniMessage?.parseMiniMessageLegacy() ?: player.displayName ?: Text.of(player.profile.name)
        }
    }

    fun getEngineItem(itemStack: ItemStack) = itemStack.engine()?.getClientItem()

    fun predictItemLeftClickInteraction(): Boolean {
        val player = client.gameSession?.mainPlayer ?: return false
        player.setInteraction(Interaction.LeftClick)
        return processLeftClickInteraction(player)
    }

    fun canBreakBlocks(): Boolean {
        val player = client.gameSession?.mainPlayer ?: return false
        return !processLeftClickInteraction(player)
    }

    fun onLeftMouseClick() {
        client.handler.onInteraction(Interaction.LeftClick)
    }

    fun onCursorStackSet(itemStack: ItemStack?) {
        val engineItem = itemStack?.engineItem()
        client.handler.onCursorItem(engineItem)
    }

    fun isGunWithSelector(item: EngineItem) = item.get<Gun>()?.selector == false

    fun onSlotEngineItemClicked(cursorItem: EngineItem, item: EngineItem) {
        if (MinecraftClient.currentScreen is CreativeInventoryScreen) {
            client.handler.onInteraction(Interaction.SlotClick(cursorItem, item))
        }
    }

    fun isEngineLoaded(): Boolean {
        val isPlayerInstantiated = client.gameSession?.mainPlayer != null
        return isPlayerInstantiated
    }

    fun onScroll(vertical: Float) {
        client.onScroll(vertical)
    }

    fun isScrollAllowed() = client.gameSession?.movementManager?.locked ?: true

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible

    fun onKey(key: Int): Boolean = with(client) {
        if (isControlDown() && developerMode) {
            if (ticks - developerModeKeyPressedTick > 20) {
                developerModeKeyPressedTick = ticks
                if (key == GLFW.GLFW_KEY_1) {
                    audioManager.playSound(
                        SoundPlay(
                            SoundEvent(
                                SoundEventId("debug_pig_scream"),
                                listOf(
                                    SoundSource(
                                        SoundId("funny/pig-scream"),
                                        1f,
                                        1f,
                                        1,
                                        16
                                    )
                                )
                            ),
                            VEC3_ZERO,
                            EngineSoundCategory.MASTER,
                        )
                    )
                    applyLittleNotification(
                        LittleNotification(
                            "Проигран звук",
                            "funny/pig-scream.ogg",
                            sprite = CD,
                        ),
                    )
                } else if (key == GLFW.GLFW_KEY_2) {
                    val player = MinecraftClient.player ?: return@with true
                    val mainHandItemStack = player.mainHandStack
                    val offHandItemStack = player.offHandStack
                    val itemStack = if (mainHandItemStack.isEmpty) offHandItemStack else mainHandItemStack
                    if (client.developerMode && player.activeItem != null && !itemStack.isEmpty) {
                        MinecraftClient.setScreen(TransformationsEditorScreen(itemStack))
                    }
                } else if (key == GLFW.GLFW_KEY_3) {
                    val gameSession = gameSession ?: return@with true
                    val start = Timestamp()
                    repeat(100) {
                        gameSession.chatManager.addMessage(
                            LiteralSystemEngineChatMessage(gameSession, roundToInt(Math.random() * 999999).toString())
                        )
                    }

                    val end = start.timeElapsed()
                    audioManager.playUiNotificationSound()
                    applyLittleNotification(
                        LittleNotification(
                            "Добавлено 100 случайных сообщений",
                            "Время: ${end} мл.",
                            sprite = VOICE_WARNING,
                        ),
                    )
                } else if (key == GLFW.GLFW_KEY_4) {
                    client.camera.stress(2f)
                } else if (key == GLFW.GLFW_KEY_5) {
                    client.acousticDebug = !client.acousticDebug
                } else {
                    return@with false
                }
            }
            return@with true
        }
        return@with false
    }

    fun onClientPlayerEntityInitialized(entity: ClientPlayerEntity) {
        val entityTable by injectEntityTable()
        val table = entityTable.client
        val player = table.getPlayer(entity)
        if (player != null) {
            table.removePlayer(entity)
            table.setPlayer(entity, player)
        }
    }

    fun sendChatMessage(content: String) {
        val gameSession = client.gameSession ?: return
        gameSession.chatManager.sendMessage(content)
    }

    fun getResourceList(): ResourceList {
        return resources ?: findAssets().also { resources = it }
    }

    fun getChatWidth() = client.options.chatFieldWidth

    fun getChatSize() = client.options.chatFieldSize

    fun getAssets(): Assets {
        return client.resources.assets
    }

    fun getCamera() = client.camera

    fun createResourceList(): ResourceList {
        resources = findAssets()
        return resources!!
    }

    fun getKeybindManager(): KeybindManager = injectValue()

    fun deleteChatMessage(message: AcceptedMessage) = client.gameSession?.chatManager?.deleteMessage(message.id)

    fun sendingMessageClosesChat() = client.options.chatInputSendClosesChat

    fun renderChatBubbles(
        matrices: MatrixStack,
        camera: Camera,
        vertexConsumers: VertexConsumerProvider.Immediate,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ) {
        if (client.options.hideChatBubblesWithUi && client.renderer.hudHidden) return
        org.lain.engine.client.mc.render.renderChatBubbles(
            matrices,
            camera,
            vertexConsumers,
            MinecraftClient.textRenderer,
            cameraX,
            cameraY,
            cameraZ,
            client.options.chatBubbleScale,
            client.options.chatBubbleHeight,
            client.options.chatBubbleBackgroundOpacity,
            client.gameSession?.chatBubbleList?.bubbles ?: emptyList(),
            client.options.chatBubbleIgnoreLightLevel,
            MinecraftClient.renderTickCounter.fixedDeltaTicks
        )
    }
}