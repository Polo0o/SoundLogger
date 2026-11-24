package com.polo.soundlogger.data

import kotlin.math.log10

/**
 * Statistiques d'une nuit (spec §3 agrégation, §4.2 rapport du matin).
 * Classe pure JVM (testable unitairement avec des Sample synthétiques).
 */
object NightStats {

    data class Stats(
        val leqDb: Double,      // Leq global (moyenne énergétique)
        val minDb: Double,
        val maxDb: Double,
        val l10: Double,        // niveau dépassé 10 % du temps
        val l50: Double,        // médiane
        val l90: Double,        // niveau dépassé 90 % du temps (bruit de fond)
        /** heure (epoch ms, tronquée à l'heure) la plus bruyante, null si vide. */
        val loudestHourStartMs: Long?
    )

    fun compute(samples: List<Sample>): Stats? {
        if (samples.isEmpty()) return null
        var sumLin = 0.0
        for (s in samples) sumLin += dbToLin(s.leq)
        val leq = linToDb(sumLin / samples.size)
        val minDb = samples.minOf { it.minDb }
        val maxDb = samples.maxOf { it.maxDb }

        // percentiles sur les Leq triés
        val sorted = samples.map { it.leq }.sorted()
        val l10 = percentile(sorted, 0.90) // L10 = niveau dépassé 10 % du temps
        val l50 = percentile(sorted, 0.50)
        val l90 = percentile(sorted, 0.10)

        // heure la plus bruyante : moyenne énergétique par heure
        val byHour = samples.groupBy { (it.ts / 3_600_000L) * 3_600_000L }
        val loudest = byHour.entries.maxByOrNull { (_, ss) ->
            ss.fold(0.0) { acc, s -> acc + dbToLin(s.leq) } / ss.size
        }?.key

        return Stats(leq, minDb, maxDb, l10, l50, l90, loudest)
    }

    /** Valeur au percentile p (0..1) d'une liste triée (interpolation linéaire). */
    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = p * (sorted.size - 1)
        val lo = sorted[idx.toInt()]
        val hi = sorted[minOf(sorted.size - 1, idx.toInt() + 1)]
        return lo + (hi - lo) * (idx - idx.toInt())
    }

    private fun dbToLin(db: Double): Double = Math.pow(10.0, db / 10.0)
    private fun linToDb(lin: Double): Double = 10.0 * log10(lin)
}
