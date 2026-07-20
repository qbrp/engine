package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.CompletableDeferred
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.mc.literalText
import org.lain.engine.player.character.EngineCharacter

abstract class AbstractSelectionScreen<T> : Screen(literalText("Character selection")) {
    abstract val looksWheel: LooksWheel<T>
    var overlay: CharacterApplyConfirmationWaitOverlay? = null

    abstract fun onEntrySelected(selected: LooksWheel.Entry<T>)

    protected fun selectEntry(selected: LooksWheel.Entry<T>?) {
        if (selected == null) {
            onClose()
            return
        }
        onEntrySelected(selected)
    }

    override fun init() {
        looksWheel.init(this)
        addRenderableWidget(looksWheel)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.isEscape) {
            onClose()
            true
        } else if (!looksWheel.keyPressed(keyEvent)) {
            super.keyPressed(keyEvent)
        } else {
            true
        }
    }

    private fun isFadingOut() = overlay?.state?.get() is CharacterApplyConfirmationWaitOverlay.State.FadeOut

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(guiGraphics, mouseX, mouseY, deltaTicks)
        looksWheel.visible = !isFadingOut()
        overlay?.render(guiGraphics, mouseX, mouseY, deltaTicks)
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        if (!isFadingOut()) {
            super.renderBlurredBackground(guiGraphics)
        }
    }
}