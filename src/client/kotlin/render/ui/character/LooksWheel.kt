package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.player.AbstractClientPlayer
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

    internal inner class Item(
        val entry: Entry<T>,
        val previewPlayer: CharacterPreviewPlayer
    )

    private var selectedIndex = 0

    private val items = initItems(looks)

    private fun initItems(looks: List<Entry<T>>): List<Item> {
        val list = looks.toMutableList()
        if (initialLook != null) {
            list.removeIf { it.key == initialLook.key }
            list.addFirst(initialLook)
        }
        val level = MinecraftClient.level ?: return emptyList()
        return list
            .map {
                val model = computeCharacterModel(
                    it.profile.bodyType,
                    it.profile.biologicalCategory,
                    it.profile.biologicalSex
                )
                Item(
                    it,
                    createCharacterPreviewPlayer(
                        level,
                        it.profile,
                        { CharacterSkin(skinTextureManager.getOrDownloadTexture(it.look), model) }
                    )
                )
            }
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

    fun tick() {
        items.forEach { it.previewPlayer.tick() }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return if (keyCode == InputConstants.KEY_RETURN) {
            onSelectLook(items.getOrNull(selectedIndex)?.entry)
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
        val baseY = (height * 0.5f).roundToInt() + 64
        val spacing = (width / 4).coerceIn(MIN_SPACING, MAX_SPACING)

        val visibleCharacters = items
            .mapIndexed { index, character -> index to character }
            .filter { (index, _) -> abs(index - selectedIndex) <= VISIBLE_SIDE_CHARACTERS }
            .sortedByDescending { (index, _) -> abs(index - selectedIndex) }

        visibleCharacters.forEach character@{ (index, item) ->
            val entry = item.entry
            val offset = index - selectedIndex

            val itemX = centerX + offset * spacing
            val distance = abs(itemX - centerX).toFloat() / centerX.coerceAtLeast(1)
            val visualScale = (1f - distance * 0.55f).coerceIn(MIN_VISUAL_SCALE, 1f)
            val heightScale = entry.profile.height.scale
            val entityScale = BASE_ENTITY_SCALE * visualScale * heightScale

            val entity = item.previewPlayer

            if (index == selectedIndex) {
                applyCursorLook(entity, itemX, baseY, mouseX, mouseY)
            } else {
                val yaw = -offset * SIDE_CHARACTER_ROTATION
                applyRotation(entity, 180f + yaw, yaw)
            }
            entity.yBodyRotO = entity.yBodyRot
            entity.yRotO = entity.yRot
            entity.yHeadRotO = entity.yHeadRot

            val headRotation = Quaternionf().rotateX(entity.xRot * DEGREES_TO_RADIANS)
            val entityRotation = Quaternionf()
                .rotateZ(Math.PI.toFloat())
                .mul(headRotation)
            val boundsWidth = (entity.bbWidth * entityScale * 2.4f)
                .roundToInt()
                .coerceAtLeast(MIN_ITEM_BOUNDS)
            val boundsHeight = (entity.bbHeight * entityScale + BOUNDS_VERTICAL_PADDING)
                .roundToInt()
                .coerceAtLeast(MIN_ITEM_BOUNDS)

            guiGraphics.enableScissor(
                itemX - boundsWidth / 2,
                baseY - boundsHeight,
                itemX + boundsWidth / 2,
                baseY
            )
            InventoryScreen.renderEntityInInventory(
                guiGraphics,
                itemX.toFloat(),
                baseY - boundsHeight / 2f,
                entityScale,
                Vector3f(0f, entity.bbHeight / 2f + ENTITY_Y_OFFSET, 0f),
                entityRotation,
                headRotation,
                entity
            )
            guiGraphics.disableScissor()

            val textY = baseY + (14 * visualScale).roundToInt()
            guiGraphics.drawCenteredString(font, entry.text, itemX, textY, 0xFFFFFFFF.toInt())
        }
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

    private fun Double.sign(): Int {
        return kotlin.math.sign(this).toInt()
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
