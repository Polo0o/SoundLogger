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
 * 16 kHz / 16 bits / mono, fenêtre par défaut de 250 ms.
 * La pondération A (IEC 61672) est un TODO v0.2 ; en attendant : dB FS + offset.
 */
class SoundLevelMeter(
    private val windowMs: Int = 250,
    // Offset de calibration par défaut (~micro de smartphone), réglable. TODO: calibration utilisateur.
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
        val buffer = ShortArray(samplesPerWindow)
        while (running) {
            val n = record?.read(buffer, 0, samplesPerWindow) ?: break
            if (n <= 0) continue
            var sumSquares = 0.0
            var peak = 0.0
            for (i in 0 until n) {
                val s = buffer[i] / 32768.0
                sumSquares += s * s
                val a = abs(s)
                if (a > peak) peak = a
            }
            val rms = sqrt(sumSquares / n)
            val leq = toDb(rms)
            // min/max TODO v0.2 : min réel sur sous-fenêtres
            onLevel?.invoke(SoundLevel(System.currentTimeMillis(), leq, leq - 6.0, toDb(peak)))
        }
    }

    private fun toDb(amplitude: Double): Double =
        if (amplitude <= 0.0) 0.0 else 20.0 * log10(amplitude) + calibrationOffsetDb
}
