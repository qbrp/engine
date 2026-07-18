package org.lain.engine.client.render.ui

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.CompletableDeferred
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.render.CharacterSkin
import org.lain.engine.mc.getText
import org.lain.engine.mc.literalText
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.computeCharacterModel
import org.lain.engine.player.scale
import kotlin.math.abs
import kotlin.math.roundToInt

class CharacterSelectionScreen(
    private val handler: ClientHandler,
    private val characters: List<EngineCharacter>,
    private val skinTextureManager: SkinTextureManager,
) : Screen(literalText("Character selection")) {
    private var selectedIndex = 0
    private val characterCompletableDeferred: CompletableDeferred<EngineCharacter?> = CompletableDeferred()
    private var overlay: CharacterApplyConfirmationWaitOverlay? = null

    suspend fun awaitCharacterSelection() = characterCompletableDeferred.await()

    private fun selectCharacter(): Boolean {
        val selectedCharacter = characters.getOrNull(selectedIndex) ?: return true
        if (overlay != null) return false
        overlay = CharacterApplyConfirmationWaitOverlay(
            handler.awaitCharacterApplyConfirmation(),
            onClose = { onClose() },
            onFaded = {
                //TODO: сделать функцию grabMouse, но не закрывающую текущий экран
                //minecraft.mouseHandler.grabMouse()
                characterCompletableDeferred.complete(selectedCharacter)
            }
        )
        return false
    }

    override fun onClose() {
        super.onClose()
        if (!characterCompletableDeferred.isCompleted) {
            characterCompletableDeferred.complete(null)
        }
    }

    override fun init() {
        if (characters.isNotEmpty()) {
            selectedIndex = selectedIndex.coerceIn(characters.indices)
        }
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.key == InputConstants.KEY_RETURN) {
            if (selectCharacter()) {
                onClose()
            }
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
        if (characters.isEmpty()) return false
        selectedIndex = (selectedIndex - verticalAmount.sign()).coerceIn(characters.indices)
        return true
    }

    private fun isFadingOut() = overlay?.state?.get() is CharacterApplyConfirmationWaitOverlay.State.FadeOut

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(guiGraphics, mouseX, mouseY, deltaTicks)
        if (!isFadingOut()) {
            renderCharacters(guiGraphics, mouseX, mouseY)
        }
        overlay?.render(guiGraphics, mouseX, mouseY, deltaTicks)
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        if (!isFadingOut()) {
            super.renderBackground(guiGraphics, i, j, f)
        }
    }

    private fun renderCharacters(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (characters.isEmpty()) {
            guiGraphics.drawCenteredString(font, "Нет персонажей", width / 2, height / 2, 0xFFFFFFFF.toInt())
            return
        }
        val centerX = width / 2
        val baseY = (height * 0.5f).roundToInt() + 64
        val spacing = (width / 4).coerceIn(MIN_SPACING, MAX_SPACING)

        val visibleCharacters = characters
            .mapIndexed { index, character -> index to character }
            .filter { (index, _) -> abs(index - selectedIndex) <= VISIBLE_SIDE_CHARACTERS }
            .sortedByDescending { (index, _) -> abs(index - selectedIndex) }

        visibleCharacters.forEach character@{ (index, character) ->
            val offset = index - selectedIndex

            val itemX = centerX + offset * spacing
            val distance = abs(itemX - centerX).toFloat() / centerX.coerceAtLeast(1)
            val visualScale = (1f - distance * 0.55f).coerceIn(MIN_VISUAL_SCALE, 1f)
            val heightScale = character.profile.height.scale
            val entityScale = BASE_ENTITY_SCALE * visualScale * heightScale
            val look = character.baseLook
            val model = computeCharacterModel(
                character.profile.bodyType,
                character.profile.biologicalCategory,
                character.profile.biologicalSex
            )
            val renderState = createCharacterPreviewRenderState(
                CharacterSkin(skinTextureManager.getTexture(look), model),
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

            val name = character.profile.name.gradientText.getText()
            val textY = baseY + (14 * visualScale).roundToInt()
            guiGraphics.drawCenteredString(font, name, itemX, textY, 0xFFFFFFFF.toInt())
        }
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

    private fun Double.sign(): Int {
        return when {
            this > 0.0 -> 1
            this < 0.0 -> -1
            else -> 0
        }
    }

    private companion object {
        const val VISIBLE_SIDE_CHARACTERS = 3
        const val MIN_SPACING = 72
        const val MAX_SPACING = 140
        const val ITEM_BOUNDS = 112
        const val MIN_ITEM_BOUNDS = 112
        const val BOUNDS_VERTICAL_PADDING = 24
        const val BASE_ENTITY_SCALE = 60f
        const val MIN_VISUAL_SCALE = 0.45f
        const val SIDE_CHARACTER_ROTATION = 14f
        const val DEGREES_TO_RADIANS = 0.017453292f
        const val ENTITY_Y_OFFSET = 0.0625f
    }
}
