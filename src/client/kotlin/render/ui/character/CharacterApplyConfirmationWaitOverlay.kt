package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.minecraft.client.gui.GuiGraphics
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.util.Color
import org.lain.engine.util.math.easeInOutCubic
import java.util.concurrent.atomic.AtomicReference

// Майнкрафт доказал бесполезность самого себя. Изначально это был подкласс Overlay, с которым я очень долго ебался,
// пытаясь обуздать его логику. В конце концов оказалось, что он не может рендерить одновременно себя с уже открытым экраном.
// Спасибо блять!!!
class CharacterApplyConfirmationWaitOverlay(
    private val characterResponseDeferred: CompletableDeferred<Unit>,
    private val onClose: () -> Unit,
    private val onFaded: () -> Unit,
) {
    private val client = MinecraftClient
    sealed class State(var tick: Float) {
        class FadeIn(val deferred: CompletableDeferred<Unit> = CompletableDeferred()) : State(0f)
        class Wait : State(0f)
        class FadeOut(val deferred: CompletableDeferred<Unit> = CompletableDeferred()) : State(0f)
    }
    private val fadeIn = State.FadeIn()
    var state = AtomicReference<State>(fadeIn)
        private set

    init {
        CoroutineScope(Dispatchers.Default).launch {
            fadeIn.deferred.await()
            client.execute { onFaded.invoke() }
            try {
                withTimeout(5000L) {
                    state.set(State.Wait())
                    characterResponseDeferred.await()
                }
            } catch (e: TimeoutCancellationException) {
                close()
                return@launch
            }
            delay(250)
            val fadeOut = State.FadeOut()
            state.set(fadeOut)
            fadeOut.deferred.await()
            close()
        }
    }

    private fun close() {
        client.execute { onClose.invoke() }
    }

    fun render(
        guiGraphics: GuiGraphics,
        i: Int,
        j: Int,
        f: Float
    ) {
        val state = state.get()
        state.tick = (state.tick + f).coerceAtMost(FADE_TIME)
        val tick = state.tick
        val progress = easeInOutCubic(tick / FADE_TIME).coerceIn(0f, 1f)
        val overlayOpacity = when (state) {
            is State.FadeIn -> progress
                .also {
                    if (tick >= FADE_TIME) {
                        state.deferred.complete(Unit)
                    }
                }
            is State.Wait -> 1f
            is State.FadeOut -> (1f - progress)
                .also {
                    if (tick >= FADE_TIME) {
                        state.deferred.complete(Unit)
                    }
                }
        }
        guiGraphics.fill(
            0,
            0,
            guiGraphics.guiWidth(),
            guiGraphics.guiHeight(),
            Color.BLACK.withAlpha((overlayOpacity * 255).toInt()).integer
        )
    }

    companion object {
        const val FADE_TIME = 10f
    }
}
