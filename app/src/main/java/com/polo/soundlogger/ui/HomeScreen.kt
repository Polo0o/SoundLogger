package com.polo.soundlogger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.polo.soundlogger.data.SettingsRepository
import com.polo.soundlogger.service.RecordingService
import com.polo.soundlogger.service.RecordingServiceController
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Permissions runtime nécessaires à l'enregistrement (spec §2.3). */
fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

fun hasAllPermissions(context: android.content.Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Écran principal (spec §4.1) : démarrer/arrêter la nuit, niveau courant,
 * paramétrage des événements (TODO v0.2).
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val recording by RecordingServiceController.isRecording.collectAsState()
    val currentDb by RecordingServiceController.currentLevel.collectAsState()
    val referenceDb by RecordingServiceController.referenceDb.collectAsState()
    val sessionStartMs by RecordingServiceController.sessionStartMs.collectAsState()
    var permissionDenied by remember { mutableStateOf(false) }
    var showDetection by remember { mutableStateOf(false) }
    val settings by SettingsRepository.get(context).settings.collectAsState()

    // Horloge + chronomètre de la session, rafraîchis chaque seconde pendant l'enregistrement
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording) {
        while (recording) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        } else {
            permissionDenied = true
        }
    }

    fun toggle() {
        val intent = Intent(context, RecordingService::class.java)
        if (recording) {
            context.stopService(intent)
        } else if (hasAllPermissions(context)) {
            context.startForegroundService(intent)
        } else {
            permissionDenied = false
            launcher.launch(requiredPermissions())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (recording) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Nuit en cours — niveau courant : " + "%.1f".format(currentDb) + " dB", fontSize = 20.sp)
            Text("Heure actuelle : ${clockFmt.format(Instant.ofEpochMilli(now))}", fontSize = 18.sp)
            sessionStartMs?.let { start ->
                Text(
                    "Surveillance depuis ${formatDuration(now - start)} " +
                        "(démarrée à ${clockFmt.format(Instant.ofEpochMilli(start))})",
                    fontSize = 14.sp
                )
            }
            referenceDb?.let {
                Text("Référence mesurée (moyenne t0+15 → t0+30 min) : " + "%.1f".format(it) + " dB", fontSize = 12.sp)
            }
            Text("(le service foreground enregistre, écran verrouillé OK)", fontSize = 12.sp)
        } else {
            Text("SoundLogger", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("Prêt à enregistrer la nuit.", fontSize = 14.sp)
            // Rappel de la session programmée si active (spec §4.1)
            if (settings.autoStartEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Session programmée : ${settings.autoStartMinutes / 60} h → ${settings.autoStopMinutes / 60} h",
                    fontSize = 12.sp
                )
            }
        }
        if (permissionDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Permission micro refusée : l'enregistrement est impossible sans RECORD_AUDIO.",
                fontSize = 12.sp
            )
            OutlinedButton(onClick = { launcher.launch(requiredPermissions()) }) {
                Text("Réessayer les permissions")
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { toggle() }) {
            Text(if (recording) "Arrêter la nuit" else "Démarrer la nuit")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showDetection = true }) {
            Text("Détection d'événements")
        }
    }

    if (showDetection) {
        DetectionDialog(settings = settings, onDismiss = { showDetection = false })
    }
}

private fun formatDuration(durationMs: Long): String {
    val s = durationMs / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
