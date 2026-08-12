package com.example.data.booking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AmenityBookingDao {
    @Query("SELECT * FROM amenity_bookings ORDER BY bookingTimeMillis ASC")
    fun getAllBookings(): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE bookingTimeMillis > :currentTime ORDER BY bookingTimeMillis ASC")
    fun getUpcomingBookings(currentTime: Long): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE reminderSent = 0 AND bookingTimeMillis > :currentTime")
    suspend fun getPendingReminders(currentTime: Long): List<AmenityBooking>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: AmenityBooking): Long

    @Update
    suspend fun updateBooking(booking: AmenityBooking)

    @Query("DELETE FROM amenity_bookings WHERE id = :id")
    suspend fun deleteBooking(id: Long)

    @Query("UPDATE amenity_bookings SET reminderSent = 1 WHERE id = :id")
    suspend fun markReminderSent(id: Long)
}
