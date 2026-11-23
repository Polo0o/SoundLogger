package com.polo.soundlogger.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

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
    val stats: String? = null          // TODO v0.2 : L10/L50/L90, agrégats minute/heure
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

@Dao
interface NightDao {
    @Insert
    suspend fun insertNight(night: Night): Long

    @Insert
    suspend fun insertSample(sample: Sample)

    @Query("UPDATE Night SET endTime = :endTime WHERE id = :id")
    suspend fun finishNight(id: Long, endTime: Long)

    @Query("SELECT * FROM Night ORDER BY startTime DESC")
    suspend fun allNights(): List<Night>

    @Query("SELECT * FROM Sample WHERE nightId = :nightId ORDER BY ts")
    suspend fun samplesOfNight(nightId: Long): List<Sample>

    @Query("DELETE FROM Night WHERE id = :id")
    suspend fun deleteNight(id: Long)
}
