package com.polo.soundlogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.polo.soundlogger.R
import com.polo.soundlogger.audio.SoundLevelMeter
import com.polo.soundlogger.data.AppDatabase
import com.polo.soundlogger.data.Night
import com.polo.soundlogger.data.Sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** État partagé service -> UI. */
object RecordingServiceController {
    val isRecording: StateFlow<Boolean> = MutableStateFlow(false)
    val currentLevel: StateFlow<Double> = MutableStateFlow(0.0)
}

/**
 * Foreground service de type microphone (spec §2.3) : mesure pendant toute la
 * nuit, écran verrouillé, wake lock partiel, notification permanente.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var meter: SoundLevelMeter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var nightId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (RecordingServiceController.isRecording.value) return START_STICKY

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
            val dao = AppDatabase.get(applicationContext).nightDao()
            nightId = dao.insertNight(Night(startTime = System.currentTimeMillis()))
            meter = SoundLevelMeter(windowMs = 250).apply {
                onLevel = { level ->
                    (RecordingServiceController.currentLevel as MutableStateFlow).value = level.leqDb
                    if (nightId > 0) {
                        // insertSample est suspend : on est sur le thread du meter.
                        val sample = Sample(nightId = nightId, ts = level.timestampMs,
                            leq = level.leqDb, minDb = level.minDb, maxDb = level.maxDb)
                        scope.launch { dao.insertSample(sample) }
                    }
                }
                start()
            }
        }
        return START_STICKY // relance si le service est tué (spec §2.3)
    }

    override fun onDestroy() {
        meter?.stop()
        meter = null
        wakeLock?.takeIf { it.isHeld }?.release()
        if (nightId > 0) {
            val id = nightId
            scope.launch {
                AppDatabase.get(applicationContext).nightDao().finishNight(id, System.currentTimeMillis())
            }
        }
        scope.cancel()
        (RecordingServiceController.isRecording as MutableStateFlow).value = false
        (RecordingServiceController.currentLevel as MutableStateFlow).value = 0.0
        super.onDestroy()
    }

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
