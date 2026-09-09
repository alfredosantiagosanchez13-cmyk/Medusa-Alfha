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

    suspend fun registerCheckInEntry(id: Long, notes: String? = "Ingreso verificado en caseta") {
        visitorCheckInDao.updateCheckInStatus(id, "CHECKED_IN", notes)
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

    /**
     * Registra la salida de un visitante por su Folio irrepetible, actualizando
     * el timestamp de egreso (checkOutTimestamp / checkOutMillis) tanto en Room DB como en Firestore.
     */
    suspend fun registerCheckOutByFolio(
        folio: String,
        notes: String? = "Salida confirmada por administración"
    ): Result<VisitorCheckIn> = withContext(Dispatchers.IO) {
        try {
            val checkIn = visitorCheckInDao.getCheckInByFolio(folio)
                ?: return@withContext Result.failure(Exception("Registro no encontrado para folio $folio"))

            val now = System.currentTimeMillis()
            val updatedNotes = if (notes.isNullOrBlank()) checkIn.guardNotes else {
                "${checkIn.guardNotes ?: ""}\n[Salida]: $notes".trim()
            }
            val updated = checkIn.copy(
                status = "DEPARTED",
                checkOutMillis = now,
                guardNotes = updatedNotes
            )

            visitorCheckInDao.insertCheckIn(updated)

            // Sincronizar en Firestore con timestamp nativo de salida
            val fs = FirebaseConfigHelper.getFirestore()
            if (fs != null) {
                try {
                    FirestoreTenantManager.recordVisitorExitInFirestore(
                        firestore = fs,
                        condominiumId = activeCondominiumId,
                        folio = folio,
                        checkOutTimestamp = com.google.firebase.Timestamp(java.util.Date(now)),
                        guardNotes = updatedNotes
                    )
                    FirestoreTenantManager.saveVisitorCheckIn(fs, activeCondominiumId, updated)
                } catch (e: Exception) {
                    Log.w("VisitorCheckInRepo", "Fallo al sincronizar salida en Firestore: ${e.message}")
                }
            }

            Result.success(updated)
        } catch (e: Exception) {
            Log.e("VisitorCheckInRepo", "Error al registrar salida por folio $folio: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra un nuevo invitado por parte del residente o guardia y lo persiste
     * directamente en Firestore (/condominiums/{condoId}/visitor_logs) y en Room DB.
     */
    suspend fun registerGuestAndLogToFirestore(
        condominiumId: String = activeCondominiumId,
        visitorName: String,
        authorizedUnitNumber: String,
        hostResidentName: String,
        visitorDocument: String = "Sin Documento",
        passTypeLabel: String = "Visita Familiar",
        vehiclePlate: String? = null,
        residentNotes: String? = null,
        isImmediateCheckIn: Boolean = false,
        guardNotes: String? = null
    ): Result<VisitorCheckIn> = withContext(Dispatchers.IO) {
        try {
            val folio = AlphaCoreEngine.generateUniqueFolio("MED")
            val passCode = "PASS-${folio.takeLast(6)}"
            val status = if (isImmediateCheckIn) "CHECKED_IN" else "PRE_REGISTRADO"
            val now = System.currentTimeMillis()

            val checkIn = VisitorCheckIn(
                id = 0,
                folio = folio,
                visitorName = visitorName.trim(),
                visitorDocument = visitorDocument.trim(),
                destinationHouse = authorizedUnitNumber.trim(),
                passCode = passCode,
                passTypeLabel = passTypeLabel,
                vehiclePlate = vehiclePlate?.trim()?.ifBlank { null },
                status = status,
                timestampMillis = now,
                guardNotes = guardNotes ?: if (isImmediateCheckIn) "Ingreso inmediato registrado" else "Pre-registro de residente",
                residentNotes = residentNotes?.trim()?.ifBlank { null },
                hostResidentName = hostResidentName.trim()
            )

            val insertedId = visitorCheckInDao.insertCheckIn(checkIn)
            val finalEntity = checkIn.copy(id = insertedId)

            // Sincronizar en Firestore
            val fs = FirebaseConfigHelper.getFirestore()
            if (fs != null) {
                try {
                    val firestoreLog = FirestoreVisitorLog.fromVisitorCheckIn(finalEntity, condominiumId)
                    FirestoreTenantManager.saveVisitorLog(fs, condominiumId, firestoreLog)
                    FirestoreTenantManager.saveVisitorCheckIn(fs, condominiumId, finalEntity)
                } catch (e: Exception) {
                    Log.w("VisitorCheckInRepo", "Fallo al guardar log en Firestore: ${e.message}")
                }
            }

            Result.success(finalEntity)
        } catch (e: Exception) {
            Log.e("VisitorCheckInRepo", "Error al registrar invitado: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sincroniza y descarga los logs de visitantes desde Firestore hacia Room DB.
     */
    suspend fun syncFromFirestore(condominiumId: String = activeCondominiumId): Result<Int> = withContext(Dispatchers.IO) {
        val fs = FirebaseConfigHelper.getFirestore()
            ?: return@withContext Result.failure(Exception("Firestore no disponible en este dispositivo"))

        try {
            val queryResult = FirestoreTenantManager.queryVisitorLogs(fs, condominiumId, limitCount = 100)
            if (queryResult.isSuccess) {
                val remoteLogs = queryResult.getOrNull() ?: emptyList()
                var newOrUpdatedCount = 0

                for (log in remoteLogs) {
                    val converted = log.toVisitorCheckIn()
                    val existing = visitorCheckInDao.getCheckInByFolio(converted.folio)
                    if (existing == null) {
                        visitorCheckInDao.insertCheckIn(converted)
                        newOrUpdatedCount++
                    } else if (existing.status != converted.status || existing.checkOutMillis != converted.checkOutMillis) {
                        // Actualizar estado si cambió en la nube
                        visitorCheckInDao.insertCheckIn(converted.copy(id = existing.id))
                        newOrUpdatedCount++
                    }
                }
                Result.success(newOrUpdatedCount)
            } else {
                Result.failure(queryResult.exceptionOrNull() ?: Exception("Error al consultar logs en Firestore"))
            }
        } catch (e: Exception) {
            Log.e("VisitorCheckInRepo", "Error sincronizando desde Firestore: ${e.message}", e)
            Result.failure(e)
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

