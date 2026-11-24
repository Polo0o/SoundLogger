package com.polo.soundlogger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests des statistiques de nuit (spec §3 / §4.2) sur données simulées. */
class NightStatsTest {

    private fun sample(ts: Long, leq: Double) = Sample(nightId = 1, ts = ts, leq = leq, minDb = leq - 3, maxDb = leq + 3)

    @Test
    fun `stats sur liste vide`() {
        assertNull(NightStats.compute(emptyList()))
    }

    @Test
    fun `leq min max et percentiles`() {
        val t0 = 3_600_000L // aligné sur une heure ronde
        val samples = mutableListOf<Sample>()
        // 100 échantillons à 40 dB dans l'heure H, 20 à 60 dB dans l'heure H+1
        repeat(100) { samples.add(sample(t0 + it * 250L, 40.0)) }
        repeat(20) { samples.add(sample(t0 + 3_600_000L + it * 250L, 60.0)) }

        val stats = NightStats.compute(samples)!!
        // Leq = 10log10((100*1e4 + 20*1e6)/120) = 52,43 dB
        assertEquals(52.43, stats.leqDb, 0.01)
        assertEquals(37.0, stats.minDb, 0.01)
        assertEquals(63.0, stats.maxDb, 0.01)
        assertEquals(40.0, stats.l50, 0.01)   // médiane
        assertEquals(60.0, stats.l10, 0.01)   // 20/120 = 16,7 % du temps au-dessus de 60-ε
        assertEquals(40.0, stats.l90, 0.01)   // bruit de fond
        assertEquals(t0 + 3_600_000L, stats.loudestHourStartMs) // heure la plus bruyante
    }
}
