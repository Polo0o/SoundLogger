package com.polo.soundlogger.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polo.soundlogger.data.SettingsRepository
import com.polo.soundlogger.service.NightScheduler
import java.util.Locale

/**
 * Paramètres (spec §4.4) : fréquence d'échantillonnage (100–1000 ms, défaut 250)
 * et calibration du micro (offset dB, à affiner avec un sonomètre de référence).
 * TODO v0.2 : heures auto start/stop, seuils de détection, thème, rétention.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings by SettingsRepository.get(context).settings.collectAsState()
    val repo = SettingsRepository.get(context)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Réglages", fontSize = 22.sp)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fréquence de mesure : ${settings.windowMs} ms")
                Text(
                    "Un point de niveau toutes les ${settings.windowMs} ms. " +
                        "250 ms par défaut (≈ 300 Ko/nuit de 8 h en CSV).",
                    fontSize = 12.sp
                )
                Slider(
                    value = settings.windowMs.toFloat(),
                    onValueChange = { repo.update { copy(windowMs = it.toInt().coerceIn(100, 1000).let { v -> if (v % 50 == 0) v else (v / 50) * 50 }) } },
                    valueRange = 100f..1000f,
                    steps = 17
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(String.format(Locale.US, "Calibration micro : %.1f dB", settings.calibrationOffsetDb))
                Text(
                    "Offset appliqué aux mesures (dB SPL approximatif). Aide : placez le téléphone " +
                        "à côté d'un sonomètre de référence, lancez une mesure, puis ajustez ce réglage " +
                        "pour que les deux affichent la même valeur.",
                    fontSize = 12.sp
                )
                Slider(
                    value = settings.calibrationOffsetDb.toFloat(),
                    onValueChange = { repo.update { copy(calibrationOffsetDb = it.toDouble().let { v -> kotlin.math.round(v * 2) / 2 }) } },
                    valueRange = 60f..120f,
                    steps = 119
                )
            }
        }

        // Immunité Doze (spec §2.3) : le wake lock partiel est déjà pris par le service ;
        // demander en plus l'exemption d'optimisation batterie évite que le système
        // ne tue le service en mode Doze (téléphone branché la nuit).
        val pm = context.getSystemService(PowerManager::class.java)
        val exempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optimisation batterie : ${if (exempt) "ignorée (OK)" else "active — risque en mode Doze"}")
                if (!exempt) {
                    OutlinedButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }) {
                        Text("Autoriser SoundLogger à ignorer l'optimisation batterie")
                    }
                }
            }
        }

        // Session programmée (spec §4.4, étape 5) : auto start/stop via WorkManager.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = settings.autoStartEnabled,
                        onCheckedChange = {
                            repo.update { copy(autoStartEnabled = it) }
                            NightScheduler.scheduleNext(context)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Session programmée (auto start/stop)")
                }
                if (settings.autoStartEnabled) {
                    Text("Début : ${settings.autoStartMinutes / 60} h ${"%02d".format(settings.autoStartMinutes % 60)}")
                    Slider(
                        value = (settings.autoStartMinutes / 60).toFloat(),
                        onValueChange = {
                            repo.update { copy(autoStartMinutes = (it.toInt() * 60).coerceIn(0, 23 * 60 + 59)) }
                            NightScheduler.scheduleNext(context)
                        },
                        valueRange = 0f..23f, steps = 22
                    )
                    Text("Fin : ${settings.autoStopMinutes / 60} h ${"%02d".format(settings.autoStopMinutes % 60)}")
                    Slider(
                        value = (settings.autoStopMinutes / 60).toFloat(),
                        onValueChange = {
                            repo.update { copy(autoStopMinutes = (it.toInt() * 60).coerceIn(0, 23 * 60 + 59)) }
                            NightScheduler.scheduleNext(context)
                        },
                        valueRange = 0f..23f, steps = 22
                    )
                }
            }
        }

        // Rétention (spec §5) : 0 = illimité (défaut)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (settings.retentionNights == 0) "Rétention : illimitée" else "Rétention : ${settings.retentionNights} nuits max")
                Slider(
                    value = settings.retentionNights.toFloat(),
                    onValueChange = { repo.update { copy(retentionNights = it.toInt()) } },
                    valueRange = 0f..90f, steps = 89
                )
            }
        }
    }
}
