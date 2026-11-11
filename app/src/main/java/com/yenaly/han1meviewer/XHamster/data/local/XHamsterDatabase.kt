package com.yenaly.han1meviewer.XHamster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [XHamsterHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class XHamsterDatabase : RoomDatabase() {
    abstract fun xHamsterHistoryDao(): XHamsterHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: XHamsterDatabase? = null

        fun getInstance(context: Context): XHamsterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    XHamsterDatabase::class.java,
                    "xhamster_history.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
