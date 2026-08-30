package com.yenaly.han1meviewer.MissAV

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yenaly.han1meviewer.BuildConfig

@Database(
    entities = [MissAvHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MissAvDatabase : RoomDatabase() {
    abstract fun missAvHistoryDao(): MissAvHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MissAvDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Example migration for future schema changes
            }
        }

        fun getInstance(context: Context): MissAvDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MissAvDatabase::class.java,
                    "missav_history.db"
                )
                .fallbackToDestructiveMigration()
                .addMigrations(MIGRATION_1_2)
                .build().also {
                    INSTANCE = it
                }
                instance
            }
        }
    }
}