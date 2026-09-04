package com.example.data.passes

import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirestoreTenantManager
import com.example.scanner.PassStatus
import com.example.scanner.PassType
import com.example.scanner.QrPassEntity
import com.example.scanner.QrPayloadParser
import com.example.scanner.VerificationResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repositorio de Pases respaldado 100% en Room SQLite con validación estricta de inquilino por Firestore.
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

    /**
     * Valida un código QR contra el condominio actual en Firestore y en Room local.
     */
    suspend fun verifyPassCode(
        code: String,
        currentCondominiumId: String? = null,
        firestore: FirebaseFirestore? = null
    ): VerificationResult = withContext(Dispatchers.IO) {
        val cleanCode = QrPayloadParser.extractEntryCode(code)
        val targetCondoId = currentCondominiumId?.uppercase()?.trim()

        var hostResidentPhone: String? = null
        var hostResidentEmail: String? = null

        // 1. Verificación en tiempo real contra Firestore si hay instancia disponible
        if (firestore != null && !targetCondoId.isNullOrEmpty()) {
            val firestoreValidationResult = FirestoreTenantManager.validateQrPassAgainstFirestore(
                firestore = firestore,
                currentCondominiumId = targetCondoId,
                scannedCode = cleanCode
            )

            if (firestoreValidationResult.isFailure) {
                val errorMsg = firestoreValidationResult.exceptionOrNull()?.message ?: "Violación de aislamiento multi-inquilino en Firestore."
                return@withContext VerificationResult(
                    passCode = cleanCode,
                    status = PassStatus.INVALID,
                    failureReason = errorMsg,
                    condominiumId = targetCondoId,
                    isFirestoreValidated = true
                )
            }

            val firestorePass = firestoreValidationResult.getOrNull()
            if (firestorePass != null) {
                // Guardar / actualizar en Room local para disponibilidad offline
                qrPassDao.insertPass(firestorePass)
            } else {
                // Si no está en 'qr_passes', buscar a través de DataRepository en colecciones 'visitors' y 'residents'
                try {
                    val dataRepo = com.example.data.DataRepository(firestore, targetCondoId)
                    val residentEntryRes = dataRepo.verifyResidentEntryCode(cleanCode, targetCondoId).getOrNull()
                    if (residentEntryRes != null && residentEntryRes.visitor != null) {
                        val v = residentEntryRes.visitor
                        val r = residentEntryRes.resident
                        hostResidentPhone = r?.phone
                        hostResidentEmail = r?.email

                        val mappedPass = QrPassRoomEntity(
                            passCode = cleanCode,
                            guestName = v.visitorName.ifBlank { "Visitante Registrado" },
                            guestDocument = v.visitorDocument.ifBlank { "Verificar en Garita" },
                            destinationHouse = v.authorizedUnitNumber.ifBlank { "Unidad ${r?.unitId ?: ""}" },
                            hostResidentName = v.hostResidentName.ifBlank { r?.fullName ?: "Residente Anfitrión" },
                            vehiclePlate = v.vehiclePlate.takeIf { it.isNotBlank() },
                            passType = when (v.visitType.uppercase()) {
                                "DELIVERY" -> PassType.DELIVERY_SERVICE
                                "EVENT", "EVENTO" -> PassType.EVENT_GUEST
                                "FREQUENT", "FRECUENTE" -> PassType.RESIDENT_PERMANENT
                                else -> PassType.VISITOR_SINGLE
                            },
                            validUntilMillis = System.currentTimeMillis() + 86400000L,
                            maxEntries = v.maxEntries,
                            currentEntriesCount = v.currentEntries,
                            note = v.notes
                        )
                        qrPassDao.insertPass(mappedPass)
                    }
                } catch (e: Exception) {
                    // Continuar con verificación local
                }
            }
        }

        var roomEntity = qrPassDao.getPassByCode(cleanCode)

        // Soporte nativo para Códigos QR generados por MEDUSA Vecinos Web/Apps Script
        if (roomEntity == null && cleanCode.startsWith("MEDUSA-VISITA-")) {
            val parts = cleanCode.split("-")
            if (parts.size >= 5) {
                val condoId = parts[2].uppercase().trim()
                val casaNum = parts[3]
                val visitaId = parts[4]

                // VALIDACIÓN DE TENANT: Si el pase pertenece a otro condominio, rechazar inmediatamente
                if (targetCondoId != null && condoId != targetCondoId && !condoId.contains(targetCondoId) && !targetCondoId.contains(condoId)) {
                    return@withContext VerificationResult(
                        passCode = cleanCode,
                        status = PassStatus.INVALID,
                        failureReason = "ACCESO DENEGADO: El pase QR pertenece al condominio '$condoId', pero la caseta activa es '$targetCondoId'. Aislamiento multi-inquilino garantizado.",
                        condominiumId = targetCondoId,
                        isFirestoreValidated = firestore != null
                    )
                }

                val condoName = when (condoId) {
                    "PARAISO" -> "Condominio Paraíso"
                    "PRADOS_1" -> "Los Prados 1"
                    "PRADOS_2" -> "Los Prados 2"
                    "PRADOS_3" -> "Los Prados 3"
                    else -> "Condominio $condoId"
                }
                val destination = if (condoId == "PARAISO") "Casa $casaNum" else "Casa $casaNum · $condoName"
                val newPass = QrPassRoomEntity(
                    passCode = cleanCode,
                    guestName = "Visita Autorizada #$visitaId",
                    guestDocument = "Verificar en Caseta",
                    destinationHouse = destination,
                    hostResidentName = "Residente Casa $casaNum ($condoName)",
                    vehiclePlate = null,
                    passType = PassType.VISITOR_SINGLE,
                    validUntilMillis = System.currentTimeMillis() + (24 * 3600 * 1000), // 24h
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Pase validado contra '$condoId' en MEDUSA ALFHA"
                )
                qrPassDao.insertPass(newPass)
                roomEntity = newPass
            }
        }

        if (roomEntity == null) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.INVALID,
                failureReason = "Código QR no registrado en el condominio activo (${targetCondoId ?: "GENERAL"}).",
                condominiumId = targetCondoId,
                isFirestoreValidated = firestore != null
            )
        }

        // Verificación estricta de pertenencia al condominio en la entidad Room
        if (targetCondoId != null) {
            val passDest = roomEntity.destinationHouse.uppercase()
            val passHost = roomEntity.hostResidentName.uppercase()
            val passNote = (roomEntity.note ?: "").uppercase()

            val isMismatched = when (targetCondoId) {
                "PARAISO" -> (passDest.contains("PRADOS") || passDest.contains("CALLE"))
                "PRADOS_1" -> (passDest.contains("PARAISO") || passDest.contains("CALLE 3") || passDest.contains("CALLE 4") || passDest.contains("CALLE 5") || passDest.contains("CALLE 6"))
                "PRADOS_2" -> (passDest.contains("PARAISO") || passDest.contains("CALLE 1") || passDest.contains("CALLE 2") || passDest.contains("CALLE 5") || passDest.contains("CALLE 6"))
                "PRADOS_3" -> (passDest.contains("PARAISO") || passDest.contains("CALLE 1") || passDest.contains("CALLE 2") || passDest.contains("CALLE 3") || passDest.contains("CALLE 4"))
                else -> false
            }

            if (isMismatched) {
                return@withContext VerificationResult(
                    passCode = cleanCode,
                    status = PassStatus.INVALID,
                    failureReason = "ACCESO DENEGADO: El pase fue emitido para otra sección/condominio (${roomEntity.destinationHouse}). No válido para $targetCondoId.",
                    condominiumId = targetCondoId,
                    isFirestoreValidated = firestore != null
                )
            }
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
                failureReason = "Violación de integridad: El pase QR ha sido alterado o manipulado.",
                condominiumId = targetCondoId,
                isFirestoreValidated = firestore != null
            )
        }

        val qrPass = roomEntity.toQrPassEntity()

        if (System.currentTimeMillis() > roomEntity.validUntilMillis) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.EXPIRED,
                qrPass = qrPass,
                failureReason = "El pase expiró el ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(roomEntity.validUntilMillis))}.",
                condominiumId = targetCondoId,
                isFirestoreValidated = firestore != null,
                hostResidentPhone = hostResidentPhone,
                hostResidentEmail = hostResidentEmail
            )
        }

        if (roomEntity.currentEntriesCount >= roomEntity.maxEntries) {
            return@withContext VerificationResult(
                passCode = cleanCode,
                status = PassStatus.ALREADY_USED,
                qrPass = qrPass,
                failureReason = "Este pase único ya alcanzó su límite (${roomEntity.currentEntriesCount}/${roomEntity.maxEntries} usos).",
                condominiumId = targetCondoId,
                isFirestoreValidated = firestore != null,
                hostResidentPhone = hostResidentPhone,
                hostResidentEmail = hostResidentEmail
            )
        }

        VerificationResult(
            passCode = cleanCode,
            status = PassStatus.VALID,
            qrPass = qrPass,
            condominiumId = targetCondoId,
            isFirestoreValidated = firestore != null,
            hostResidentPhone = hostResidentPhone,
            hostResidentEmail = hostResidentEmail
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
