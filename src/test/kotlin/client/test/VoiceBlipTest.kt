package org.lain.engine.client.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.client.chat.Syllable
import org.lain.engine.client.chat.VoiceBlip
import org.lain.engine.client.chat.VoiceBlipNode
import org.lain.engine.client.chat.VoiceProperties
import org.lain.engine.client.chat.toChars
import org.lain.engine.player.randomPlayerId

class VoiceBlipTest {
    @Test
    fun lastSyllableCompletesBeforeTheEffectSettles() {
        val syllables = listOf(
            Syllable("name ", 0, 1f, shake = false, playSound = false),
            Syllable("hi", 5, 1f, shake = true, playSound = true),
        )
        val blip = voiceBlip(syllables, processed = 1)
        var sounds = 0

        assertTrue(blip.active)
        blip.step(0.25f) { sounds++ }
        assertEquals(1, sounds)
        assertEquals(6, blip.visibleSymbols())
        assertTrue(blip.active)

        blip.step(0.75f) { sounds++ }
        assertEquals(1, sounds)
        assertFalse(blip.active)
        assertEquals(7, blip.visibleSymbols())
        assertFalse(blip.settled)

        blip.step(3f) { sounds++ }
        assertEquals(1, sounds)
        assertTrue(blip.settled)
        assertEquals(7, blip.visibleSymbols())
    }

    @Test
    fun eachSpokenSyllableStartsOnceAndSeparatorsAreSilent() {
        val blip = voiceBlip(
            listOf(
                Syllable("a", 0, 1f, shake = true, playSound = true),
                Syllable(" ", 1, 1f, shake = true, playSound = false),
                Syllable("b", 2, 1f, shake = true, playSound = true),
            )
        )
        var sounds = 0

        blip.step(0.5f) { sounds++ }
        blip.step(0.5f) { sounds++ }
        assertEquals(1, sounds)
        blip.step(1f) { sounds++ }
        assertEquals(2, sounds)
        assertTrue(blip.active)
        blip.step(1f) { sounds++ }

        assertEquals(2, sounds)
        assertFalse(blip.active)
        assertEquals(3, blip.visibleSymbols())
    }

    @Test
    fun emptyAndAlreadyProcessedSequencesCanBeStepped() {
        val empty = voiceBlip(emptyList())
        val prefixOnly = voiceBlip(
            listOf(Syllable("name ", 0, 1f, shake = false, playSound = false)),
            processed = 1,
        )
        var sounds = 0

        empty.step(1f) { sounds++ }
        prefixOnly.step(1f) { sounds++ }

        assertFalse(empty.active)
        assertTrue(empty.settled)
        assertEquals(0, empty.visibleSymbols())
        assertFalse(prefixOnly.active)
        assertTrue(prefixOnly.settled)
        assertEquals(5, prefixOnly.visibleSymbols())
        assertEquals(0, sounds)
    }

    private fun voiceBlip(syllables: List<Syllable>, processed: Int = 0) = VoiceBlip(
        VoiceBlipNode(
            syllables = syllables,
            chars = syllables.toChars(processed),
            processed = processed,
            started = processed,
        ),
        VoiceProperties(volume = 1f),
        randomPlayerId(),
    )
}
