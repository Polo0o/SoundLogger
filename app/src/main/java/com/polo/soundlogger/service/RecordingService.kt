package com.polo.soundlogger.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.polo.soundlogger.R
import com.polo.soundlogger.audio.SoundLevelMeter
import com.polo.soundlogger.data.AppDatabase
import com.polo.soundlogger.data.Event
import com.polo.soundlogger.data.Night
import com.polo.soundlogger.data.NightStats
import com.polo.soundlogger.data.Sample
import com.polo.soundlogger.data.SettingsRepository
import com.polo.soundlogger.detection.DetectionConfig
import com.polo.soundlogger.detection.EventDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** État partagé service -> UI. */
object RecordingServiceController {
    val isRecording: StateFlow<Boolean> = MutableStateFlow(false)
    val currentLevel: StateFlow<Double> = MutableStateFlow(0.0)
    /** Référence mesurée (moyenne t0+15→t0+30 min) en mode relatif, null sinon (spec §4.1). */
    val referenceDb: StateFlow<Double?> = MutableStateFlow(null)
}

/**
 * Foreground service de type microphone (spec §2.3) : mesure pendant toute la
 * nuit, écran verrouillé, wake lock partiel, notification permanente.
 * Alimente la détection d'événements (spec §3) et persiste samples + événements.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var meter: SoundLevelMeter? = null
    private var detector: EventDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var nightId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (RecordingServiceController.isRecording.value) return START_STICKY
        // Garde-fou : sans RECORD_AUDIO, AudioRecord lèverait SecurityException.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        // Wake lock partiel : le CPU ne dort pas entre les fenêtres.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundLogger:night")
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 h max, nuit type

        (RecordingServiceController.isRecording as MutableStateFlow).value = true

        scope.launch {
            val settings = SettingsRepository.get(applicationContext).settings.value
            val dao = AppDatabase.get(applicationContext).nightDao()
            // Service tué puis relancé (START_STICKY) : clôturer la nuit orpheline
            // (discontinuité marquée par la fin de l'ancienne nuit, spec §2.3).
            dao.latestOpenNight()?.let { orphan ->
                val orphanStats = NightStats.compute(dao.samplesOfNight(orphan.id))
                if (orphanStats != null) {
                    dao.finishNightWithStats(orphan.id, System.currentTimeMillis(),
                        orphanStats.leqDb, orphanStats.minDb, orphanStats.maxDb,
                        dao.eventsOfNight(orphan.id).size, "")
                } else {
                    dao.finishNight(orphan.id, System.currentTimeMillis())
                }
            }
            nightId = dao.insertNight(Night(startTime = System.currentTimeMillis()))
            detector = EventDetector(
                DetectionConfig(
                    mode = settings.detectionMode,
                    absoluteThresholdDb = settings.absoluteThresholdDb,
                    deltaDb = settings.deltaDb,
                    minAboveDurationMs = settings.minAboveDurationMs,
                    calmDurationMs = settings.calmDurationMs
                )
            ).apply { startSession(System.currentTimeMillis()) }
            meter = SoundLevelMeter(
                windowMs = settings.windowMs,
                calibrationOffsetDb = settings.calibrationOffsetDb
            ).apply {
                onLevel = { level ->
                    (RecordingServiceController.currentLevel as MutableStateFlow).value = level.leqDb
                    (RecordingServiceController.referenceDb as MutableStateFlow).value = detector?.referenceDb
                    if (nightId > 0) {
                        // insertSample est suspend : on est sur le thread du meter.
                        val sample = Sample(nightId = nightId, ts = level.timestampMs,
                            leq = level.leqDb, minDb = level.minDb, maxDb = level.maxDb)
                        scope.launch { dao.insertSample(sample) }
                        // détection d'événements en flux (spec §3)
                        val closed = detector?.onSample(level).orEmpty()
                        for (ev in closed) {
                            scope.launch { dao.insertEvent(ev.toEntity(nightId)) }
                        }
                    }
                }
                start()
            }
        }
        return START_STICKY // relance si le service est tué (spec §2.3)
    }

    override fun onDestroy() {
        meter?.stop() // join du thread de mesure : plus aucun callback onLevel ensuite
        meter = null
        wakeLock?.takeIf { it.isHeld }?.release()
        if (nightId > 0) {
            val id = nightId
            // runBlocking : onDestroy n'attend pas les coroutines ; ne pas perdre la clôture.
            runBlocking {
                val dao = AppDatabase.get(applicationContext).nightDao()
                // événements finaux (avec écart au bruit moyen de la nuit, spec §3)
                val finalEvents = detector?.finish(System.currentTimeMillis()).orEmpty()
                dao.deleteEventsOfNight(id)
                finalEvents.forEach { dao.insertEvent(it.toEntity(id)) }
                // rétro-analyse de la nuit : stats agrégées (spec §3, §4.2)
                val stats = NightStats.compute(dao.samplesOfNight(id))
                val statsJson = stats?.let {
                    """{"leq":${"%.2f".format(it.leqDb)},"min":${"%.2f".format(it.minDb)},""" +
                        """"max":${"%.2f".format(it.maxDb)},"l10":${"%.2f".format(it.l10)},""" +
                        """"l50":${"%.2f".format(it.l50)},"l90":${"%.2f".format(it.l90)},""" +
                        """"loudestHour":${it.loudestHourStartMs ?: "null"}}"""
                }
                if (stats != null) {
                    dao.finishNightWithStats(id, System.currentTimeMillis(),
                        stats.leqDb, stats.minDb, stats.maxDb, finalEvents.size, statsJson ?: "")
                } else {
                    dao.finishNight(id, System.currentTimeMillis())
                }
                // Rétention : suppression auto au-delà de N nuits (0 = illimité, spec §5)
                val retention = SettingsRepository.get(applicationContext).settings.value.retentionNights
                if (retention > 0) {
                    dao.allNights().drop(retention).forEach { dao.deleteNightCascade(it.id) }
                }
            }
        }
        detector = null
        scope.cancel()
        (RecordingServiceController.isRecording as MutableStateFlow).value = false
        (RecordingServiceController.currentLevel as MutableStateFlow).value = 0.0
        (RecordingServiceController.referenceDb as MutableStateFlow).value = null
        super.onDestroy()
    }

    private fun com.polo.soundlogger.detection.SoundEvent.toEntity(night: Long) = Event(
        nightId = night, startMs = startMs, endMs = endMs, leqDb = leqDb,
        maxDb = maxDb, deltaVsNightMeanDb = deltaVsNightMeanDb, truncated = truncated
    )

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "night_recording"
        private const val NOTIF_ID = 1
    }
}
