package com.example.data.visitor

import kotlinx.coroutines.flow.Flow

class VisitorCheckInRepository(private val visitorCheckInDao: VisitorCheckInDao) {

    val allCheckIns: Flow<List<VisitorCheckIn>> = visitorCheckInDao.getAllCheckIns()

    suspend fun insertCheckIn(checkIn: VisitorCheckIn): Long {
        return visitorCheckInDao.insertCheckIn(checkIn)
    }

    suspend fun updateCheckInStatus(id: Long, status: String, notes: String?) {
        visitorCheckInDao.updateCheckInStatus(id, status, notes)
    }

    suspend fun updateResidentNotes(id: Long, notes: String?) {
        visitorCheckInDao.updateResidentNotes(id, notes)
    }

    suspend fun deleteCheckInById(id: Long) {
        visitorCheckInDao.deleteCheckInById(id)
    }

    suspend fun deleteAllCheckIns() {
        visitorCheckInDao.deleteAllCheckIns()
    }

    suspend fun getCheckInCount(): Int {
        return visitorCheckInDao.getCheckInCount()
    }
}
