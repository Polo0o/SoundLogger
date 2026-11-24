package com.polo.soundlogger.detection

import com.polo.soundlogger.audio.SoundLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Nuits simulées (série de niveaux injectés, pas d'AudioRecord réel) — tests
 * demandés par AGENTS.md : détection, timestamps, événements tronqués en fin de session.
 */
class EventDetectorTest {

    private val t0 = 1_000_000L
    private val stepMs = 250L

    /** Construit une série de niveaux : chaque segment = (duréeMs, leqDb), échantillonné à 250 ms. */
    private fun series(vararg segments: Pair<Long, Double>): List<SoundLevel> {
        val out = mutableListOf<SoundLevel>()
        var t = t0
        for ((durMs, db) in segments) {
            val n = (durMs / stepMs).toInt()
            repeat(n) {
                out.add(SoundLevel(t, db, db - 3.0, db + 3.0))
                t += stepMs
            }
        }
        return out
    }

    private fun run(config: DetectionConfig, levels: List<SoundLevel>, finishAt: Long): List<SoundEvent> {
        val det = EventDetector(config)
        det.startSession(t0)
        for (l in levels) det.onSample(l)
        // finish() renvoie la liste complète et définitive (avec deltas remplis)
        return det.finish(finishAt)
    }

    @Test
    fun `mode absolu detecte un pic avec bons timestamps`() {
        val config = DetectionConfig(mode = DetectionMode.ABSOLUTE_THRESHOLD, absoluteThresholdDb = 55.0)
        // 10 min à 40 dB, 2 min à 60 dB, 3 min à 40 dB
        val levels = series(600_000L to 40.0, 120_000L to 60.0, 180_000L to 40.0)
        val events = run(config, levels, t0 + 900_000L)

        assertEquals(1, events.size)
        val ev = events[0]
        assertEquals(t0 + 600_000L, ev.startMs)          // début au 1er échantillon >= 55 dB
        assertEquals(t0 + 720_000L, ev.endMs)            // fin au retour sous 53 dB (calmSince)
        assertEquals(120_000L, ev.durationMs)
        assertEquals(60.0, ev.leqDb, 0.1)                // Leq de l'événement
        assertEquals(63.0, ev.maxDb, 0.1)                // max des maxDb
        assertFalse(ev.truncated)
        // écart au bruit moyen de la nuit : (2400*1e4 + 480*1e6 + 720*1e4)/3600 -> 51.52 dB
        val nightMean = 10.0 * kotlin.math.log10((2400 * 1e4 + 480 * 1e6 + 720 * 1e4) / 3600)
        assertEquals(60.0 - nightMean, ev.deltaVsNightMeanDb, 0.05)
    }

    @Test
    fun `mode relatif suspendu tant que la reference t0+15 a t0+30 nest pas calculee`() {
        val config = DetectionConfig(mode = DetectionMode.DEFERRED_RELATIVE, deltaDb = 10.0)
        // pic à t0+5 min (référence pas encore calculée -> ignoré),
        // puis fenêtre 15-30 min à 40 dB, pic à 31-33 min à 60 dB
        val levels = series(
            300_000L to 40.0,   // 0-5 min
            60_000L to 60.0,    // 5-6 min : pic AVANT la fenêtre de référence
            1_500_000L to 40.0, // 6-31 min (couvre 15-30 min)
            120_000L to 60.0,   // 31-33 min : pic APRÈS référence
            120_000L to 40.0    // 33-35 min
        )
        val det = EventDetector(config)
        det.startSession(t0)
        for (l in levels) {
            det.onSample(l)
            if (l.timestampMs == t0 + 330_000L) assertFalse(det.referenceReady) // à 5,5 min : suspendu
        }
        val closed = det.finish(t0 + 2_100_000L)

        assertTrue(det.referenceReady)
        assertEquals(40.0, det.referenceDb!!, 0.01) // moyenne de la fenêtre 15-30 min
        assertEquals(1, closed.size)                 // seul le pic de 31-33 min est détecté
        assertEquals(t0 + 1_860_000L, closed[0].startMs) // 31 min
    }

    @Test
    fun `evenement tronque en fin de session`() {
        val config = DetectionConfig(mode = DetectionMode.ABSOLUTE_THRESHOLD, absoluteThresholdDb = 55.0)
        val levels = series(600_000L to 40.0, 60_000L to 60.0) // pic toujours en cours à la fin
        val finishAt = t0 + 660_000L
        val events = run(config, levels, finishAt)

        assertEquals(1, events.size)
        assertTrue(events[0].truncated)
        assertEquals(finishAt, events[0].endMs)
        assertEquals(t0 + 600_000L, events[0].startMs)
    }

    @Test
    fun `bip plus court que la duree minimale est ignore`() {
        val config = DetectionConfig(mode = DetectionMode.ABSOLUTE_THRESHOLD, absoluteThresholdDb = 55.0)
        // un seul échantillon à 60 dB (250 ms < 500 ms min) au milieu de 40 dB
        val levels = series(600_000L to 40.0, 250L to 60.0, 600_000L to 40.0)
        val events = run(config, levels, t0 + 1_200_250L)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `seuil de fin avec hysteresis a seuil - 2 dB`() {
        val config = DetectionConfig(
            mode = DetectionMode.ABSOLUTE_THRESHOLD, absoluteThresholdDb = 55.0,
            minAboveDurationMs = 500, calmDurationMs = 10_000
        )
        // 40 dB, puis 54 dB (= seuil-1, dans l'hystérésis) prolongé, puis 40 dB
        val levels = series(600_000L to 40.0, 120_000L to 60.0, 60_000L to 54.0, 300_000L to 40.0)
        val events = run(config, levels, t0 + 1_080_000L)
        assertEquals(1, events.size)
        // l'événement inclut la phase à 54 dB (>= 53) et se termine 10 s après le retour à 40 dB
        assertEquals(t0 + 780_000L, events[0].endMs)
        assertTrue(events[0].leqDb < 60.0 && events[0].leqDb > 55.0)
    }
}
