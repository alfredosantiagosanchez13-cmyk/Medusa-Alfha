package com.example.data.booking

import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de abstracción para la gestión de reservas de áreas comunes (Common Area Bookings).
 * Sigue el patrón Repository conforme a las directivas de arquitectura de Room.
 */
class CommonAreaBookingRepository(private val dao: CommonAreaBookingDao) {

    val allBookings: Flow<List<CommonAreaBooking>> = dao.getAllBookings()

    fun getBookingsForUser(userId: String): Flow<List<CommonAreaBooking>> {
        return dao.getBookingsByUserId(userId)
    }

    fun getBookingsForFacility(facilityName: String): Flow<List<CommonAreaBooking>> {
        return dao.getBookingsByFacility(facilityName)
    }

    fun getBookingsByDate(bookingDate: String): Flow<List<CommonAreaBooking>> {
        return dao.getBookingsByDate(bookingDate)
    }

    fun getBookingsByFacilityAndDate(facilityName: String, bookingDate: String): Flow<List<CommonAreaBooking>> {
        return dao.getBookingsByFacilityAndDate(facilityName, bookingDate)
    }

    fun getUpcomingBookingsForUser(userId: String, currentTimeMillis: Long = System.currentTimeMillis()): Flow<List<CommonAreaBooking>> {
        return dao.getUpcomingBookingsForUser(userId, currentTimeMillis)
    }

    suspend fun checkConflict(facilityName: String, startTimeMillis: Long, endTimeMillis: Long): Boolean {
        return dao.findConflictingBookings(facilityName, startTimeMillis, endTimeMillis).isNotEmpty()
    }

    suspend fun insertBooking(booking: CommonAreaBooking): Long {
        return dao.insertBooking(booking)
    }

    suspend fun updateBooking(booking: CommonAreaBooking): Int {
        return dao.updateBooking(booking)
    }

    suspend fun cancelBooking(id: Long): Int {
        return dao.cancelBooking(id)
    }

    suspend fun deleteBookingById(id: Long): Int {
        return dao.deleteBookingById(id)
    }

    suspend fun getBookingById(id: Long): CommonAreaBooking? {
        return dao.getBookingById(id)
    }

    suspend fun getBookingByFolio(folio: String): CommonAreaBooking? {
        return dao.getBookingByFolio(folio)
    }

    suspend fun getActiveCount(): Int {
        return dao.getActiveBookingsCount()
    }
}
