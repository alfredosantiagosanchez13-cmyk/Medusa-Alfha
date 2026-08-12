package com.example.data.booking

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.chat.AiGuardChatLog
import com.example.data.chat.AiGuardChatLogDao
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInDao

@Database(entities = [AmenityBooking::class, VisitorCheckIn::class, AiGuardChatLog::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun amenityBookingDao(): AmenityBookingDao
    abstract fun visitorCheckInDao(): VisitorCheckInDao
    abstract fun aiGuardChatLogDao(): AiGuardChatLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medusa_security_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
