package com.polo.soundlogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polo.soundlogger.data.AppSettings
import com.polo.soundlogger.data.SettingsRepository
import com.polo.soundlogger.detection.DetectionMode
import java.util.Locale

/**
 * Modal de paramétrage des événements (spec §4.1, invariants AGENTS.md 3) :
 * - mode seuil absolu (slider dB) ou seuil relatif différé (+Δ dB sur la
 *   moyenne t0+15 → t0+30 min, fenêtre fixe affichée) ;
 * - durée minimale de dépassement (défaut 500 ms) et retour au calme (défaut 10 s).
 */
@Composable
fun DetectionDialog(
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = SettingsRepository.get(context)

    var mode by remember { mutableStateOf(settings.detectionMode) }
    var absThreshold by remember { mutableDoubleStateOf(settings.absoluteThresholdDb) }
    var delta by remember { mutableDoubleStateOf(settings.deltaDb) }
    var minAboveS by remember { mutableDoubleStateOf(settings.minAboveDurationMs / 1000.0) }
    var calmS by remember { mutableDoubleStateOf(settings.calmDurationMs / 1000.0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Détection d'événements") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // mode seuil absolu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == DetectionMode.ABSOLUTE_THRESHOLD,
                        onClick = { mode = DetectionMode.ABSOLUTE_THRESHOLD }
                    )
                    Column {
                        Text("Seuil absolu")
                        Text(
                            String.format(Locale.US, "%.0f dB", absThreshold),
                            fontSize = 12.sp
                        )
                    }
                }
                if (mode == DetectionMode.ABSOLUTE_THRESHOLD) {
                    Slider(
                        value = absThreshold.toFloat(),
                        onValueChange = { absThreshold = it.toDouble() },
                        valueRange = 40f..80f, steps = 39
                    )
                }
                // mode relatif différé
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == DetectionMode.DEFERRED_RELATIVE,
                        onClick = { mode = DetectionMode.DEFERRED_RELATIVE }
                    )
                    Column {
                        Text("Seuil relatif différé (défaut)")
                        Text(
                            String.format(Locale.US, "+%.0f dB au-dessus de la moyenne t0+15 → t0+30 min", delta),
                            fontSize = 12.sp
                        )
                    }
                }
                if (mode == DetectionMode.DEFERRED_RELATIVE) {
                    Slider(
                        value = delta.toFloat(),
                        onValueChange = { delta = it.toDouble() },
                        valueRange = 3f..20f, steps = 16
                    )
                }
                Text("Durée minimale de dépassement : ${"%.2f".format(minAboveS)} s", fontSize = 12.sp)
                Slider(
                    value = minAboveS.toFloat(),
                    onValueChange = { minAboveS = it.toDouble() },
                    valueRange = 0.25f..2f, steps = 6
                )
                Text("Retour au calme : ${"%.0f".format(calmS)} s", fontSize = 12.sp)
                Slider(
                    value = calmS.toFloat(),
                    onValueChange = { calmS = it.toDouble() },
                    valueRange = 2f..30f, steps = 27
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                repo.update {
                    copy(
                        detectionMode = mode,
                        absoluteThresholdDb = absThreshold,
                        deltaDb = delta,
                        minAboveDurationMs = (minAboveS * 1000).toLong(),
                        calmDurationMs = (calmS * 1000).toLong()
                    )
                }
                onDismiss()
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
