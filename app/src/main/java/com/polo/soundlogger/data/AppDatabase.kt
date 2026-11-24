package com.polo.soundlogger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Night::class, Sample::class, Event::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "soundlogger.db")
                    // v1 -> v2 : ajout de la table events. Pas d'utilisateurs en prod :
                    // migration destructive acceptable en dev (TODO migration propre avant release).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
