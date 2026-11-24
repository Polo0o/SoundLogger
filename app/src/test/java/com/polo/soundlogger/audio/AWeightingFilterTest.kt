package com.polo.soundlogger.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * Vérifie la pondération A (IEC 61672) contre la table de référence :
 * 1 kHz = 0 dB, 100 Hz = -19,1 dB, 20 Hz = -50,5 dB, 4 kHz = +1,0 dB, 8 kHz = -1,1 dB.
 */
class AWeightingFilterTest {

    private val filter = AWeightingFilter(16_000)

    private fun assertGainDb(expectedDb: Double, freqHz: Double, tolDb: Double) {
        val g = filter.gainDbAt(freqHz)
        assertEquals("gain à ${freqHz} Hz", expectedDb, g, tolDb)
    }

    @Test
    fun `gain nul a 1 kHz`() {
        assertGainDb(0.0, 1000.0, 0.05)
    }

    @Test
    fun `forme de la ponderation A selon IEC 61672`() {
        // À fs = 16 kHz, le gauchissement bilinéaire dégrade la réponse au-delà
        // d'environ 4 kHz (8 kHz = Nyquist) ; la bande utile du bruit nocturne
        // (20 Hz - 4 kHz) est couverte à mieux que 1 dB.
        assertGainDb(-50.5, 20.0, 1.0)
        assertGainDb(-39.4, 31.5, 1.0)
        assertGainDb(-30.2, 50.0, 0.8)
        assertGainDb(-19.1, 100.0, 0.6)
        assertGainDb(-8.6, 250.0, 0.6)
        assertGainDb(+1.2, 2000.0, 0.5)
        assertGainDb(+1.0, 4000.0, 1.0)
        // au-delà, le filtre atténue (limite de Nyquist à 16 kHz)
        org.junit.Assert.assertTrue(filter.gainDbAt(8000.0) < 0.5)
    }

    @Test
    fun `reponse en regime etabli sur sinus`() {
        val fs = 16_000
        // sinus 1 kHz amplitude 1 -> gain 0 dB -> amplitude de sortie ~ 1
        assertEquals(1.0, sinusPeakDb(AWeightingFilter(fs), fs, 1000.0, 1.0), 0.05)
        // sinus 100 Hz amplitude 1 -> -19,1 dB -> amplitude ~ 0,111
        assertEquals(0.111, sinusPeakDb(AWeightingFilter(fs), fs, 100.0, 1.0), 0.02)
    }

    /** Amplitude de crête en sortie pour un sinus d'amplitude [amp] à freqHz, en régime établi. */
    private fun sinusPeakDb(f: AWeightingFilter, fs: Int, freqHz: Double, amp: Double): Double {
        var peak = 0.0
        val n = fs // 1 seconde
        for (i in 0 until n) {
            val x = amp * sin(2.0 * PI * freqHz * i / fs)
            val y = f.process(x)
            if (i > n / 2) {
                val a = abs(y)
                if (a > peak) peak = a
            }
        }
        return peak
    }
}
