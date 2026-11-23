package com.polo.soundlogger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Historique multi-nuits (spec §4.3) — TODO v0.2 :
 * - liste des nuits (date, Leq, nb d'événements, mini-sparkline) ;
 * - résumé tableau : une ligne par nuit, nombre d'événements de chaque nuit ;
 * - suppression / renommage.
 * (Pas de comparaison superposée : retirée des specs.)
 */
@Composable
fun HistoryScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Historique des nuits")
        Card { Text("TODO : résumé des nuits passées (date, Leq, nb d'événements, durée)") }
    }
}
