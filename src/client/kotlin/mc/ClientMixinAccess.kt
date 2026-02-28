package org.lain.engine.client.mc

import net.minecraft.client.model.ModelPart
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.PlayerLikeEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.getClientItem
import org.lain.engine.client.mc.render.EquipmentRenderState
import org.lain.engine.client.mc.render.setEquipment
import org.lain.engine.client.mc.render.setMainArmPose
import org.lain.engine.client.mc.render.setMinorArmPose
import org.lain.engine.client.resources.Assets
import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.item.*
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.mc.engine
import org.lain.engine.player.*
import org.lain.engine.util.*

object ClientMixinAccess {
    private val client by injectClient()
    private val mainPlayer get() = client.gameSession?.mainPlayer
    private var resources: ResourceList? = null
    private val outfitItemStacksCache = mutableMapOf<Identifier, Outfit>()
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

    data class PlayerEquipmentItemStacks(val stacks: MutableMap<EquippedItem, ItemStack>) : Component

    fun modelPartOf(part: PlayerPart, model: PlayerEntityModel) = when(part) {
        PlayerPart.HEAD -> model.head
        PlayerPart.LEFT_ARM -> model.leftArm
        PlayerPart.RIGHT_ARM -> model.rightArm
        PlayerPart.LEFT_PALM -> model.leftArm
        PlayerPart.RIGHT_PALM -> model.rightArm
        PlayerPart.BODY -> model.body
        PlayerPart.LEFT_LEG -> model.leftLeg
        PlayerPart.RIGHT_LEG -> model.rightLeg
        PlayerPart.LEFT_FEET -> model.leftLeg
        PlayerPart.RIGHT_FEET -> model.rightLeg
    }

    fun transformationOf(part: PlayerPart, model: PlayerEntityModel) = when(part) {
        PlayerPart.HEAD -> model.head
        PlayerPart.LEFT_ARM -> model.leftArm
        PlayerPart.RIGHT_ARM -> model.rightArm
        PlayerPart.LEFT_PALM -> model.leftArm
        PlayerPart.RIGHT_PALM -> model.rightArm
        PlayerPart.BODY -> model.body
        PlayerPart.LEFT_LEG -> model.leftLeg
        PlayerPart.RIGHT_LEG -> model.rightLeg
        PlayerPart.LEFT_FEET -> model.leftLeg
        PlayerPart.RIGHT_FEET -> model.rightLeg
    }

    fun createModelPartEquipmentRenderStates(entity: PlayerEntity, player: EnginePlayer, playerModelPart: PlayerEntityModel, modelPart: ModelPart): List<EquipmentRenderState> {
        return player.outfit
            .filter { (outfit, assets) -> outfit.display is OutfitDisplay.Separated && modelPartOf(outfit.parts.first(), playerModelPart) == modelPart }
            .map {
                val state = ItemRenderState()
                val equipmentStacks = player.getOrSet { PlayerEquipmentItemStacks(mutableMapOf()) }.stacks
                val itemStack = equipmentStacks.computeIfAbsent(it) { (_, model) ->
                    val stack = ITEM_STACK_MATERIAL.copy()
                    stack.set(
                        DataComponentTypes.ITEM_MODEL,
                        EngineId(model.assets["default"] ?: "missingno")
                    )
                    stack
                }
                MinecraftClient.itemModelManager.updateForLivingEntity(
                    state,
                    itemStack,
                    ItemDisplayContext.HEAD,
                    entity,
                )
                EquipmentRenderState(state, modelPart)
            }
    }

    fun updatePlayerRenderState(playerLikeEntity: PlayerLikeEntity, playerEntityRenderState: PlayerEntityRenderState, model: PlayerEntityModel) {
        if (playerLikeEntity !is PlayerEntity) return
        val entityTable by injectEntityTable()
        val enginePlayer = entityTable.client.getPlayer(playerLikeEntity) ?: return
        val inventory = enginePlayer.require<PlayerInventory>()
        val extends = enginePlayer.require<ArmStatus>().extend

        val selectorLeft = isGunWithoutSelector(inventory.offHandItem)
        playerEntityRenderState.setMainArmPose(
           armPoseOf(true, extends, isGun(inventory.mainHandItem), isGunWithoutSelector(inventory.mainHandItem), selectorLeft)
       )
        playerEntityRenderState.setMinorArmPose(
            armPoseOf(false, extends, isGun(inventory.offHandItem), selectorLeft, false)
        )
        playerEntityRenderState.setEquipment(
            model.parts.flatMap { createModelPartEquipmentRenderStates(playerLikeEntity, enginePlayer, model, it) }
        )
    }

    private val identifierCache = mutableMapOf<String, Identifier>()

    fun getEngineItemModel(itemStack: ItemStack): Identifier? {
        if (!client.gameSessionActive) return null
        val engineItem = itemStack.engine()?.getClientItem() ?: return null
        return resolveItemAsset(engineItem).let { path ->
            identifierCache.computeIfAbsent(resolveItemAsset(engineItem)) { EngineId(path) }
        }
    }

    private fun isGun(item: EngineItem?) = item?.has<Gun>() == true

    private fun isGunWithoutSelector(item: EngineItem?): Boolean {
        return (item?.get<Gun>() ?: return false).mode != FireMode.SELECTOR
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