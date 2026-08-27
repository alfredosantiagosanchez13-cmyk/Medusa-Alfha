package com.example.data.passes

import com.example.data.core.AlphaCoreEngine
import com.example.scanner.PassStatus
import com.example.scanner.PassType
import com.example.scanner.QrPassEntity
import com.example.scanner.VerificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repositorio de Pases respaldado 100% en Room SQLite.
 * Principio: "Capturar una vez, utilizar muchas veces."
 */
class QrPassRepository(private val qrPassDao: QrPassDao) {

    val allPassesFlow: Flow<List<QrPassRoomEntity>> = qrPassDao.getAllPassesFlow()

    suspend fun getAllPasses(): List<QrPassRoomEntity> = withContext(Dispatchers.IO) {
        qrPassDao.getAllPassesList()
    }

    suspend fun insertPass(pass: QrPassRoomEntity) = withContext(Dispatchers.IO) {
        qrPassDao.insertPass(pass)
    }

    suspend fun verifyPassCode(code: String): VerificationResult = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()
        val roomEntity = qrPassDao.getPassByCode(cleanCode)

        if (roomEntity == null) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.INVALID,
                failureReason = "Código QR no registrado en la base de datos de MEDUSA ALFHA."
            )
        }

        // Integrity validation
        val expectedHash = AlphaCoreEngine.computeIntegrityHash(
            roomEntity.passCode,
            roomEntity.guestDocument,
            roomEntity.destinationHouse
        )
        if (roomEntity.integrityHash.isNotEmpty() && roomEntity.integrityHash != expectedHash) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.INVALID,
                failureReason = "Violación de integridad: El pase QR ha sido alterado o manipulado."
            )
        }

        val qrPass = roomEntity.toQrPassEntity()

        if (System.currentTimeMillis() > roomEntity.validUntilMillis) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.EXPIRED,
                qrPass = qrPass,
                failureReason = "El pase expiró el ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(roomEntity.validUntilMillis))}."
            )
        }

        if (roomEntity.currentEntriesCount >= roomEntity.maxEntries) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.ALREADY_USED,
                qrPass = qrPass,
                failureReason = "Este pase único ya alcanzó su límite (${roomEntity.currentEntriesCount}/${roomEntity.maxEntries} usos)."
            )
        }

        VerificationResult(
            passCode = cleanCode,
            status = PassStatus.VALID,
            qrPass = qrPass
        )
    }

    suspend fun markPassAsUsed(passCode: String) = withContext(Dispatchers.IO) {
        qrPassDao.incrementUsage(passCode)
    }

    suspend fun seedInitialPassesIfEmpty() = withContext(Dispatchers.IO) {
        if (qrPassDao.getPassCount() == 0) {
            val initial = listOf(
                QrPassRoomEntity(
                    passCode = "MED-20260821-0101",
                    guestName = "Valeria Sofia Mendoza",
                    guestDocument = "18.492.301-2",
                    destinationHouse = "Casa #104",
                    hostResidentName = "Carlos Mendoza",
                    vehiclePlate = "KXYZ-98",
                    passType = PassType.VISITOR_SINGLE,
                    validUntilMillis = System.currentTimeMillis() + (8 * 3600 * 1000),
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Cena familiar / Ingreso por Portón Principal"
                ),
                QrPassRoomEntity(
                    passCode = "MED-20260821-0102",
                    guestName = "Marcos Esteban Rios (Uber Eats)",
                    guestDocument = "16.123.890-K",
                    destinationHouse = "Casa #208",
                    hostResidentName = "Ana Maria Gomez",
                    vehiclePlate = "DLPR-44",
                    passType = PassType.DELIVERY_SERVICE,
                    validUntilMillis = System.currentTimeMillis() + (2 * 3600 * 1000),
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Entrega de comida a domicilio"
                ),
                QrPassRoomEntity(
                    passCode = "MED-20260821-0103",
                    guestName = "Camila Andrea Silva",
                    guestDocument = "19.876.543-1",
                    destinationHouse = "Casa #302",
                    hostResidentName = "Felipe Silva",
                    vehiclePlate = null,
                    passType = PassType.EVENT_GUEST,
                    validUntilMillis = System.currentTimeMillis() - (3600 * 1000), // Expirado
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Invitada Cumpleaños VIP en Club House"
                ),
                QrPassRoomEntity(
                    passCode = "MED-20260821-0104",
                    guestName = "Gonzalo Inostroza",
                    guestDocument = "15.990.112-9",
                    destinationHouse = "Casa #115",
                    hostResidentName = "Patricia Soto",
                    vehiclePlate = "BCDF-12",
                    passType = PassType.VISITOR_SINGLE,
                    validUntilMillis = System.currentTimeMillis() + (12 * 3600 * 1000),
                    maxEntries = 1,
                    currentEntriesCount = 1, // Ya usado
                    note = "Reparación técnica de Fibra Óptica"
                ),
                QrPassRoomEntity(
                    passCode = "MED-20260821-0105",
                    guestName = "Dra. Romina Alarcón",
                    guestDocument = "14.331.002-3",
                    destinationHouse = "Casa #101",
                    hostResidentName = "Directiva Condominio",
                    vehiclePlate = "PORS-99",
                    passType = PassType.RESIDENT_PERMANENT,
                    validUntilMillis = System.currentTimeMillis() + (30L * 86400 * 1000),
                    maxEntries = 999,
                    currentEntriesCount = 4,
                    note = "Pase Frecuente Médico Residentes"
                )
            )
            qrPassDao.insertPasses(initial)
        }
    }
}

fun QrPassRoomEntity.toQrPassEntity(): QrPassEntity {
    return QrPassEntity(
        passCode = passCode,
        guestName = guestName,
        guestDocument = guestDocument,
        destinationHouse = destinationHouse,
        hostResidentName = hostResidentName,
        vehiclePlate = vehiclePlate,
        passType = passType,
        validUntilMillis = validUntilMillis,
        maxEntries = maxEntries,
        currentEntriesCount = currentEntriesCount,
        note = note
    )
}
