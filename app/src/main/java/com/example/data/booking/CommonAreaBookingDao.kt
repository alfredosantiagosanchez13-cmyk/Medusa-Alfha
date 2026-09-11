package com.example.data.booking

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) de Room para gestionar reservas de áreas comunes (Common Area Bookings).
 * Proporciona consultas reactivas basadas en Flow, detección de conflictos de horarios
 * y operaciones de persistencia asíncronas seguras.
 */
@Dao
interface CommonAreaBookingDao {

    /**
     * Obtiene el flujo reactivo de todas las reservas ordenadas cronológicamente.
     */
    @Query("SELECT * FROM common_area_bookings ORDER BY startTimeMillis DESC")
    fun getAllBookings(): Flow<List<CommonAreaBooking>>

    /**
     * Obtiene las reservas pertenecientes a un usuario específico (userId).
     */
    @Query("SELECT * FROM common_area_bookings WHERE userId = :userId ORDER BY startTimeMillis DESC")
    fun getBookingsByUserId(userId: String): Flow<List<CommonAreaBooking>>

    /**
     * Obtiene las reservas asociadas a una instalación o área común (facilityName).
     */
    @Query("SELECT * FROM common_area_bookings WHERE facilityName = :facilityName ORDER BY startTimeMillis DESC")
    fun getBookingsByFacility(facilityName: String): Flow<List<CommonAreaBooking>>

    /**
     * Obtiene las reservas activas para una fecha específica ("yyyy-MM-dd").
     */
    @Query("SELECT * FROM common_area_bookings WHERE bookingDate = :bookingDate AND status != 'CANCELLED' ORDER BY startTimeMillis ASC")
    fun getBookingsByDate(bookingDate: String): Flow<List<CommonAreaBooking>>

    /**
     * Obtiene las reservas de una instalación en una fecha determinada.
     */
    @Query("SELECT * FROM common_area_bookings WHERE facilityName = :facilityName AND bookingDate = :bookingDate AND status != 'CANCELLED' ORDER BY startTimeMillis ASC")
    fun getBookingsByFacilityAndDate(facilityName: String, bookingDate: String): Flow<List<CommonAreaBooking>>

    /**
     * Obtiene las próximas reservas futuras de un usuario.
     */
    @Query("SELECT * FROM common_area_bookings WHERE userId = :userId AND endTimeMillis >= :currentTimeMillis AND status != 'CANCELLED' ORDER BY startTimeMillis ASC")
    fun getUpcomingBookingsForUser(userId: String, currentTimeMillis: Long): Flow<List<CommonAreaBooking>>

    /**
     * Obtiene reservas con aislamiento por condominio.
     */
    @Query("SELECT * FROM common_area_bookings WHERE condominiumId = :condominiumId AND status != 'CANCELLED' ORDER BY startTimeMillis ASC")
    fun getActiveBookingsByCondo(condominiumId: String): Flow<List<CommonAreaBooking>>

    /**
     * Busca colisiones o conflictos de horario para una instalación entre startTimeMillis y endTimeMillis.
     * Una colisión ocurre si una reserva existente activa se solapa con el intervalo propuesto.
     */
    @Query("""
        SELECT * FROM common_area_bookings 
        WHERE facilityName = :facilityName 
          AND status != 'CANCELLED' 
          AND (startTimeMillis < :newEndTimeMillis AND endTimeMillis > :newStartTimeMillis)
    """)
    suspend fun findConflictingBookings(
        facilityName: String,
        newStartTimeMillis: Long,
        newEndTimeMillis: Long
    ): List<CommonAreaBooking>

    /**
     * Busca colisiones respetando el inquilino/condominio específico.
     */
    @Query("""
        SELECT * FROM common_area_bookings 
        WHERE condominiumId = :condominiumId 
          AND facilityName = :facilityName 
          AND status != 'CANCELLED' 
          AND (startTimeMillis < :newEndTimeMillis AND endTimeMillis > :newStartTimeMillis)
    """)
    suspend fun findConflictingBookingsWithTenant(
        condominiumId: String,
        facilityName: String,
        newStartTimeMillis: Long,
        newEndTimeMillis: Long
    ): List<CommonAreaBooking>

    /**
     * Busca una reserva por su ID autogenerado.
     */
    @Query("SELECT * FROM common_area_bookings WHERE id = :id LIMIT 1")
    suspend fun getBookingById(id: Long): CommonAreaBooking?

    /**
     * Busca una reserva por su folio único.
     */
    @Query("SELECT * FROM common_area_bookings WHERE folio = :folio LIMIT 1")
    suspend fun getBookingByFolio(folio: String): CommonAreaBooking?

    /**
     * Inserta una nueva reserva de área común.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: CommonAreaBooking): Long

    /**
     * Inserta múltiples reservas en una única transacción.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookings(bookings: List<CommonAreaBooking>): List<Long>

    /**
     * Actualiza los datos de una reserva existente.
     */
    @Update
    suspend fun updateBooking(booking: CommonAreaBooking): Int

    /**
     * Cambia el estado de una reserva (e.g. CONFIRMED, IN_USE, COMPLETED, CANCELLED).
     */
    @Query("UPDATE common_area_bookings SET status = :newStatus, updatedAtMillis = :timestamp WHERE id = :id")
    suspend fun updateBookingStatus(
        id: Long,
        newStatus: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /**
     * Cancela una reserva registrando la marca de tiempo de cancelación.
     */
    @Query("UPDATE common_area_bookings SET status = 'CANCELLED', updatedAtMillis = :cancelledAtMillis WHERE id = :id")
    suspend fun cancelBooking(
        id: Long,
        cancelledAtMillis: Long = System.currentTimeMillis()
    ): Int

    /**
     * Elimina un registro de reserva.
     */
    @Delete
    suspend fun deleteBooking(booking: CommonAreaBooking): Int

    /**
     * Elimina una reserva por su ID.
     */
    @Query("DELETE FROM common_area_bookings WHERE id = :id")
    suspend fun deleteBookingById(id: Long): Int

    /**
     * Conteo total de reservas registradas.
     */
    @Query("SELECT COUNT(*) FROM common_area_bookings")
    suspend fun getBookingsCount(): Int

    /**
     * Conteo de reservas activas (no canceladas).
     */
    @Query("SELECT COUNT(*) FROM common_area_bookings WHERE status != 'CANCELLED'")
    suspend fun getActiveBookingsCount(): Int
}
