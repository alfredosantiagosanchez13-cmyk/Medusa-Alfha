package com.example.data.booking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AmenityBookingDao {
    @Query("SELECT * FROM amenity_bookings ORDER BY bookingTimeMillis DESC")
    fun getAllBookings(): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE condominiumId = :condominiumId OR condominiumId = 'GENERAL' ORDER BY bookingTimeMillis DESC")
    fun getBookingsByCondominium(condominiumId: String): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL') AND bookingDate = :bookingDate AND status != 'CANCELADA' ORDER BY bookingTimeMillis ASC")
    fun getBookingsByDate(condominiumId: String, bookingDate: String): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL') AND bookingDate LIKE :yearMonthPrefix || '%' AND status != 'CANCELADA' ORDER BY bookingTimeMillis ASC")
    fun getBookingsByMonth(condominiumId: String, yearMonthPrefix: String): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE bookingTimeMillis > :currentTime AND status != 'CANCELADA' ORDER BY bookingTimeMillis ASC")
    fun getUpcomingBookings(currentTime: Long): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE unitId = :unitId ORDER BY bookingTimeMillis DESC")
    fun getBookingsByUnit(unitId: String): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE amenityName = :amenityName AND status != 'CANCELADA'")
    fun getActiveBookingsForAmenity(amenityName: String): Flow<List<AmenityBooking>>

    @Query("SELECT * FROM amenity_bookings WHERE reminderSent = 0 AND bookingTimeMillis > :currentTime AND status != 'CANCELADA'")
    suspend fun getPendingReminders(currentTime: Long): List<AmenityBooking>

    @Query("SELECT * FROM amenity_bookings WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL') AND amenityName = :amenityName AND status != 'CANCELADA' AND ((bookingTimeMillis < :newEndTime AND (bookingTimeMillis + (durationMinutes * 60000)) > :newStartTime))")
    suspend fun findConflictingBookingsWithTenant(
        condominiumId: String,
        amenityName: String,
        newStartTime: Long,
        newEndTime: Long
    ): List<AmenityBooking>

    @Query("SELECT * FROM amenity_bookings WHERE amenityName = :amenityName AND status != 'CANCELADA' AND ((bookingTimeMillis < :newEndTime AND (bookingTimeMillis + (durationMinutes * 60000)) > :newStartTime))")
    suspend fun findConflictingBookings(
        amenityName: String,
        newStartTime: Long,
        newEndTime: Long
    ): List<AmenityBooking>

    @Query("SELECT * FROM amenity_bookings WHERE folio = :folio LIMIT 1")
    suspend fun getBookingByFolio(folio: String): AmenityBooking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: AmenityBooking): Long

    @Update
    suspend fun updateBooking(booking: AmenityBooking)

    @Query("DELETE FROM amenity_bookings WHERE id = :id")
    suspend fun deleteBooking(id: Long)

    @Query("UPDATE amenity_bookings SET reminderSent = 1 WHERE id = :id")
    suspend fun markReminderSent(id: Long)

    @Query("UPDATE amenity_bookings SET status = :status WHERE id = :id")
    suspend fun updateBookingStatus(id: Long, status: String)

    @Query("UPDATE amenity_bookings SET status = 'CANCELADA', cancelledBy = :cancelledBy, cancellationReason = :reason, cancelledAtMillis = :nowMillis WHERE id = :id")
    suspend fun cancelBooking(id: Long, cancelledBy: String, reason: String, nowMillis: Long)

    @Query("SELECT COUNT(*) FROM amenity_bookings")
    suspend fun getBookingsCount(): Int

    @Query("SELECT COUNT(*) FROM amenity_bookings WHERE status != 'CANCELADA'")
    suspend fun getActiveBookingsCount(): Int

    @Query("SELECT * FROM amenity_bookings")
    suspend fun getAllBookingsList(): List<AmenityBooking>
}

