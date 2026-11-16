package org.lain.engine.client.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.engine.client.chat.VoiceProperties
import org.lain.engine.client.script.AudioLibrary
import org.lain.engine.client.util.AudioSource
import org.lain.engine.client.util.EngineAudioManager
import org.lain.engine.script.lua.loadScript
import org.lain.engine.script.lua.luaTable
import org.lain.engine.test.lua.LuaScriptEngineTestBase
import org.lain.engine.util.math.Pos
import org.lain.engine.world.EngineSoundCategory
import org.lain.engine.world.SoundEmissionParameters
import org.lain.engine.world.SoundEvent
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaAudioLibraryTest : LuaScriptEngineTestBase() {
    private lateinit var audioManager: RecordingAudioManager
    private lateinit var functions: LuaTable

    @BeforeEach
    fun setUp() {
        val lua = createLuaEngine("lua/audio_library.lua")
        audioManager = RecordingAudioManager()
        val audio = AudioLibrary(audioManager)
        lua.engineTable.set("game_session", luaTable { "audio"(audio.library) })
        functions = lua.globals.loadScript(lua.entrypoint).call().checktable()
    }

    @Test
    fun createsSourceWithDefaultParametersWithoutPlayingIt() {
        val source = functions.get("default_source").call().audioSource()

        assertEquals("test:bell", source.sound.id.value)
        assertFalse(source.sound.stream)
        assertEquals(EngineSoundCategory.AMBIENT, source.category)
        assertTrue(audioManager.started.isEmpty())
    }

    @Test
    fun forwardsConfiguredParametersAndStreamingSound() {
        val source = functions.get("configured_source").call().audioSource()

        assertEquals("test:wind", source.sound.id.value)
        assertTrue(source.sound.stream)
        assertEquals(EngineSoundCategory.PLAYERS, source.category)
        assertEquals(-2.5f, source.x)
        assertEquals(4f, source.y)
        assertEquals(8f, source.z)
        assertEquals(0.25f, source.volume)
        assertEquals(1.5f, source.pitch)
        assertTrue(source.spatial)
        assertTrue(source.looping)
        assertEquals(24, source.radius)
        assertTrue(audioManager.started.isEmpty())
    }

    @Test
    fun acceptsEveryLuaSoundCategory() {
        val sources = functions.get("categories").call().checktable()

        EngineSoundCategory.entries.forEachIndexed { index, category ->
            assertEquals(category, sources.get(index + 1).audioSource().category)
        }
    }

    @Test
    fun playAndStopDelegateToAudioManagerWithDistinctSlots() {
        val result = functions.get("play_and_stop").invoke()
        val first = result.arg(1).audioSource()
        val second = result.arg(2).audioSource()

        assertEquals(listOf("0", "1"), audioManager.started.map { it.second })
        assertSame(first, audioManager.started[0].first)
        assertSame(second, audioManager.started[1].first)
        assertEquals(listOf(first), audioManager.stopped)
        assertNull(first.slot)
        assertTrue(first.isEnded)
        assertEquals("1", second.slot)
        assertFalse(second.isEnded)
    }

    @Test
    fun rejectsInvalidParametersWithoutStartingPlayback() {
        functions.get("invalid_parameters").call()

        assertTrue(audioManager.started.isEmpty())
        assertTrue(audioManager.stopped.isEmpty())
    }

    private fun LuaValue.audioSource(): AudioSource =
        checkuserdata(AudioSource::class.java) as AudioSource

    private class RecordingAudioManager : EngineAudioManager {
        val started = mutableListOf<Pair<AudioSource, String>>()
        val stopped = mutableListOf<AudioSource>()
        private val active = mutableMapOf<String, AudioSource>()

        override fun addAudioSource(audioSource: AudioSource, slot: String) {
            check(active.putIfAbsent(slot, audioSource) == null)
            started += audioSource to slot
            audioSource.slot = slot
            audioSource.isEnded = false
        }

        override fun stopAudioSource(audioSource: AudioSource) {
            stopped += audioSource
            val slot = audioSource.slot ?: return
            active.remove(slot)
            audioSource.slot = null
            audioSource.isEnded = true
        }

        override fun containsAudioSource(slot: String): Boolean = slot in active
        override fun playUiNotificationSound(): Unit = error("Неожиданный вызов playUiNotificationSound")
        override fun playPigScreamSound(): Unit = error("Неожиданный вызов playPigScreamSound")
        override fun playKickSound(): Unit = error("Неожиданный вызов playKickSound")
        override fun playSound(event: SoundEvent, parameters: SoundEmissionParameters): Unit =
            error("Неожиданный вызов playSound")
        override fun playVoiceBlipSound(voiceProperties: VoiceProperties, pos: Pos, randomizationMultiplier: Float): Unit =
            error("Неожиданный вызов playVoiceBlipSound")
        override fun invalidateCache(): Unit = error("Неожиданный вызов invalidateCache")
    }
}
