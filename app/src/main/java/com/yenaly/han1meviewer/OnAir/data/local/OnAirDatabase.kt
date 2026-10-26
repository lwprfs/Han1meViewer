package com.yenaly.han1meviewer.OnAir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OnAirHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OnAirDatabase : RoomDatabase() {

    abstract fun onAirHistoryDao(): OnAirHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: OnAirDatabase? = null

        fun getInstance(context: Context): OnAirDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OnAirDatabase::class.java,
                    "onair_history.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
