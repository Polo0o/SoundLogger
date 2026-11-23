package com.polo.soundlogger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Night::class, Sample::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "soundlogger.db")
                    .build()
                    .also { instance = it }
            }
    }
}
