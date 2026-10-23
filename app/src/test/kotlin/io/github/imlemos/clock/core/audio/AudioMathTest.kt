package io.github.imlemos.clock.core.audio

import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.audio.AudioMath.OutputKind.BLUETOOTH
import io.github.imlemos.clock.core.audio.AudioMath.OutputKind.OTHER
import io.github.imlemos.clock.core.audio.AudioMath.OutputKind.SPEAKER
import io.github.imlemos.clock.core.data.prefs.AudioOutput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioMathTest {

    @Test
    fun `ramp starts silent, reaches the target and stays there`() {
        assertEquals(0f, AudioMath.rampVolume(0, 60_000, 0.8f))
        assertEquals(0.2f, AudioMath.rampVolume(30_000, 60_000, 0.8f), 0.0001f)
        assertEquals(0.8f, AudioMath.rampVolume(60_000, 60_000, 0.8f))
        assertEquals(0.8f, AudioMath.rampVolume(120_000, 60_000, 0.8f))
    }

    @Test
    fun `ramp is monotonic`() {
        val samples = (0..60).map { AudioMath.rampVolume(it * 1_000L, 60_000, 1f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `no ramp means the target immediately`() {
        assertEquals(1f, AudioMath.rampVolume(0, 0, 1f))
    }

    @Test
    fun `phone and Bluetooth keeps the system routing`() {
        assertNull(AudioMath.preferredOutput(AudioOutput.PHONE_AND_BLUETOOTH, listOf(SPEAKER, BLUETOOTH)))
    }

    @Test
    fun `phone only forces the speaker`() {
        assertEquals(SPEAKER, AudioMath.preferredOutput(AudioOutput.PHONE_ONLY, listOf(SPEAKER, BLUETOOTH)))
    }

    @Test
    fun `Bluetooth only uses Bluetooth when connected and the speaker otherwise`() {
        assertEquals(BLUETOOTH, AudioMath.preferredOutput(AudioOutput.BLUETOOTH_ONLY, listOf(SPEAKER, BLUETOOTH, OTHER)))
        assertEquals(SPEAKER, AudioMath.preferredOutput(AudioOutput.BLUETOOTH_ONLY, listOf(SPEAKER, OTHER)))
    }

    @Test
    fun `every vibration except none has a valid repeating waveform`() {
        Vibration.entries.filter { it != Vibration.NONE }.forEach { vibration ->
            val pattern = AudioMath.pattern(vibration)!!
            assertTrue(pattern.size >= 2 && pattern.drop(1).any { it > 0 }, "$vibration")
        }
        assertNull(AudioMath.pattern(Vibration.NONE))
    }
}
