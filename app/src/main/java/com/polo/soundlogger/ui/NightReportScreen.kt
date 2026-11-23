package com.polo.soundlogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Rapport du matin (spec §4.2) — TODO v0.2 :
 * - courbe du Leq sur toute la nuit (zoom, couleur par plage : vert < 45,
 *   orange 45-60, rouge > 60 dB), marqueurs d'événements ;
 * - stats : Leq global, min/max, L10/L50/L90, heure la plus bruyante ;
 * - liste des événements : heure, Leq de l'événement, durée, écart au bruit
 *   moyen de la nuit ;
 * - comparaison avec la moyenne des nuits précédentes.
 */
@Composable
fun NightReportScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rapport de la nuit")
        Card { Text("TODO : courbe du Leq (Vico / MPAndroidChart) avec marqueurs d'événements") }
        Card { Text("TODO : statistiques (Leq, min/max, L10/L50/L90)") }
        Card { Text("TODO : liste des événements (heure, niveau moyen, durée, écart à la moyenne de la nuit)") }
    }
}
