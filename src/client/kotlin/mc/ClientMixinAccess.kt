package org.lain.engine.client.mc

import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.require
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.getClientItem
import org.lain.engine.client.render.world.RenderStateComponent
import org.lain.engine.client.render.world.modelPartOf
import org.lain.engine.client.render.world.setEngineState
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Writable
import org.lain.engine.item.getTooltip
import org.lain.engine.item.resolveItemAsset
import org.lain.engine.mc.engine
import org.lain.engine.mc.engineId
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Hearing
import org.lain.engine.player.processLeftClickInteraction
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectValue

object ClientMixinAccess {
    private val client by injectClient()
    private val minecraftClient by injectMinecraftClient()
    private val mainPlayer get() = client.gameSession?.mainPlayer
    private var mainPlayerHearing: Hearing? = null
    private var resources: ResourceList? = null
    var chatClipboardCopyTicksElapsed = 0
    var takeOffEquipPressed = false

    fun getEngineClient() = client

    fun onDisconnect() {
        minecraftClient.onDisconnect()
    }

    fun tick() {
        chatClipboardCopyTicksElapsed += 1
    }

    fun onSetWorldProjectionMatrix() {}

    fun getTooltip(engineItem: EngineItem, advanced: Boolean): List<String> {
        with(client.gameSession?.world ?: return emptyList()) {
            return engineItem.getTooltip(advanced)
        }
    }

    fun getWriteable(itemStack: ItemStack): Writable? {
        val gameSession = client.gameSession ?: return null
        return with(gameSession.world) { getEngineItem(itemStack)?.getComponent<Writable>() }
    }

    fun onBookClose(itemStack: ItemStack, writable: Writable, pages: List<String>) {
        val item = getEngineItem(itemStack) ?: return
        val gameSession = client.gameSession ?: return
        writable.contents = pages
        with(gameSession.world) {
            client.handler.onWriteableContentsUpdate(
                item.requireComponent<PersistentIdComponent>().id,
                pages
            )
        }
    }

    fun editVolume(sound: SoundInstance, volume: Float, category: SoundSource): Float? {
        if (category == SoundSource.UI || category == SoundSource.MUSIC) return null
        if (sound.sound?.location?.path == "builtin/tinnitus") return null
        val loss = mainPlayerHearing?.loss ?: return null
        return volume * (1f - loss).coerceIn(0.01f, 1f)
    }

    fun onMainPlayerInstantiated(mainPlayer: EnginePlayer) {
        mainPlayerHearing = mainPlayer.require<Hearing>()
    }

    fun updatePlayerRenderState(playerLikeEntity: Avatar, playerEntityRenderState: AvatarRenderState, model: PlayerModel) {
        if (playerLikeEntity !is Player) return
        val entityTable by injectEntityTable()
        val enginePlayer = entityTable.client.getPlayer(playerLikeEntity) ?: return
        val renderState = enginePlayer.get<RenderStateComponent>()?.renderState ?: return
        renderState.detachedEquipment.forEach { it.playerModelPart = modelPartOf(it.playerPart, model) }
        playerEntityRenderState.setEngineState(renderState)
    }

    private val identifierCache = mutableMapOf<String, Identifier>()

    fun getEngineItemModel(itemStack: ItemStack): Identifier? {
        if (!client.gameSessionActive) return null
        val engineItem = itemStack.engine()?.getClientItem() ?: return null
        return with(client.gameSession?.world ?: return null) {
            resolveItemAsset(engineItem).let { path ->
                identifierCache.computeIfAbsent(resolveItemAsset(engineItem)) { engineId(path) }
            }
        }
    }

    fun getEngineItem(itemStack: ItemStack): EngineItem? {
        return client.gameSession?.let {
            itemStack.engine()?.getClientItem()
        }
    }

    fun predictItemLeftClickInteraction(): Boolean {
        val player = client.gameSession?.mainPlayer ?: return false
        return with(client.gameSession?.world ?: return false) { processLeftClickInteraction(player) }
    }

    fun canBreakBlocks(): Boolean {
        val gameSession = client.gameSession ?: return false
        return with(gameSession.world) { !processLeftClickInteraction(gameSession.mainPlayer) }
    }

    fun onCursorStackSet(itemStack: ItemStack?) {
        val engineItem = if (itemStack?.isEmpty == true) null else itemStack?.engine()?.getClientItem()
        client.handler.onCursorItem(engineItem)
    }

    fun isEngineLoaded(): Boolean {
        val isPlayerInstantiated = client.gameSession?.mainPlayer != null
        return isPlayerInstantiated
    }

    fun onScroll(vertical: Float) {
        client.onScroll(vertical)
    }

    fun isScrollAllowed(): Boolean {
        val changingSpeed = client.gameSession?.movementManager?.locked?.not() ?: false
        val concentration = client.gameSession?.inspection?.concentration ?: false
        return !changingSpeed && !concentration
    }

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible

    fun isHotbarIndicatorsVisible() = client.options.crosshairIndicatorVisible

    fun onClientPlayerEntityInitialized(entity: LocalPlayer) {
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
}