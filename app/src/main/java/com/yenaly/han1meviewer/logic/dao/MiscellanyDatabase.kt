package com.yenaly.han1meviewer.logic.dao

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.util.applicationContext

@Database(
    entities = [HKeyframeEntity::class],
    version = 1, exportSchema = false
)
abstract class MiscellanyDatabase : RoomDatabase() {

    abstract val hKeyframeDao: HKeyframeDao

    companion object {
        val instance by lazy {
            Room.databaseBuilder(
                applicationContext,
                MiscellanyDatabase::class.java,
                "miscellany.db"
            ).build()
        }
    }
}
