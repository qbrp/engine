package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.AbstractClientPlayer
import org.lain.engine.Constants
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.player.EnginePlayerRenderState
import org.lain.engine.mc.Text
import org.lain.engine.mc.compat.removeGenderCacheEntry
import org.lain.engine.mc.literalText
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.Look
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class LooksWheel<T>(
    private val skinTextureManager: SkinTextureManager,
    looks: List<Entry<T>>,
    val onSelectLook: (Entry<T>?) -> Unit,
) : AbstractWidget(0, 0, 0, 0, literalText("Character wheel")) {
    private var initial = looks.find { it.isInitial }
    data class Entry<T>(
        val look: Look,
        val text: Text,
        val profile: CharacterProfile,
        val result: T,
        val isInitial: Boolean,
        val uuid: UUID = UUID.randomUUID(),
    )

    inner class Item(
        val entry: Entry<T>,
        val previewPlayer: CharacterPreviewPlayer
    )

    private var selectedIndex = 0
    private var items = initItems(looks)
    val selectedItem
        get() = items.getOrNull(selectedIndex.coerceIn(items.indices))

    fun reloadItems(looks: List<Entry<T>>) {
        clearGenderCaches()
        items = initItems(looks)
    }

    private fun initItems(looks: List<Entry<T>>): List<Item> {
        val list = looks.toMutableList()
        val initial = looks.find { it.isInitial }
            .also { this.initial = it }

        if (initial != null) {
            list.remove(initial)
            list.addFirst(initial)
        }
        val level = MinecraftClient.level ?: return emptyList()

        if (looks.isNotEmpty()) {
            selectedIndex = selectedIndex.coerceIn(looks.indices)
        }

        return list
            .map {
                val profile = it.profile
                Item(
                    it,
                    createCharacterPreviewPlayer(
                        level,
                        profile,
                        uuid = it.uuid,
                        EnginePlayerRenderState(
                            eyeLevel = profile.appearance.eyeLevel
                        ),
                    ) { skinTextureManager.getOrDownloadTexture(it.look) }
                )
            }
    }

    fun init(screen: Screen) {
        width = screen.width
        height = screen.height
    }

    fun tick() {
        items.forEach { it.previewPlayer.tick() }
    }

    fun close() {
        clearGenderCaches()
    }

    private fun clearGenderCaches() {
        if (Constants.GENDER_MOD_AVAILABLE) {
            items.forEach { removeGenderCacheEntry(it.entry.uuid) }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return if (keyCode == InputConstants.KEY_RETURN) {
            onSelectLook(items.getOrNull(selectedIndex)?.entry)
            true
        } else if (keyCode == InputConstants.KEY_RIGHT) {
            selectedIndex = (selectedIndex + 1).coerceIn(items.indices)
            true
        } else if (keyCode == InputConstants.KEY_LEFT) {
            selectedIndex = (selectedIndex - 1).coerceIn(items.indices)
            true
        } else {
            super.keyPressed(keyCode, scanCode, modifiers)
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (items.isEmpty()) return false
        selectedIndex = (selectedIndex - verticalAmount.sign()).coerceIn(items.indices)
        return true
    }

    private fun applyCursorLook(
        entity: AbstractClientPlayer,
        entityX: Int,
        entityY: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val yaw = ((mouseX - entityX) / 8f).coerceIn(-35f, 35f)
        applyRotation(entity, 180f - yaw * 0.35f, -yaw)
    }

    private fun applyRotation(
        entity: AbstractClientPlayer,
        bodyYaw: Float,
        relativeHeadYaw: Float,
    ) {
        val headYaw = bodyYaw + relativeHeadYaw
        entity.yBodyRot = bodyYaw
        entity.setYRot(headYaw)
        entity.yHeadRot = headYaw
    }

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        f: Float
    ) {
        val font = MinecraftClient.font
        if (items.isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                "Нет персонажей",
                width / 2,
                height / 2,
                0xFFFFFFFF.toInt()
            )
            return
        }
        val centerX = width / 2
        val centerY = (height * 0.5f).roundToInt() + 64
        val spacing = (width / 4).coerceIn(MIN_SPACING, MAX_SPACING)

        val visibleCharacters = items
            .mapIndexed { index, character -> index to character }
            .filter { (index, _) -> abs(index - selectedIndex) <= VISIBLE_SIDE_CHARACTERS }
            .sortedByDescending { (index, _) -> abs(index - selectedIndex) }

        visibleCharacters.forEach character@{ (index, item) ->
            val entry = item.entry
            val entity = item.previewPlayer

            val offset = index - selectedIndex

            val itemX = centerX + offset * spacing
            val distance = abs(itemX - centerX).toFloat() / centerX.coerceAtLeast(1)
            val visualScale = (1f - distance * 0.55f).coerceIn(MIN_VISUAL_SCALE, 1f)

            val widget = CharacterPreviewWidget(entity, entry.profile)

            if (index == selectedIndex) {
                entity.setOnGround(true)
                widget.applyCursorLook(itemX, mouseX)
            } else {
                entity.setOnGround(false)
                val yaw = -offset * SIDE_CHARACTER_ROTATION
                widget.applyRotation(180f + yaw, yaw)
            }

            widget.computeScale(visualScale)
            widget.computePosition(itemX.toFloat(), centerY.toFloat())
            widget.y -= PREVIEW_VERTICAL_PADDING
            widget.y = widget.y.coerceAtLeast(MIN_PREVIEW_HEIGHT.toFloat())
            widget.render(guiGraphics)

            val textY = centerY + (14 * visualScale).roundToInt()
            guiGraphics.drawCenteredString(font, entry.text, itemX, textY, 0xFFFFFFFF.toInt())
        }
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

    private fun Double.sign(): Int {
        return kotlin.math.sign(this).toInt()
    }

    companion object {
        private const val VISIBLE_SIDE_CHARACTERS = 5
        private const val MIN_SPACING = 72
        private const val MAX_SPACING = 110
        private const val MIN_PREVIEW_HEIGHT = 112
        private const val PREVIEW_VERTICAL_PADDING = 12
        private const val MIN_VISUAL_SCALE = 0.45f
        private const val SIDE_CHARACTER_ROTATION = 14f
    }
}
