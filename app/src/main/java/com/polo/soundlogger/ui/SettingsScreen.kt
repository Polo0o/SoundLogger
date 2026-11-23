package com.polo.soundlogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Paramètres (spec §4.4) — TODO v0.2 : heures auto start/stop,
 * fréquence 100-1000 ms, seuils de détection, calibration du micro.
 */
@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Réglages")
        Card { Text("TODO : session programmée (auto start/stop)") }
        Card { Text("TODO : fréquence d'échantillonnage (défaut 250 ms)") }
        Card { Text("TODO : seuils de détection d'événements") }
        Card { Text("TODO : calibration du micro") }
    }
}
