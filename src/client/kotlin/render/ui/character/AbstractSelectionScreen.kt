package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.CompletableDeferred
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.mc.literalText

abstract class AbstractSelectionScreen<T>(
    protected val characterSelection: CharacterSelection,
) :
    Screen(literalText("Character selection"))
{
    abstract val looksWheel: LooksWheel<T>
    val selection = CompletableDeferred<T?>()
    var overlay: CharacterApplyConfirmationWaitOverlay? = null

    protected fun selectEntry(selected: LooksWheel.Entry<T>?) {
        if (overlay != null) return
        if (selected == null) {
            onClose()
            return
        }
        overlay = CharacterApplyConfirmationWaitOverlay(
            characterSelection,
            onClose = { onClose() },
            onFaded = { selection.complete(selected.result) }
        )
    }

    override fun init() {
        looksWheel.init(this)
        addRenderableWidget(looksWheel)
    }

    override fun tick() {
        super.tick()
        looksWheel.tick()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (overlay != null) return true
        return if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose()
            true
        } else if (!looksWheel.keyPressed(keyCode, scanCode, modifiers)) {
            super.keyPressed(keyCode, scanCode, modifiers)
        } else {
            true
        }
    }

    override fun onClose() {
        if (!selection.isCompleted) {
            selection.complete(null)
        }
        overlay?.job?.cancel()
        super.onClose()
    }

    private fun isFadingOut() =
        overlay?.state?.get() is CharacterApplyConfirmationWaitOverlay.State.FadeOut

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(guiGraphics, mouseX, mouseY, deltaTicks)
        looksWheel.visible = !isFadingOut()
        overlay?.render(guiGraphics, mouseX, mouseY, deltaTicks)
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        if (!isFadingOut()) {
            super.renderBlurredBackground(f)
        }
    }
}
