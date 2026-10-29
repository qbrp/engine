package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.CompletableDeferred
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.render.blendFrom
import org.lain.engine.client.render.copyColorTo
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.literalText
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.util.Color
import org.lain.engine.util.math.easeInOutCubic

abstract class AbstractSelectionScreen<T>(
    protected val characterSelection: CharacterSelection,
) :
    Screen(literalText("Character selection"))
{
    abstract val looksWheel: LooksWheel<T>
    val selection = CompletableDeferred<T?>()
    var reloadJob: Job? = null
    var overlay: CharacterApplyConfirmationWaitOverlay? = null
    private var fadeInStartedAt: Long? = null
    private var fadeInBackground: TextureTarget? = null

    open suspend fun onReload(account: AccountResponse) {}

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

    fun reload() {
        if (reloadJob != null) return
        reloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = runCatching {
                    characterSelection.client.accountManager.fetchAccountResponse()
                }
                    .getOrNull()
                withClientContext {
                    response?.let { onReload(it) }
                    reloadJob = null
                }
            } catch (e: CancellationException) {

            }
        }
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
        } else if (!selection.isCompleted && keyCode == InputConstants.KEY_F5) {
            reload()
            true
        } else if (!looksWheel.keyPressed(keyCode, scanCode, modifiers)) {
            super.keyPressed(keyCode, scanCode, modifiers)
        } else {
            false
        }
    }

    override fun onClose() {
        if (!selection.isCompleted) {
            selection.complete(null)
        }
        overlay?.job?.cancel()
        looksWheel.close()
        reloadJob?.cancel()
        super.onClose()
    }

    override fun removed() {
        releaseFadeInBackground()
        fadeInStartedAt = null
        super.removed()
    }

    private fun isFadingOut() =
        overlay?.state?.get() is CharacterApplyConfirmationWaitOverlay.State.FadeOut

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val client = minecraft ?: return
        val now = Util.getMillis()
        val startedAt = fadeInStartedAt ?: now.also { fadeInStartedAt = it }
        val progress = ((now - startedAt) / FADE_IN_DURATION_MS).coerceIn(0f, 1f)
        if (progress < 1f) {
            captureFadeInBackground(guiGraphics, client.mainRenderTarget)
        } else {
            releaseFadeInBackground()
        }

        renderContents(guiGraphics, mouseX, mouseY, deltaTicks)
        fadeInBackground?.let { background ->
            renderFadeInBackground(guiGraphics, background, 1f - easeInOutCubic(progress))
        }
    }

    private fun renderContents(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(guiGraphics, mouseX, mouseY, deltaTicks)
        looksWheel.visible = !isFadingOut()
        val minecraft = minecraft ?: return

        if (reloadJob != null) {
            guiGraphics.drawCenteredString(
                minecraft.font,
                "Перезагрузка...",
                width / 2,
                height / 2,
                Color.WHITE.integer
            )
        } else {
            overlay?.render(guiGraphics, mouseX, mouseY, deltaTicks)
            if (overlay == null) {
                guiGraphics.drawCenteredString(
                    minecraft.font,
                    "Нажмите на Enter, чтобы выбрать персонажа",
                    width / 2,
                    height - minecraft.font.lineHeight - 8,
                    Color.WHITE.integer
                )
            }
        }
    }

    private fun captureFadeInBackground(guiGraphics: GuiGraphics, mainTarget: RenderTarget) {
        guiGraphics.flush()
        try {
            val background = fadeInBackground
                ?: TextureTarget(mainTarget.width, mainTarget.height, false, Minecraft.ON_OSX)
                    .also { fadeInBackground = it }
            if (background.width != mainTarget.width || background.height != mainTarget.height) {
                background.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX)
            }
            mainTarget.copyColorTo(background)
        } finally {
            mainTarget.bindWrite(true)
        }
    }

    private fun renderFadeInBackground(guiGraphics: GuiGraphics, background: TextureTarget, opacity: Float) {
        guiGraphics.flush()
        // Blend the original scene over the screen to fade opaque entities with the UI.
        minecraft?.mainRenderTarget?.blendFrom(background, opacity)
    }

    private fun releaseFadeInBackground() {
        val background = fadeInBackground ?: return
        fadeInBackground = null
        try {
            background.destroyBuffers()
        } finally {
            minecraft?.mainRenderTarget?.bindWrite(true)
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        if (!isFadingOut()) {
            super.renderBlurredBackground(f)
        }
    }

    companion object {
        private const val FADE_IN_DURATION_MS = 200f
    }
}
