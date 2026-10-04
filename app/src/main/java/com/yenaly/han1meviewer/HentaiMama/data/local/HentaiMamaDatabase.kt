package com.yenaly.han1meviewer.HentaiMama.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HentaiMamaHistoryEntity::class,
        HentaiMamaSeriesEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HentaiMamaDatabase : RoomDatabase() {
    abstract fun historyDao(): HentaiMamaHistoryDao
    abstract fun seriesDao(): HentaiMamaSeriesDao

    companion object {
        @Volatile
        private var INSTANCE: HentaiMamaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hentaimama_series` (
                        `slug` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `altTitles` TEXT NOT NULL,
                        `thumbSmall` TEXT,
                        `thumbFull` TEXT NOT NULL,
                        `posterAlt` TEXT NOT NULL,
                        `rating` REAL,
                        `favorites` INTEGER,
                        `postId` INTEGER,
                        `nonce` TEXT,
                        `studios` TEXT NOT NULL,
                        `studioUrls` TEXT NOT NULL,
                        `year` INTEGER,
                        `viewsRaw` TEXT,
                        `views` INTEGER,
                        `episodeCount` INTEGER,
                        `description` TEXT,
                        `hasLongDescription` INTEGER NOT NULL,
                        `genres` TEXT NOT NULL,
                        `genreSlugs` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`slug`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_hentaimama_series_slug` " +
                            "ON `hentaimama_series` (`slug`)"
                )
            }
        }

        fun getInstance(context: Context): HentaiMamaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HentaiMamaDatabase::class.java,
                    "hentaimama_history.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
