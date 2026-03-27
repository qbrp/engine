package org.lain.engine.client.mc

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.entity.PlayerLikeEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.getClientItem
import org.lain.engine.client.mc.render.world.RenderStateComponent
import org.lain.engine.client.mc.render.world.modelPartOf
import org.lain.engine.client.mc.render.world.setEngineState
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Writable
import org.lain.engine.item.resolveItemAsset
import org.lain.engine.mc.engine
import org.lain.engine.player.processLeftClickInteraction
import org.lain.engine.util.EngineId
import org.lain.engine.util.component.get
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectValue

object ClientMixinAccess {
    private val client by injectClient()
    private val mainPlayer get() = client.gameSession?.mainPlayer
    private var resources: ResourceList? = null
    var chatClipboardCopyTicksElapsed = 0
    var takeOffEquipPressed = false

    fun getEngineClient() = client

    fun tick() {
        chatClipboardCopyTicksElapsed += 1
    }

    fun getWriteable(itemStack: ItemStack): Writable? {
        return getEngineItem(itemStack)?.get<Writable>()
    }

    fun onBookClose(itemStack: ItemStack, writable: Writable, pages: List<String>) {
        val item = getEngineItem(itemStack) ?: return
        writable.contents = pages
        client.handler.onWriteableContentsUpdate(item.uuid, pages)
    }

    fun updatePlayerRenderState(playerLikeEntity: PlayerLikeEntity, playerEntityRenderState: PlayerEntityRenderState, model: PlayerEntityModel) {
        if (playerLikeEntity !is PlayerEntity) return
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
        return resolveItemAsset(engineItem).let { path ->
            identifierCache.computeIfAbsent(resolveItemAsset(engineItem)) { EngineId(path) }
        }
    }

    fun getEngineItem(itemStack: ItemStack): EngineItem? {
        return client.gameSession?.let {
            itemStack.engine()?.getClientItem()
        }
    }

    fun predictItemLeftClickInteraction(): Boolean {
        val player = client.gameSession?.mainPlayer ?: return false
        return processLeftClickInteraction(player)
    }

    fun canBreakBlocks(): Boolean {
        val player = client.gameSession?.mainPlayer ?: return false
        return !processLeftClickInteraction(player)
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

    fun isScrollAllowed() = client.gameSession?.movementManager?.locked ?: true

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible

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
}