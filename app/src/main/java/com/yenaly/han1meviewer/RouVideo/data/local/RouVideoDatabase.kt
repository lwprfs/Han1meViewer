package com.yenaly.han1meviewer.RouVideo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RouVideoHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RouVideoDatabase : RoomDatabase() {
    abstract fun rouVideoHistoryDao(): RouVideoHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: RouVideoDatabase? = null

        fun getInstance(context: Context): RouVideoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RouVideoDatabase::class.java,
                    "rouvideo_history.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
