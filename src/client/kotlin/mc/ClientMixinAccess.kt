package org.lain.engine.client.mc

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.PlayerLikeEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.getClientItem
import org.lain.engine.client.mc.render.setMainArmPose
import org.lain.engine.client.mc.render.setMinorArmPose
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Gun
import org.lain.engine.item.Writable
import org.lain.engine.mc.engine
import org.lain.engine.player.*
import org.lain.engine.util.*

object ClientMixinAccess {
    private val client by injectClient()
    private val mainPlayer get() = client.gameSession?.mainPlayer
    private var resources: ResourceList? = null
    var chatClipboardCopyTicksElapsed = 0

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

    fun updatePlayerRenderState(playerLikeEntity: PlayerLikeEntity, playerEntityRenderState: PlayerEntityRenderState, f: Float) {
        if (playerLikeEntity !is PlayerEntity) return
        val entityTable by injectEntityTable()
        val enginePlayer = entityTable.client.getPlayer(playerLikeEntity) ?: return
        val inventory = enginePlayer.require<PlayerInventory>()
        val extends = enginePlayer.require<ArmStatus>().extend

        val selectorLeft = isGunWithSelector(inventory.offHandItem)
        playerEntityRenderState.setMainArmPose(
           armPoseOf(true, extends, isGun(inventory.mainHandItem), isGunWithSelector(inventory.mainHandItem), selectorLeft)
       )
        playerEntityRenderState.setMinorArmPose(
            armPoseOf(false, extends, isGun(inventory.offHandItem), isGunWithSelector(inventory.offHandItem), false)
        )
    }

    private fun isGun(item: EngineItem?) = item?.has<Gun>() == true

    private fun isGunWithSelector(item: EngineItem?) = item?.get<Gun>()?.selector == false

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

    fun onLeftMouseClick() {
        mainPlayer?.setInteraction(Interaction.LeftClick)
    }

    fun onCursorStackSet(itemStack: ItemStack?) {
        val engineItem = if (itemStack?.isEmpty == true) null else itemStack?.engine()?.getClientItem()
        client.handler.onCursorItem(engineItem)
    }

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