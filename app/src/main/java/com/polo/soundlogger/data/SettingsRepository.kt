package com.polo.soundlogger.data

import android.content.Context
import com.polo.soundlogger.detection.DetectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Réglages de l'app (spec §4.4), persistés en SharedPreferences.
 * - windowMs : hop de mesure, 100–1000 ms (défaut 250 ms, spec §2.2)
 * - calibrationOffsetDb : offset de calibration du micro (dB), réglable
 *   finement avec un sonomètre de référence (spec §2.1)
 * - paramètres de détection d'événements (spec §3 / §4.1, invariants AGENTS.md)
 */
data class AppSettings(
    val windowMs: Int = 250,
    val calibrationOffsetDb: Double = 90.0,
    val detectionMode: DetectionMode = DetectionMode.DEFERRED_RELATIVE,
    val absoluteThresholdDb: Double = 55.0,
    val deltaDb: Double = 10.0,
    val minAboveDurationMs: Long = 500,
    val calmDurationMs: Long = 10_000,
    val autoStartEnabled: Boolean = false,
    val autoStartMinutes: Int = 23 * 60,
    val autoStopMinutes: Int = 7 * 60,
    /** 0 = illimité (défaut, spec §5). */
    val retentionNights: Int = 0
)

class SettingsRepository private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    fun update(block: AppSettings.() -> AppSettings) {
        val next = _settings.value.block()
        _settings.value = next
        prefs.edit().apply {
            putInt(KEY_WINDOW_MS, next.windowMs)
            putLong(KEY_CALIBRATION, next.calibrationOffsetDb.toBits())
            putString(KEY_MODE, next.detectionMode.name)
            putLong(KEY_ABS_THRESHOLD, next.absoluteThresholdDb.toBits())
            putLong(KEY_DELTA, next.deltaDb.toBits())
            putLong(KEY_MIN_ABOVE, next.minAboveDurationMs)
            putLong(KEY_CALM, next.calmDurationMs)
            putBoolean(KEY_AUTO_ENABLED, next.autoStartEnabled)
            putInt(KEY_AUTO_START, next.autoStartMinutes)
            putInt(KEY_AUTO_STOP, next.autoStopMinutes)
            putInt(KEY_RETENTION, next.retentionNights)
            apply()
        }
    }

    private fun load(): AppSettings = AppSettings(
        windowMs = prefs.getInt(KEY_WINDOW_MS, 250).coerceIn(100, 1000),
        calibrationOffsetDb = Double.fromBits(prefs.getLong(KEY_CALIBRATION, 90.0.toBits())),
        detectionMode = runCatching { DetectionMode.valueOf(prefs.getString(KEY_MODE, "") ?: "") }
            .getOrDefault(DetectionMode.DEFERRED_RELATIVE),
        absoluteThresholdDb = Double.fromBits(prefs.getLong(KEY_ABS_THRESHOLD, 55.0.toBits())),
        deltaDb = Double.fromBits(prefs.getLong(KEY_DELTA, 10.0.toBits())),
        minAboveDurationMs = prefs.getLong(KEY_MIN_ABOVE, 500),
        calmDurationMs = prefs.getLong(KEY_CALM, 10_000),
        autoStartEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, false),
        autoStartMinutes = prefs.getInt(KEY_AUTO_START, 23 * 60),
        autoStopMinutes = prefs.getInt(KEY_AUTO_STOP, 7 * 60),
        retentionNights = prefs.getInt(KEY_RETENTION, 0)
    )

    companion object {
        private const val PREFS_NAME = "soundlogger_settings"
        private const val KEY_WINDOW_MS = "window_ms"
        private const val KEY_CALIBRATION = "calibration_offset_db"
        private const val KEY_MODE = "detection_mode"
        private const val KEY_ABS_THRESHOLD = "absolute_threshold_db"
        private const val KEY_DELTA = "delta_db"
        private const val KEY_MIN_ABOVE = "min_above_ms"
        private const val KEY_CALM = "calm_ms"
        private const val KEY_AUTO_ENABLED = "auto_start_enabled"
        private const val KEY_AUTO_START = "auto_start_minutes"
        private const val KEY_AUTO_STOP = "auto_stop_minutes"
        private const val KEY_RETENTION = "retention_nights"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
