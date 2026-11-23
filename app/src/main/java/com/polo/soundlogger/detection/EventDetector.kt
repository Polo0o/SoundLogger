package com.polo.soundlogger.detection

import com.polo.soundlogger.audio.SoundLevel

/** Mode de détection d'événements (spec §3 et §4.1). */
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

data class SoundEvent(
    val startMs: Long,
    val endMs: Long,
    val leqDb: Double,     // niveau moyen de l'événement
    val maxDb: Double,
    val deltaVsNightMeanDb: Double // écart au bruit moyen de la nuit
)

/**
 * Détecteur d'événements en flux (spec §3).
 * En mode DEFERRED_RELATIVE, la détection est suspendue tant que la référence
 * (moyenne t0+15 -> t0+30) n'est pas calculée.
 */
class EventDetector(private val config: DetectionConfig) {

    private var sessionStartMs: Long = 0
    private val referenceWindow = mutableListOf<SoundLevel>()
    var referenceDb: Double? = null
        private set

    // état de l'événement en cours
    private var aboveSince: Long? = null
    private var calmSince: Long? = null
    private val events = mutableListOf<SoundEvent>()

    fun startSession(nowMs: Long) {
        sessionStartMs = nowMs
        referenceWindow.clear()
        referenceDb = null
        aboveSince = null
        calmSince = null
        events.clear()
    }

    /** À appeler pour chaque point de mesure ; retourne les événements terminés. */
    fun onSample(level: SoundLevel): List<SoundEvent> {
        if (sessionStartMs == 0L) startSession(level.timestampMs)

        // Alimentation de la fenêtre de référence (t0+15 -> t0+30)
        if (config.mode == DetectionMode.DEFERRED_RELATIVE && referenceDb == null) {
            val t = level.timestampMs - sessionStartMs
            if (t in config.referenceWindowStartMs..config.referenceWindowEndMs) {
                referenceWindow.add(level)
            } else if (t > config.referenceWindowEndMs) {
                referenceDb = referenceWindow.map { it.leqDb }.average()
            }
        }

        val threshold = when (config.mode) {
            DetectionMode.ABSOLUTE_THRESHOLD -> config.absoluteThresholdDb
            DetectionMode.DEFERRED_RELATIVE -> (referenceDb ?: return emptyList()) + config.deltaDb
        }
        val endThreshold = threshold - 2.0

        val now = level.timestampMs
        if (level.leqDb >= threshold) {
            if (aboveSince == null) aboveSince = now
            calmSince = null
        } else {
            val start = aboveSince
            if (start != null && now - start >= config.minAboveDurationMs) {
                calmSince = calmSince ?: now
                if (now - (calmSince ?: now) >= config.calmDurationMs && level.leqDb < endThreshold) {
                    events.add(buildEvent(start, calmSince ?: now, level))
                    aboveSince = null
                    calmSince = null
                }
            } else if (level.leqDb < endThreshold) {
                aboveSince = null // dépassement trop bref
            }
        }
        // NOTE v0.2 : accumuler les Leq/min/max de l'événement en cours pour
        // calculer le Leq de l'événement et l'écart à la moyenne de la nuit.
        return emptyList()
    }

    /** Événements restants à la fin de session (événement en cours tronqué). */
    fun finish(nowMs: Long): List<SoundEvent> {
        aboveSince?.let { start ->
            events.add(buildEvent(start, nowMs, null))
            aboveSince = null
        }
        return events.toList()
    }

    private fun buildEvent(startMs: Long, endMs: Long, last: SoundLevel?): SoundEvent =
        SoundEvent(
            startMs = startMs,
            endMs = endMs,
            leqDb = last?.leqDb ?: 0.0, // TODO v0.2 : Leq moyen de l'événement
            maxDb = last?.maxDb ?: 0.0, // TODO v0.2 : max sur l'événement
            deltaVsNightMeanDb = 0.0     // TODO v0.2 : écart au Leq moyen de la nuit
        )
}
