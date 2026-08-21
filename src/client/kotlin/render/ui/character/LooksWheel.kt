package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.CharacterSkin
import org.lain.engine.mc.Text
import org.lain.engine.mc.literalText
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.computeCharacterModel
import org.lain.engine.player.scale
import kotlin.math.abs
import kotlin.math.roundToInt

class LooksWheel<T>(
    private val skinTextureManager: SkinTextureManager,
    private val initialLook: Entry<T>?,
    looks: List<Entry<T>>,
    val onSelectLook: (Entry<T>?) -> Unit,
) : AbstractWidget(0, 0, 0, 0, literalText("Character wheel")) {
    data class Entry<T>(
        val look: Look,
        val text: Text,
        val profile: CharacterProfile,
        val result: T,
        val key: String = look.id
    )

    private var selectedIndex = 0

    private val entries = run {
        val list = looks.toMutableList()
        if (initialLook != null) {
            list.removeIf { it.key == initialLook.key }
            list.addFirst(initialLook)
        }
        list.toList()
    }

    init {
        if (looks.isNotEmpty()) {
            selectedIndex = selectedIndex.coerceIn(looks.indices)
        }
    }

    fun init(screen: Screen) {
        width = screen.width
        height = screen.height
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.key == InputConstants.KEY_RETURN) {
            onSelectLook(entries.getOrNull(selectedIndex))
            true
        } else {
            super.keyPressed(keyEvent)
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (entries.isEmpty()) return false
        selectedIndex = (selectedIndex - verticalAmount.sign()).coerceIn(entries.indices)
        return true
    }

    private fun applyCursorLook(
        renderState: AvatarRenderState,
        entityX: Int,
        entityY: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        val yaw = ((mouseX - entityX) / 8f).coerceIn(-35f, 35f)
        renderState.bodyRot = 180f + -yaw * 0.35f
        renderState.yRot = -yaw
    }

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        f: Float
    ) {
        val font = MinecraftClient.font
        if (entries.isEmpty()) {
            guiGraphics.drawCenteredString(font, "Нет персонажей", width / 2, height / 2, 0xFFFFFFFF.toInt())
            return
        }
        val centerX = width / 2
        val baseY = (height * 0.5f).roundToInt() + 64
        val spacing = (width / 4).coerceIn(MIN_SPACING, MAX_SPACING)

        val visibleCharacters = entries
            .mapIndexed { index, character -> index to character }
            .filter { (index, _) -> abs(index - selectedIndex) <= VISIBLE_SIDE_CHARACTERS }
            .sortedByDescending { (index, _) -> abs(index - selectedIndex) }

        visibleCharacters.forEach character@{ (index, entry) ->
            val offset = index - selectedIndex

            val itemX = centerX + offset * spacing
            val distance = abs(itemX - centerX).toFloat() / centerX.coerceAtLeast(1)
            val visualScale = (1f - distance * 0.55f).coerceIn(MIN_VISUAL_SCALE, 1f)
            val heightScale = entry.profile.height.scale
            val entityScale = BASE_ENTITY_SCALE * visualScale * heightScale
            val look = entry.look
            val model = computeCharacterModel(
                entry.profile.bodyType,
                entry.profile.biologicalCategory,
                entry.profile.biologicalSex
            )
            val renderState = createCharacterPreviewRenderState(
                entry.profile,
                CharacterSkin(skinTextureManager.getOrDownloadTexture(look), model),
                1f
            )

            if (index == selectedIndex) {
                applyCursorLook(renderState, itemX, baseY, mouseX, mouseY)
            } else {
                renderState.yRot = -offset * SIDE_CHARACTER_ROTATION
                renderState.bodyRot = 180f + renderState.yRot
            }

            val headRotation = Quaternionf().rotateX(renderState.xRot * DEGREES_TO_RADIANS)
            val entityRotation = Quaternionf()
                .rotateZ(Math.PI.toFloat())
                .mul(headRotation)
            val boundsWidth = (renderState.boundingBoxWidth * entityScale * 2.4f)
                .roundToInt()
                .coerceAtLeast(MIN_ITEM_BOUNDS)
            val boundsHeight = (renderState.boundingBoxHeight * entityScale + BOUNDS_VERTICAL_PADDING)
                .roundToInt()
                .coerceAtLeast(MIN_ITEM_BOUNDS)

            guiGraphics.submitEntityRenderState(
                renderState,
                entityScale,
                Vector3f(0f, renderState.boundingBoxHeight / 2f + ENTITY_Y_OFFSET, 0f),
                entityRotation,
                headRotation,
                itemX - boundsWidth / 2,
                baseY - boundsHeight,
                itemX + boundsWidth / 2,
                baseY
            )

            val textY = baseY + (14 * visualScale).roundToInt()
            guiGraphics.drawCenteredString(font, entry.text, itemX, textY, 0xFFFFFFFF.toInt())
        }
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

    private fun Double.sign(): Int {
        return when {
            this > 0.0 -> 1
            this < 0.0 -> -1
            else -> 0
        }
    }

    companion object {
        private const val VISIBLE_SIDE_CHARACTERS = 3
        private const val MIN_SPACING = 72
        private const val MAX_SPACING = 140
        private const val ITEM_BOUNDS = 112
        private const val MIN_ITEM_BOUNDS = 112
        private const val BOUNDS_VERTICAL_PADDING = 24
        private const val BASE_ENTITY_SCALE = 60f
        private const val MIN_VISUAL_SCALE = 0.45f
        private const val SIDE_CHARACTER_ROTATION = 14f
        private const val DEGREES_TO_RADIANS = 0.017453292f
        private const val ENTITY_Y_OFFSET = 0.0625f
    }
}
