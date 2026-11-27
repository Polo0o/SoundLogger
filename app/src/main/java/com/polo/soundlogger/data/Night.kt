package com.polo.soundlogger.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/** Une nuit enregistrée (spec §5, table nights). */
@Entity
data class Night(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val leq: Double? = null,          // Leq global de la nuit
    val minDb: Double? = null,
    val maxDb: Double? = null,
    val eventCount: Int? = null,
    val stats: String? = null,        // JSON L10/L50/L90, heure la plus bruyante (spec §4.2)
    val label: String? = null         // renommage optionnel (spec §4.3)
)

/** Un point de mesure (spec §5, table samples) : un par fenêtre (défaut 250 ms). */
@Entity
data class Sample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val ts: Long,        // epoch ms
    val leq: Double,
    val minDb: Double,
    val maxDb: Double
)

/**
 * Un événement sonore détecté (spec §3, invariant AGENTS.md 4 :
 * début, Leq moyen de l'événement, durée, dB max, écart au bruit moyen de la nuit).
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val startMs: Long,
    val endMs: Long,
    val leqDb: Double,
    val maxDb: Double,
    val deltaVsNightMeanDb: Double,
    val truncated: Boolean = false
)

@Dao
interface NightDao {
    @Insert
    suspend fun insertNight(night: Night): Long

    @Insert
    suspend fun insertSample(sample: Sample)

    @Insert
    suspend fun insertEvent(event: Event)

    @Query("UPDATE Night SET endTime = :endTime WHERE id = :id")
    suspend fun finishNight(id: Long, endTime: Long)

    /** Clôture complète d'une nuit : fin + stats agrégées (spec §3, §4.2). */
    @Query(
        "UPDATE Night SET endTime = :endTime, leq = :leq, minDb = :minDb, maxDb = :maxDb, " +
            "eventCount = :eventCount, stats = :stats WHERE id = :id"
    )
    suspend fun finishNightWithStats(
        id: Long, endTime: Long, leq: Double, minDb: Double, maxDb: Double,
        eventCount: Int, stats: String
    )

    @Query("SELECT * FROM Night ORDER BY startTime DESC")
    suspend fun allNights(): List<Night>

    @Query("SELECT * FROM Night WHERE id = :id")
    suspend fun nightById(id: Long): Night?

    /** Dernière nuit non clôturée (service tué puis relancé : discontinuité, spec §2.3). */
    @Query("SELECT * FROM Night WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun latestOpenNight(): Night?

    @Query("SELECT * FROM Sample WHERE nightId = :nightId ORDER BY ts")
    suspend fun samplesOfNight(nightId: Long): List<Sample>

    @Query("SELECT * FROM events WHERE nightId = :nightId ORDER BY startMs")
    suspend fun eventsOfNight(nightId: Long): List<Event>

    @Query("DELETE FROM events WHERE nightId = :nightId")
    suspend fun deleteEventsOfNight(nightId: Long)

    @Query("DELETE FROM Sample WHERE nightId = :nightId")
    suspend fun deleteSamplesOfNight(nightId: Long)

    @Query("DELETE FROM Night WHERE id = :id")
    suspend fun deleteNight(id: Long)

    @Query("UPDATE Night SET label = :label WHERE id = :id")
    suspend fun renameNight(id: Long, label: String?)

    /** Suppression en cascade d'une nuit (samples + événements + nuit), spec §4.3. */
    @Transaction
    suspend fun deleteNightCascade(id: Long) {
        deleteEventsOfNight(id)
        deleteSamplesOfNight(id)
        deleteNight(id)
    }
}
