package com.yenaly.han1meviewer.MissAV

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MissAvHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MissAvDatabase : RoomDatabase() {
    abstract fun missAvHistoryDao(): MissAvHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MissAvDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE missav_watch_history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE missav_watch_history ADD COLUMN isPlayed INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE missav_watch_history ADD COLUMN lastPlayedDate INTEGER"
                )
            }
        }

        fun getInstance(context: Context): MissAvDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MissAvDatabase::class.java,
                    "missav_history.db",
                )
                    .addMigrations(MIGRATION_1_2)

                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
