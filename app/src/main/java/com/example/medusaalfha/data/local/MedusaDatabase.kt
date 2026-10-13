package com.example.medusaalfha.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.medusaalfha.data.local.dao.CheckpointDao
import com.example.medusaalfha.data.local.dao.PatrolReportDao
import com.example.medusaalfha.data.local.entity.CheckpointEntity
import com.example.medusaalfha.data.local.entity.PatrolReportEntity

@Database(
    entities = [CheckpointEntity::class, PatrolReportEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MedusaDatabase : RoomDatabase() {

    abstract fun checkpointDao(): CheckpointDao
    abstract fun patrolReportDao(): PatrolReportDao

    companion object {
        @Volatile
        private var INSTANCE: MedusaDatabase? = null

        fun getInstance(context: Context): MedusaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedusaDatabase::class.java,
                    "medusa_patrol.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): MedusaDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                MedusaDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
