package com.polo.soundlogger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.polo.soundlogger.data.Event
import com.polo.soundlogger.data.Sample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Courbe du Leq d'une nuit (spec §4.2) : tracé Canvas Compose avec
 * - couleur par plage (vert < 45, orange 45–60, rouge > 60 dB),
 * - bande min/max par colonne de pixels,
 * - marqueurs d'événements (tap → [onEventSelected]),
 * - zoom/pan au doigt (detectTransformGestures), zoom sur l'événement via [focusEvent].
 */
@Composable
fun NightChart(
    samples: List<Sample>,
    events: List<Event>,
    focusEvent: Event? = null,
    onEventSelected: (Event) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (samples.isEmpty()) return

    var scale by remember(samples) { mutableFloatStateOf(1f) }      // 1 = toute la nuit
    var firstIndex by remember(samples) { mutableFloatStateOf(0f) }   // index du bord gauche

    // Quand on sélectionne un événement, zoom dessus (courbe zoomée de l'événement, spec §4.2)
    LaunchedEffect(focusEvent) {
        val ev = focusEvent ?: return@LaunchedEffect
        val startIdx = samples.indexOfFirst { it.ts >= ev.startMs }.coerceAtLeast(0)
        val endIdx = samples.indexOfLast { it.ts <= ev.endMs }.coerceAtLeast(startIdx)
        val span = (endIdx - startIdx).coerceAtLeast(1)
        scale = (samples.size / span.toFloat()).coerceIn(1f, 50f)
        firstIndex = (startIdx - span * 0.1f).coerceAtLeast(0f)
    }

    val dbMin = 20f
    val dbMax = 100f
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.LightGray, fontSize = 11.sp)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(samples, events) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 50f)
                    val visible = samples.size / newScale
                    val pxPerIdx = size.width / visible
                    firstIndex = (firstIndex - pan.x / pxPerIdx)
                        .coerceIn(0f, max(0f, samples.size - visible))
                    scale = newScale
                }
            }
            .pointerInput(samples, events, scale, firstIndex) {
                detectTapGestures { tap ->
                    val visible = samples.size / scale
                    val pxPerIdx = size.width / visible
                    val idx = firstIndex + tap.x / pxPerIdx
                    val nearest = events.minByOrNull { ev ->
                        val evIdx = samples.indexOfFirst { it.ts >= ev.startMs }.coerceAtLeast(0)
                        abs(evIdx - idx) * pxPerIdx
                    }
                    if (nearest != null) {
                        val evIdx = samples.indexOfFirst { it.ts >= nearest.startMs }.coerceAtLeast(0)
                        if (abs(evIdx - idx) * pxPerIdx < 24f) onEventSelected(nearest)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val visible = samples.size / scale
        val pxPerIdx = w / visible

        fun xOf(i: Float) = (i - firstIndex) * pxPerIdx
        fun yOf(db: Float) = h - (db - dbMin) / (dbMax - dbMin) * h

        // bande min/max + courbe Leq, couleur par plage
        val colStep = max(1, (visible / w).toInt()) // sous-échantillonnage par colonne
        var i = firstIndex.toInt().coerceIn(0, samples.lastIndex)
        var prevX = xOf(i.toFloat())
        var prevY = yOf(samples[i].leq.toFloat())
        var prevColor = colorFor(samples[i].leq)
        while (i <= samples.lastIndex) {
            val x = xOf(i.toFloat())
            if (x > w + 4) break
            val s = samples[i]
            val y = yOf(s.leq.toFloat())
            if (x >= -4) {
                drawLine(prevColor, Offset(prevX, prevY), Offset(x, y), strokeWidth = 2f)
                // bande min/max
                drawLine(
                    prevColor.copy(alpha = 0.25f),
                    Offset(x, yOf(s.minDb.toFloat())),
                    Offset(x, yOf(s.maxDb.toFloat())),
                    strokeWidth = 1f
                )
            }
            prevX = x; prevY = y; prevColor = colorFor(s.leq)
            i += colStep
        }

        // marqueurs d'événements
        for (ev in events) {
            val evIdx = samples.indexOfFirst { it.ts >= ev.startMs }
            if (evIdx < 0) continue
            val x = xOf(evIdx.toFloat())
            if (x < -4 || x > w + 4) continue
            drawLine(Color(0xFFE91E63), Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, 10f), strokeWidth = 3f)
        }

        // axe du temps : 5 graduations
        repeat(5) { k ->
            val x = w * k / 4f
            val idx = (firstIndex + k / 4f * visible).toInt().coerceIn(0, samples.lastIndex)
            val label = timeFmt.format(Instant.ofEpochMilli(samples[idx].ts))
            drawText(textMeasurer.measure(label, labelStyle), topLeft = Offset(x + 4f, h - 22f))
        }
        // axe dB
        for (db in listOf(40, 60, 80)) {
            val y = yOf(db.toFloat())
            drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            drawText(textMeasurer.measure("$db", labelStyle), topLeft = Offset(4f, y - 18f))
        }
    }
}

private fun colorFor(db: Double): Color = when {
    db < 45.0 -> Color(0xFF4CAF50) // vert
    db < 60.0 -> Color(0xFFFF9800) // orange
    else -> Color(0xFFF44336)      // rouge
}
