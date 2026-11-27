package com.polo.soundlogger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polo.soundlogger.data.AppDatabase
import com.polo.soundlogger.data.Night
import com.polo.soundlogger.data.Sample
import com.polo.soundlogger.export.CsvExporter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Historique multi-nuits (spec §4.3) : une ligne par nuit (date, Leq, nb
 * d'événements, durée, sparkline), avec renommage, suppression et export CSV.
 * (Pas de comparaison superposée de plusieurs nuits : retiré des specs.)
 */
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nights by remember { mutableStateOf<List<Night>>(emptyList()) }
    var sparks by remember { mutableStateOf<Map<Long, List<Sample>>>(emptyMap()) }
    var toDelete by remember { mutableStateOf<Night?>(null) }
    var toRename by remember { mutableStateOf<Night?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            val dao = AppDatabase.get(context).nightDao()
            val list = dao.allNights()
            nights = list
            // sparkline : un échantillon sur 20 pour ne pas charger toute la nuit
            sparks = list.associate { n ->
                n.id to dao.samplesOfNight(n.id).filterIndexed { i, _ -> i % 20 == 0 }
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

    if (nights.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Historique des nuits", fontSize = 22.sp)
            Text("Aucune nuit archivée.", fontSize = 14.sp)
        }
        return
    }

    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Historique des nuits (${nights.size})", fontSize = 22.sp) }
        items(nights, key = { it.id }) { night ->
            val samples = sparks[night.id].orEmpty()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        (night.label ?: "Nuit du ${dateFmt.format(Instant.ofEpochMilli(night.startTime))}") +
                            if (night.endTime == null) " (en cours)" else "",
                        fontSize = 16.sp
                    )
                    Text(
                        buildString {
                            if (night.endTime != null) {
                                val durS = (night.endTime!! - night.startTime) / 1000
                                append("durée ${durS / 3600} h ${(durS % 3600) / 60} min — ")
                            }
                            append(
                                if (night.leq != null)
                                    String.format(Locale.US, "Leq %.1f dB — ", night.leq!!)
                                else "Leq — dB — "
                            )
                            append("${night.eventCount ?: 0} événement(s)")
                        },
                        fontSize = 13.sp
                    )
                    Sparkline(samples, Modifier.fillMaxWidth().height(48.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val dao = AppDatabase.get(context).nightDao()
                                val s = dao.samplesOfNight(night.id)
                                val f = CsvExporter.export(context, night, s)
                                CsvExporter.share(context, f)
                            }
                        }) { Text("Export CSV") }
                        OutlinedButton(onClick = { toRename = night; renameText = night.label ?: "" }) {
                            Text("Renommer")
                        }
                        OutlinedButton(onClick = { toDelete = night }) { Text("Supprimer") }
                    }
                }
            }
        }
    }

    // confirmation suppression
    toDelete?.let { night ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer cette nuit ?") },
            text = { Text("Samples, événements et stats de la nuit du ${dateFmt.format(Instant.ofEpochMilli(night.startTime))} seront supprimés définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        AppDatabase.get(context).nightDao().deleteNightCascade(night.id)
                        toDelete = null
                        reload()
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }

    // renommage
    toRename?.let { night ->
        AlertDialog(
            onDismissRequest = { toRename = null },
            title = { Text("Renommer la nuit") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nom") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        AppDatabase.get(context).nightDao()
                            .renameNight(night.id, renameText.ifBlank { null })
                        toRename = null
                        reload()
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { toRename = null }) { Text("Annuler") } }
        )
    }
}

/** Mini-courbe (sparkline) du Leq d'une nuit. */
@Composable
private fun Sparkline(samples: List<Sample>, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) return
    val minDb = samples.minOf { it.minDb }.toFloat()
    val maxDb = samples.maxOf { it.maxDb }.toFloat().coerceAtLeast(minDb + 1f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val step = w / samples.size.coerceAtLeast(1)
        var prev = Offset(0f, h - (samples[0].leq.toFloat() - minDb) / (maxDb - minDb) * h)
        samples.forEachIndexed { i, s ->
            val x = i * step
            val y = h - (s.leq.toFloat() - minDb) / (maxDb - minDb) * h
            val c = when {
                s.leq < 45.0 -> Color(0xFF4CAF50)
                s.leq < 60.0 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
            drawLine(c, prev, Offset(x, y), strokeWidth = 1.5f)
            prev = Offset(x, y)
        }
    }
}
