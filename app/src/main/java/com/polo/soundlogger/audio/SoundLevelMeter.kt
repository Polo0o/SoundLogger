package com.polo.soundlogger.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Un point de mesure produit par [SoundLevelMeter]. */
data class SoundLevel(
    val timestampMs: Long,
    val leqDb: Double, // niveau RMS de la fenêtre, dB (calibré)
    val minDb: Double,
    val maxDb: Double
)

/**
 * Mesure du niveau sonore via AudioRecord PCM (spec §2.1-2.2).
 * 16 kHz / 16 bits / mono, fenêtre par défaut de 250 ms (réglable 100–1000 ms).
 * Pondération A logicielle (IEC 61672, [AWeightingFilter]) + offset de
 * calibration : les niveaux affichés sont des dB(A) calibrés.
 * Chaque point rapporte le Leq de la fenêtre et les min/max des
 * sous-fenêtres (10 par fenêtre).
 */
class SoundLevelMeter(
    private val windowMs: Int = 250,
    // Offset de calibration par défaut (~micro de smartphone), réglable dans les réglages.
    private val calibrationOffsetDb: Double = 90.0
) {
    companion object {
        const val SAMPLE_RATE = 16_000
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile var running = false
        private set

    /** Listener appelé à chaque fenêtre. */
    var onLevel: ((SoundLevel) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start() {
        check(!running) { "Meter déjà démarré" }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE * 2)
        ).also { it.startRecording() }
        running = true
        thread = Thread({ loop() }, "SoundLevelMeter").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        record?.run { runCatching { stop() }; release() }
        record = null
    }

    private fun loop() {
        val samplesPerWindow = SAMPLE_RATE * windowMs / 1000
        // 10 sous-fenêtres par fenêtre pour les min/max (spec §2.2).
        val subSamples = maxOf(1, samplesPerWindow / 10)
        val buffer = ShortArray(samplesPerWindow)
        val aWeighting = AWeightingFilter(SAMPLE_RATE)
        while (running) {
            val n = record?.read(buffer, 0, samplesPerWindow) ?: break
            if (n <= 0) continue
            var sumSquares = 0.0
            var peak = 0.0
            var minSubDb = Double.POSITIVE_INFINITY
            var maxSubDb = 0.0
            var idx = 0
            while (idx < n) {
                val start = idx
                val end = minOf(idx + subSamples, n)
                var subSq = 0.0
                while (idx < end) {
                    val s = buffer[idx] / 32768.0
                    val w = aWeighting.process(s) // pondération A (IEC 61672)
                    sumSquares += w * w
                    subSq += w * w
                    val a = abs(w)
                    if (a > peak) peak = a
                    idx++
                }
                val subDb = toDb(sqrt(subSq / (end - start)))
                if (subDb < minSubDb) minSubDb = subDb
                if (subDb > maxSubDb) maxSubDb = subDb
            }
            val rms = sqrt(sumSquares / n)
            val leq = toDb(rms)
            onLevel?.invoke(
                SoundLevel(
                    timestampMs = System.currentTimeMillis(),
                    leqDb = leq,
                    minDb = minSubDb,
                    maxDb = maxOf(toDb(peak), maxSubDb)
                )
            )
        }
    }

    private fun toDb(amplitude: Double): Double =
        if (amplitude <= 0.0) 0.0 else 20.0 * log10(amplitude) + calibrationOffsetDb
}
