package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.DubStageDao
import com.example.data.model.ClipEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.TakeEntity

@Database(
    entities = [ProjectEntity::class, ClipEntity::class, TakeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DubStageDatabase : RoomDatabase() {
    abstract fun dubStageDao(): DubStageDao

    companion object {
        @Volatile
        private var INSTANCE: DubStageDatabase? = null

        fun getDatabase(context: Context): DubStageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DubStageDatabase::class.java,
                    "dubstage_studio.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
