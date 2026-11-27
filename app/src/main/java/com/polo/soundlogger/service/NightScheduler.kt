package com.polo.soundlogger.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.polo.soundlogger.data.SettingsRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Planification des sessions automatiques (spec §4.4, étape 5) :
 * démarre/arrête le service aux heures réglées (défaut 23 h → 7 h),
 * replanifié à chaque exécution et après un reboot ([BootReceiver]).
 */
object NightScheduler {
    private const val TAG_START = "night_start"
    private const val TAG_STOP = "night_stop"

    /** (Re)planifie les prochains démarrages/arrêts selon les réglages. */
    fun scheduleNext(context: Context) {
        val settings = SettingsRepository.get(context).settings.value
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG_START)
        wm.cancelAllWorkByTag(TAG_STOP)
        if (!settings.autoStartEnabled) return
        val now = System.currentTimeMillis()
        val startAt = nextOccurrence(now, settings.autoStartMinutes)
        val stopAt = nextOccurrence(now, settings.autoStopMinutes)
        wm.enqueue(
            OneTimeWorkRequestBuilder<StartNightWorker>()
                .setInitialDelay(startAt - now, TimeUnit.MILLISECONDS)
                .addTag(TAG_START)
                .build()
        )
        wm.enqueue(
            OneTimeWorkRequestBuilder<StopNightWorker>()
                .setInitialDelay(stopAt - now, TimeUnit.MILLISECONDS)
                .addTag(TAG_STOP)
                .build()
        )
    }

    /** Prochaine occurrence (ms) de minutesOfDay après nowMs. */
    private fun nextOccurrence(nowMs: Long, minutesOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
        }
        if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

/** Démarre la session de nuit à l'heure prévue. */
class StartNightWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ctx.startForegroundService(Intent(ctx, RecordingService::class.java))
        }
        NightScheduler.scheduleNext(ctx) // replanifie le cycle suivant
        return Result.success()
    }
}

/** Arrête la session de nuit à l'heure prévue. */
class StopNightWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        applicationContext.stopService(Intent(applicationContext, RecordingService::class.java))
        NightScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}

/** Relance la planification après un reboot (RECEIVE_BOOT_COMPLETED, spec §2.3). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NightScheduler.scheduleNext(context)
        }
    }
}
