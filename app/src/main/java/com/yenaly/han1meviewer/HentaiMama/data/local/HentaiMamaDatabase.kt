package com.yenaly.han1meviewer.HentaiMama.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HentaiMamaHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HentaiMamaDatabase : RoomDatabase() {
    abstract fun historyDao(): HentaiMamaHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: HentaiMamaDatabase? = null

        fun getInstance(context: Context): HentaiMamaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HentaiMamaDatabase::class.java,
                    "hentaimama_history.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
