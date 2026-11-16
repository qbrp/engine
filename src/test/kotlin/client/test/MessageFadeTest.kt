package org.lain.engine.client.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.engine.client.render.ui.chat.MessageFade

class MessageFadeTest {
    private val lifetime = 200f

    @Test
    fun messageFadesNearTheEndOfItsLifetime() {
        val fade = MessageFade(100)

        assertEquals(1f, fade.alpha(280, lifetime, true))
        assertEquals(0.25f, fade.alpha(290, lifetime, true), 0.0001f)
        assertEquals(0f, fade.alpha(300, lifetime, true))
        assertEquals(1f, fade.alpha(300, lifetime, false))
        assertEquals(0f, fade.alpha(300, lifetime, true))
    }

    @Test
    fun blockingPausesTheLifetimeUntilTheSpeechFinishes() {
        val fade = MessageFade(100)
        fade.update(canFade = false, tick = 100)

        assertEquals(1f, fade.alpha(400, lifetime, true))
        fade.update(canFade = true, tick = 400)

        assertEquals(1f, fade.alpha(580, lifetime, true))
        assertEquals(0.25f, fade.alpha(590, lifetime, true), 0.0001f)
        assertEquals(0f, fade.alpha(600, lifetime, true))
    }

    @Test
    fun rebuildingDuringABlockPreservesPreviouslyElapsedTime() {
        val fade = MessageFade(100)
        fade.update(canFade = false, tick = 200)
        val rebuilt = fade.copy()
        rebuilt.update(canFade = false, tick = 250)
        rebuilt.update(canFade = true, tick = 400)

        assertEquals(1f, rebuilt.alpha(480, lifetime, true))
        assertEquals(0.25f, rebuilt.alpha(490, lifetime, true), 0.0001f)
        assertEquals(0f, rebuilt.alpha(500, lifetime, true))
    }
}
