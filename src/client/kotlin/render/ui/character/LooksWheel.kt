package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import org.lain.engine.Constants
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.player.EnginePlayerRenderState
import org.lain.engine.mc.Text
import org.lain.engine.mc.compat.removeGenderCacheEntry
import org.lain.engine.mc.literalText
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.Look
import org.lain.engine.util.math.lerp
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
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
        val previewPlayer: CharacterPreviewPlayer,
        val widget: CharacterPreviewWidget = CharacterPreviewWidget(previewPlayer, entry.profile),
        var y: Float? = null
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
            val item = selectedItem ?: return false
            item.previewPlayer.playAnimation(CharacterAnimations.all.next())
            onSelectLook(item.entry)
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
            val widget = item.widget
            val entity = item.previewPlayer

            val offset = index - selectedIndex

            val itemX = centerX + offset * spacing
            val distance = abs(itemX - centerX).toFloat() / centerX.coerceAtLeast(1)
            val visualScale = (1f - distance * 0.55f).coerceIn(MIN_VISUAL_SCALE, 1f)

            if (index == selectedIndex) {
                entity.setOnGround(true)
                widget.applyCursorLook(itemX, mouseX)
            } else {
                entity.setOnGround(false)
                val yaw = -offset * SIDE_CHARACTER_ROTATION
                widget.applyRotation(180f + yaw, yaw)
            }

            val targetX = itemX.toFloat()
            val targetY = (centerY.toFloat() - PREVIEW_VERTICAL_PADDING).coerceAtLeast(MIN_PREVIEW_HEIGHT)

            item.y = lerp(item.y ?: targetY, targetY, 1f - 0.2f.pow(f))
            if (widget.x == 0f) {
                widget.x = targetX
            }

            widget.computeScale(visualScale)
            widget.computePosition(
                lerp(widget.x, targetX, 1f - 0.2f.pow(f)),
                item.y ?: targetY
            )
            widget.render(guiGraphics)

            val textY = (item.y ?: targetY) + (14 * visualScale)
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(widget.x, textY, 0f)
            guiGraphics.drawCenteredString(font, entry.text, 0, 0, 0xFFFFFFFF.toInt())
            guiGraphics.pose().popPose()
        }
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

    private fun Double.sign(): Int {
        return kotlin.math.sign(this).toInt()
    }

    companion object {
        private const val VISIBLE_SIDE_CHARACTERS = 5f
        private const val MIN_SPACING = 72
        private const val MAX_SPACING = 110
        private const val MIN_PREVIEW_HEIGHT = 112f
        private const val PREVIEW_VERTICAL_PADDING = 12
        private const val MIN_VISUAL_SCALE = 0.45f
        private const val SIDE_CHARACTER_ROTATION = 14f
    }
}
