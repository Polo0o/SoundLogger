package com.polo.soundlogger.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.polo.soundlogger.data.Night
import com.polo.soundlogger.data.Sample
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Export CSV par nuit (spec §5) : night_YYYY-MM-DD.csv,
 * timestamp ISO-8601 + dB, partageable via intent (mail, Drive...).
 * NOTE: nécessite un FileProvider déclaré dans le manifest (TODO v0.2).
 */
object CsvExporter {

    fun export(context: Context, night: Night, samples: List<Sample>): File {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(night.startTime), ZoneId.systemDefault())
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "night_" + date + ".csv")
        file.writeText(buildString {
            appendLine("timestamp,leq_db,min_db,max_db")
            samples.forEach { s ->
                val ts = Instant.ofEpochMilli(s.ts).atZone(ZoneId.systemDefault()).toLocalDateTime()
                appendLine(ts.toString() + "," + "%.1f".format(s.leq) + "," +
                    "%.1f".format(s.minDb) + "," + "%.1f".format(s.maxDb))
            }
        })
        return file
    }

    /** Intent de partage du fichier exporté. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager la nuit"))
    }
}
