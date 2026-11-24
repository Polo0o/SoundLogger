package com.polo.soundlogger.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pondération A fréquentielle selon IEC 61672, en filtres numériques
 * (prototype analogique + transformée de bilinéaire), normalisée à 0 dB à 1 kHz.
 *
 * Prototype analogique (forme standard R_A(f)) :
 *   H(s) = s^4 / prod(s + p_i), pôles réels à
 *   20.598997 Hz (x2), 107.65265 Hz, 737.86223 Hz, 12194.217 Hz (x2).
 * Après bilinéaire s = 2fs(1-z^-1)/(1+z^-1) :
 *   H(z) ∝ (1-z^-1)^4 (1+z^-1)^2 / prod(1 + a_i z^-1)
 * Les pôles sont pré-gauchis pour retomber aux bonnes fréquences numériques ;
 * un pôle au-delà de fs/2 est rabattu juste sous fs/2.
 *
 * Classe pure JVM : testable unitairement sans Android.
 */
class AWeightingFilter(sampleRate: Int) {

    private val fs = sampleRate.toDouble()

    // 6 sections du 1er ordre, dénominateur seul : y[n] = x[n] - a_i*y[n-1]
    private val a = DoubleArray(N_SECTIONS)
    private val y1 = DoubleArray(N_SECTIONS)

    // FIR = (1 - z^-1)^4 * (1 + z^-1)^2 = 1 - 2z^-1 - z^-2 + 4z^-3 - z^-4 - 2z^-5 + z^-6
    private val fir = doubleArrayOf(1.0, -2.0, -1.0, 4.0, -1.0, -2.0, 1.0)
    private val fx = DoubleArray(fir.size)

    /** Normalisation : gain = 1 (0 dB) à 1 kHz. */
    private val norm: Double

    init {
        val polesHz = doubleArrayOf(
            20.598997, 20.598997, 107.65265, 737.86223, 12194.217, 12194.217
        )
        val c = 2.0 * fs
        for (i in polesHz.indices) {
            // prewarp : le pôle numérique tombe exactement à f Hz
            val f = polesHz[i].coerceAtMost(0.499 * fs)
            val p = c * tan(PI * f / fs)
            a[i] = (p - c) / (c + p)
        }
        norm = 1.0 / rawGainAt(1000.0)
    }

    /** Filtre un échantillon (amplitude normalisée [-1, 1]) ; retourne la valeur pondérée A. */
    fun process(x: Double): Double {
        var v = x
        for (i in 0 until N_SECTIONS) {
            val y = v - a[i] * y1[i]
            y1[i] = y
            v = y
        }
        // FIR
        for (k in fx.size - 1 downTo 1) fx[k] = fx[k - 1]
        fx[0] = v
        var out = 0.0
        for (k in fir.indices) out += fir[k] * fx[k]
        return norm * out
    }

    fun reset() {
        y1.fill(0.0)
        fx.fill(0.0)
    }

    /** Gain du filtre (normalisé) à [freqHz], en dB relatif à 1 kHz. */
    fun gainDbAt(freqHz: Double): Double = 20.0 * log10(rawGainAt(freqHz) * norm)

    /** Gain brut (avant normalisation) à freqHz. */
    private fun rawGainAt(freqHz: Double): Double {
        val w = 2.0 * PI * freqHz / fs
        val c = cos(w)
        val s = sin(w)
        // produit des 6 sections 1 / (1 + a_i e^-jw)
        var gr = 1.0
        var gi = 0.0
        for (i in 0 until N_SECTIONS) {
            val dr = 1.0 + a[i] * c
            val di = -a[i] * s
            val dd = dr * dr + di * di
            val qr = dr / dd
            val qi = -di / dd
            val tr = gr * qr - gi * qi
            gi = gr * qi + gi * qr
            gr = tr
        }
        // FIR : |(1-e^-jw)^4 (1+e^-jw)^2| = (2 sin(w/2))^4 * (2 cos(w/2))^2
        val firGain = (2.0 * sin(w / 2.0)).let { it * it * it * it } * (2.0 * cos(w / 2.0)) * (2.0 * cos(w / 2.0))
        return sqrt(gr * gr + gi * gi) * firGain
    }

    companion object {
        private const val N_SECTIONS = 6
    }
}
