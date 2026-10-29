package com.yenaly.han1meviewer.FapFolder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FapFolderHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FapFolderDatabase : RoomDatabase() {

    abstract fun fapFolderHistoryDao(): FapFolderHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: FapFolderDatabase? = null

        fun getInstance(context: Context): FapFolderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FapFolderDatabase::class.java,
                    "fapfolder_history.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
