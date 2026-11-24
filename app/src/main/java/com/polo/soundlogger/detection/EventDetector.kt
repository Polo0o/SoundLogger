package com.polo.soundlogger.detection

import com.polo.soundlogger.audio.SoundLevel
import kotlin.math.log10
import kotlin.math.max

/** Mode de détection d'événements (spec §3 et §4.1, invariant AGENTS.md 3). */
enum class DetectionMode {
    /** Seuil absolu fixe (ex. 55 dB). */
    ABSOLUTE_THRESHOLD,
    /** Défaut : référence = moyenne t0+15 -> t0+30 min, événement si niveau > référence + deltaDb. */
    DEFERRED_RELATIVE
}

data class DetectionConfig(
    val mode: DetectionMode = DetectionMode.DEFERRED_RELATIVE,
    /** Seuil absolu (mode ABSOLUTE_THRESHOLD) en dB. */
    val absoluteThresholdDb: Double = 55.0,
    /** Décalage au-dessus de la référence (mode DEFERRED_RELATIVE), défaut +10 dB. */
    val deltaDb: Double = 10.0,
    /** Fenêtre de référence : début et fin après t0 (ms). */
    val referenceWindowStartMs: Long = 15 * 60_000,
    val referenceWindowEndMs: Long = 30 * 60_000,
    /** Durée minimale de dépassement (défaut 500 ms) et retour au calme (défaut 10 s). */
    val minAboveDurationMs: Long = 500,
    val calmDurationMs: Long = 10_000
)

/** Un événement détecté (invariant AGENTS.md 4 : début, Leq, durée, max, écart à la moyenne de nuit). */
data class SoundEvent(
    val startMs: Long,
    val endMs: Long,
    val leqDb: Double,     // Leq moyen de l'événement
    val maxDb: Double,
    val deltaVsNightMeanDb: Double, // écart au Leq moyen de la nuit (rempli à finish())
    /** true si l'événement était encore en cours à la fin de session (tronqué). */
    val truncated: Boolean = false
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Détecteur d'événements en flux (spec §3, invariants AGENTS.md 3-4).
 *
 * Machine à états : IDLE -> ABOVE (niveau >= seuil) -> confirmé après
 * [DetectionConfig.minAboveDurationMs] -> fin quand le niveau retombe sous
 * seuil - 2 dB pendant >= [DetectionConfig.calmDurationMs].
 *
 * En mode DEFERRED_RELATIVE, la détection est **suspendue** tant que la
 * référence (moyenne des niveaux entre t0+15 et t0+30 min) n'est pas calculée
 * ([referenceReady] == false) ; un échantillon dans la fenêtre de référence
 * alimente celle-ci.
 *
 * Le Leq de l'événement est la moyenne énergétique des échantillons de
 * l'événement ; l'écart au bruit moyen de la nuit est rempli dans [finish].
 */
class EventDetector(private val config: DetectionConfig) {

    private var sessionStartMs: Long = 0
    private val referenceWindow = mutableListOf<Double>()
    var referenceDb: Double? = null
        private set
    /** false tant que la référence n'est pas calculée (mode relatif) : détection suspendue. */
    var referenceReady: Boolean = false
        private set

    // statistiques de la nuit (tous échantillons) pour l'écart à la moyenne
    private var nightSumLin = 0.0
    private var nightCount = 0L

    // état de l'événement en cours
    private var aboveSince: Long? = null
    private var confirmed = false
    private var calmSince: Long? = null
    private var evSumLin = 0.0
    private var evCount = 0L
    private var evMaxDb = Double.NEGATIVE_INFINITY

    private val events = mutableListOf<SoundEvent>()

    fun startSession(nowMs: Long) {
        sessionStartMs = nowMs
        referenceWindow.clear()
        referenceDb = null
        referenceReady = config.mode == DetectionMode.ABSOLUTE_THRESHOLD
        nightSumLin = 0.0
        nightCount = 0
        aboveSince = null
        confirmed = false
        calmSince = null
        evSumLin = 0.0
        evCount = 0
        evMaxDb = Double.NEGATIVE_INFINITY
        events.clear()
    }

    /** À appeler pour chaque point de mesure ; retourne les événements terminés. */
    fun onSample(level: SoundLevel): List<SoundEvent> {
        if (sessionStartMs == 0L) startSession(level.timestampMs)

        // stats de la nuit (moyenne énergétique globale)
        nightSumLin += dbToLin(level.leqDb)
        nightCount++

        // Alimentation de la fenêtre de référence (t0+15 -> t0+30)
        if (config.mode == DetectionMode.DEFERRED_RELATIVE && referenceDb == null) {
            val t = level.timestampMs - sessionStartMs
            if (t in config.referenceWindowStartMs..config.referenceWindowEndMs) {
                referenceWindow.add(level.leqDb)
            } else if (t > config.referenceWindowEndMs) {
                computeReference()
            }
        }

        val threshold = when (config.mode) {
            DetectionMode.ABSOLUTE_THRESHOLD -> config.absoluteThresholdDb
            DetectionMode.DEFERRED_RELATIVE -> {
                val r = referenceDb ?: return emptyList() // détection suspendue (spec §3)
                r + config.deltaDb
            }
        }
        val endThreshold = threshold - 2.0
        val now = level.timestampMs
        val closed = mutableListOf<SoundEvent>()

        if (level.leqDb >= threshold) {
            if (aboveSince == null) {
                aboveSince = now
                confirmed = false
                calmSince = null
                evSumLin = 0.0
                evCount = 0
                evMaxDb = Double.NEGATIVE_INFINITY
            }
            if (now - (aboveSince ?: now) >= config.minAboveDurationMs) confirmed = true
            calmSince = null
            accumulate(level)
        } else {
            val start = aboveSince
            if (start != null) {
                if (confirmed) {
                    if (level.leqDb >= endThreshold) {
                        // hystérésis : encore dans l'événement
                        calmSince = null
                        accumulate(level)
                    } else {
                        calmSince = calmSince ?: now
                        if (now - calmSince!! >= config.calmDurationMs) {
                            val ev = closeEvent(start, calmSince!!, truncated = false)
                            events.add(ev)
                            closed.add(ev)
                            resetCurrent()
                        }
                    }
                } else if (level.leqDb < endThreshold) {
                    resetCurrent() // dépassement trop bref (< minAboveDurationMs)
                }
            }
        }
        return closed
    }

    /** Événements fermés jusqu'ici (deltaVsNightMeanDb à 0 tant que finish() n'a pas tourné). */
    fun currentEvents(): List<SoundEvent> = events.toList()

    /**
     * Fin de session : ferme l'événement en cours (tronqué), calcule la
     * référence si la fenêtre n'a pas été atteinte, et remplit l'écart au
     * bruit moyen de la nuit pour tous les événements.
     */
    fun finish(nowMs: Long): List<SoundEvent> {
        if (config.mode == DetectionMode.DEFERRED_RELATIVE && referenceDb == null) computeReference()
        val start = aboveSince
        if (start != null && confirmed) {
            events.add(closeEvent(start, nowMs, truncated = true))
        }
        resetCurrent()
        val nightMeanDb = if (nightCount > 0) linToDb(nightSumLin / nightCount) else 0.0
        val withDelta = events.map { it.copy(deltaVsNightMeanDb = it.leqDb - nightMeanDb) }
        events.clear()
        events.addAll(withDelta)
        return events.toList()
    }

    private fun computeReference() {
        referenceDb = if (referenceWindow.isNotEmpty()) {
            referenceWindow.average()
        } else if (nightCount > 0) {
            linToDb(nightSumLin / nightCount) // session plus courte que la fenêtre
        } else {
            0.0
        }
        referenceReady = true
    }

    private fun accumulate(level: SoundLevel) {
        evSumLin += dbToLin(level.leqDb)
        evCount++
        evMaxDb = max(evMaxDb, level.maxDb)
    }

    private fun closeEvent(start: Long, end: Long, truncated: Boolean): SoundEvent =
        SoundEvent(
            startMs = start,
            endMs = end,
            leqDb = if (evCount > 0) linToDb(evSumLin / evCount) else 0.0,
            maxDb = if (evMaxDb.isFinite()) evMaxDb else 0.0,
            deltaVsNightMeanDb = 0.0, // rempli dans finish()
            truncated = truncated
        )

    private fun resetCurrent() {
        aboveSince = null
        confirmed = false
        calmSince = null
        evSumLin = 0.0
        evCount = 0
        evMaxDb = Double.NEGATIVE_INFINITY
    }

    private fun dbToLin(db: Double): Double = Math.pow(10.0, db / 10.0)
    private fun linToDb(lin: Double): Double = 10.0 * log10(lin)
}
