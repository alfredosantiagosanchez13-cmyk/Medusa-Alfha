package com.example.data.visitor

import android.util.Log
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VisitorCheckInRepository(
    private val visitorCheckInDao: VisitorCheckInDao,
    var activeCondominiumId: String = "PRADOS_1"
) {

    val allCheckIns: Flow<List<VisitorCheckIn>> = visitorCheckInDao.getAllCheckIns()

    suspend fun insertCheckIn(checkIn: VisitorCheckIn): Long {
        val id = visitorCheckInDao.insertCheckIn(checkIn)
        val inserted = checkIn.copy(id = id)
        // Sincronizar automáticamente en Firestore /condominiums/{condoId}/visitor_logs
        withContext(Dispatchers.IO) {
            val fs = FirebaseConfigHelper.getFirestore()
            if (fs != null) {
                try {
                    FirestoreTenantManager.saveVisitorCheckIn(fs, activeCondominiumId, inserted)
                } catch (e: Exception) {
                    Log.w("VisitorCheckInRepo", "Firestore sync diferido: ${e.message}")
                }
            }
        }
        return id
    }

    suspend fun updateCheckInStatus(id: Long, status: String, notes: String? = null) {
        visitorCheckInDao.updateCheckInStatus(id, status, notes)
        withContext(Dispatchers.IO) {
            val fs = FirebaseConfigHelper.getFirestore()
            if (fs != null) {
                try {
                    val all = visitorCheckInDao.getAllCheckInsList()
                    val target = all.firstOrNull { it.id == id }
                    if (target != null) {
                        FirestoreTenantManager.saveVisitorCheckIn(fs, activeCondominiumId, target)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun registerCheckOut(id: Long, notes: String? = "Salida confirmada en garita") {
        visitorCheckInDao.registerCheckOut(id, notes = notes)
        withContext(Dispatchers.IO) {
            val fs = FirebaseConfigHelper.getFirestore()
            if (fs != null) {
                try {
                    val all = visitorCheckInDao.getAllCheckInsList()
                    val target = all.firstOrNull { it.id == id }
                    if (target != null) {
                        FirestoreTenantManager.saveVisitorCheckIn(fs, activeCondominiumId, target)
                    }
                } catch (_: Exception) {}
            }
        }
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

    suspend fun getActiveVisitorsInsideCount(): Int {
        return visitorCheckInDao.getActiveVisitorsInsideCount()
    }

    suspend fun seedInitialCheckInsIfEmpty() = withContext(Dispatchers.IO) {
        if (visitorCheckInDao.getCheckInCount() == 0) {
            val initial = listOf(
                VisitorCheckIn(
                    folio = AlphaCoreEngine.generateUniqueFolio("MED"),
                    visitorName = "Valeria Sofía Mendoza",
                    visitorDocument = "18.492.301-2",
                    destinationHouse = "Casa #104",
                    passCode = "MEDUSA-PASS-101",
                    passTypeLabel = "Visita Ocasional",
                    vehiclePlate = "KXYZ-98",
                    timestampMillis = System.currentTimeMillis() - (55 * 60 * 1000),
                    checkOutMillis = System.currentTimeMillis() - (5 * 60 * 1000),
                    status = "DEPARTED",
                    guardNotes = "Ingreso autorizado y salida registrada en Garita Principal",
                    hostResidentName = "Carlos Mendoza"
                ),
                VisitorCheckIn(
                    folio = AlphaCoreEngine.generateUniqueFolio("MED"),
                    visitorName = "Marcos Esteban Ríos",
                    visitorDocument = "16.123.890-K",
                    destinationHouse = "Casa #208",
                    passCode = "MEDUSA-PASS-102",
                    passTypeLabel = "Delivery / Uber Eats",
                    vehiclePlate = "DLPR-44",
                    timestampMillis = System.currentTimeMillis() - (12 * 60 * 1000),
                    status = "CHECKED_IN",
                    guardNotes = "En condominio entregando pedido",
                    hostResidentName = "Ana María Gómez"
                ),
                VisitorCheckIn(
                    folio = AlphaCoreEngine.generateUniqueFolio("MED"),
                    visitorName = "Gonzalo Inostroza",
                    visitorDocument = "15.990.112-9",
                    destinationHouse = "Casa #115",
                    passCode = "MEDUSA-PASS-104",
                    passTypeLabel = "Técnico Fibra Óptica",
                    vehiclePlate = "BCDF-12",
                    timestampMillis = System.currentTimeMillis() - (45 * 60 * 1000),
                    status = "CHECKED_IN",
                    guardNotes = "Técnico en mantenimiento programado",
                    hostResidentName = "Patricia Soto"
                ),
                VisitorCheckIn(
                    folio = AlphaCoreEngine.generateUniqueFolio("MED"),
                    visitorName = "Rodrigo San Martín",
                    visitorDocument = "17.430.881-5",
                    destinationHouse = "Casa #501",
                    passCode = "MEDUSA-PASS-202",
                    passTypeLabel = "Servicio Técnico",
                    vehiclePlate = "HPWL-88",
                    timestampMillis = System.currentTimeMillis() - (2 * 60 * 1000),
                    status = "VERIFICADO",
                    guardNotes = "Verificación en proceso",
                    hostResidentName = "Familia San Martín"
                )
            )
            initial.forEach { visitorCheckInDao.insertCheckIn(it) }
        }
    }
}

