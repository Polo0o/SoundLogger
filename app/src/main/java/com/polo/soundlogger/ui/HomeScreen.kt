package com.polo.soundlogger.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polo.soundlogger.service.RecordingService

/**
 * Écran principal (spec §4.1) : démarrer/arrêter la nuit, niveau courant,
 * paramétrage des événements (TODO v0.2).
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val recording by com.polo.soundlogger.service.RecordingServiceController.isRecording.collectAsState()
    val currentDb by com.polo.soundlogger.service.RecordingServiceController.currentLevel.collectAsState()

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
        Spacer(Modifier.height(32.dp))
        Button(onClick = {
            val intent = Intent(context, RecordingService::class.java)
            if (recording) context.stopService(intent)
            else context.startForegroundService(intent)
        }) {
            Text(if (recording) "Arrêter la nuit" else "Démarrer la nuit")
        }
        // TODO v0.2 : modal de paramétrage des événements :
        //  - mode seuil absolu (slider, ex. 55 dB)
        //  - mode seuil relatif différé (+delta dB sur la moyenne t0+15 -> t0+30 min)
        //  - durée minimale de dépassement (500 ms), retour au calme (10 s)
    }
}
