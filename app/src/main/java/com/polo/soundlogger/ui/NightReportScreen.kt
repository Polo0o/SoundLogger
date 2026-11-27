package com.polo.soundlogger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polo.soundlogger.data.AppDatabase
import com.polo.soundlogger.data.Event
import com.polo.soundlogger.data.Night
import com.polo.soundlogger.data.NightStats
import com.polo.soundlogger.data.Sample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rapport du matin (spec §4.2) : courbe du Leq (zoom/pan, couleur par plage,
 * marqueurs d'événements), statistiques (Leq, min/max, L10/L50/L90, heure la plus
 * bruyante), liste des événements tapables, comparaison au Leq moyen des nuits
 * précédentes.
 */
@Composable
fun NightReportScreen() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<Report?>(null) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    LaunchedEffect(Unit) {
        val dao = AppDatabase.get(context).nightDao()
        val nights = dao.allNights()
        // nuit affichée : la dernière clôturée, sinon la dernière tout court
        val night = nights.firstOrNull { it.endTime != null } ?: nights.firstOrNull()
        if (night != null) {
            val samples = dao.samplesOfNight(night.id)
            val events = dao.eventsOfNight(night.id)
            val stats = NightStats.compute(samples)
            val prev = nights.filter { it.id != night.id && it.leq != null }.map { it.leq!! }
            report = Report(
                night = night,
                samples = samples,
                events = events,
                stats = stats,
                previousNightsMeanDb = prev.takeIf { it.isNotEmpty() }?.average()
            )
        }
    }

    val r = report
    if (r == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Rapport de la nuit", fontSize = 22.sp)
            Text("Aucune nuit enregistrée pour le moment.", fontSize = 14.sp)
        }
        return
    }

    val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Rapport — nuit du ${dateFmt.format(Instant.ofEpochMilli(r.night.startTime))}", fontSize = 20.sp)
            if (r.night.endTime == null) Text("(session en cours)", fontSize = 12.sp)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                NightChart(
                    samples = r.samples,
                    events = r.events,
                    focusEvent = selectedEvent,
                    onEventSelected = { selectedEvent = it },
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            }
        }
        r.stats?.let { st ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Statistiques", fontSize = 16.sp)
                        Text(String.format(Locale.US, "Leq global : %.1f dB   min : %.1f   max : %.1f", st.leqDb, st.minDb, st.maxDb))
                        Text(String.format(Locale.US, "L10 : %.1f   L50 : %.1f   L90 : %.1f dB", st.l10, st.l50, st.l90))
                        st.loudestHourStartMs?.let {
                            Text("Heure la plus bruyante : ${timeFmt.format(Instant.ofEpochMilli(it))}")
                        }
                        r.previousNightsMeanDb?.let { mean ->
                            Text(String.format(Locale.US, "vs moyenne des nuits précédentes (%.1f dB) : %+.1f dB", mean, st.leqDb - mean))
                        }
                    }
                }
            }
        }
        item { Text("Événements (${r.events.size}) — touchez un marqueur ou une ligne pour zoomer/détail", fontSize = 16.sp) }
        items(r.events) { ev ->
            Card(
                Modifier.fillMaxWidth().clickable { selectedEvent = ev }
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${timeFmt.format(Instant.ofEpochMilli(ev.startMs))} → " +
                            "${timeFmt.format(Instant.ofEpochMilli(ev.endMs))}  " +
                            "(${formatDuration(ev.endMs - ev.startMs)})" +
                            if (ev.truncated) "  [tronqué]" else "",
                        fontSize = 14.sp
                    )
                    Text(
                        String.format(
                            Locale.US,
                            "Leq %.1f dB — max %.1f dB — %+.1f dB vs moyenne de la nuit",
                            ev.leqDb, ev.maxDb, ev.deltaVsNightMeanDb
                        ),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val s = durationMs / 1000
    return if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"
}

private data class Report(
    val night: Night,
    val samples: List<Sample>,
    val events: List<Event>,
    val stats: NightStats.Stats?,
    val previousNightsMeanDb: Double?
)
