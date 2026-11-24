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
import com.polo.soundlogger.service.RecordingService
import com.polo.soundlogger.service.RecordingServiceController

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
    var permissionDenied by remember { mutableStateOf(false) }

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
            Text("(le service foreground enregistre, écran verrouillé OK)", fontSize = 12.sp)
        } else {
            Text("SoundLogger", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("Prêt à enregistrer la nuit.", fontSize = 14.sp)
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
        // TODO v0.2 : modal de paramétrage des événements :
        //  - mode seuil absolu (slider, ex. 55 dB)
        //  - mode seuil relatif différé (+delta dB sur la moyenne t0+15 -> t0+30 min)
        //  - durée minimale de dépassement (500 ms), retour au calme (10 s)
    }
}
