package com.yenaly.han1meviewer.SupJav.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SupJavHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SupJavDatabase : RoomDatabase() {
    abstract fun supJavHistoryDao(): SupJavHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SupJavDatabase? = null

        fun getInstance(context: Context): SupJavDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SupJavDatabase::class.java,
                    "supjav_history.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
