package com.example.data.visitor

import android.content.Context
import android.util.Log
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.utils.ResidentNotificationManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado del flujo de autorización bidireccional entre la Garita (Caseta) y el Residente.
 */
enum class LiveAccessStatus(val label: String) {
    PENDING("Esperando Respuesta del Residente"),
    AUTHORIZED("Acceso Autorizado por Residente"),
    DENIED("Acceso Denegado por Residente")
}

/**
 * Modelo de solicitud de acceso en vivo generada por el guardia en garita
 * con las dos fotografías tomadas (identificación y placas/vehículo).
 */
data class LiveAccessRequest(
    val id: String = AlphaCoreEngine.generateUniqueFolio("GAR"),
    val timestampMillis: Long = System.currentTimeMillis(),
    val visitorName: String,
    val accessType: String, // "VISITA", "PROVEEDOR", "PAQUETERÍA"
    val destinationHouse: String,
    val vehiclePlate: String? = null,
    val idPhotoPath: String? = null, // Foto 1: Identificación Oficial (INE/Licencia)
    val vehiclePhotoPath: String? = null, // Foto 2: Vehículo y Placas
    val guardName: String = "Guardia Garita Principal",
    val status: LiveAccessStatus = LiveAccessStatus.PENDING,
    val resolvedAtMillis: Long? = null,
    val resolutionNotes: String? = null,
    val hostResidentName: String = "Residente"
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

    val isPending: Boolean
        get() = status == LiveAccessStatus.PENDING

    val isAuthorized: Boolean
        get() = status == LiveAccessStatus.AUTHORIZED

    val isDenied: Boolean
        get() = status == LiveAccessStatus.DENIED
}

/**
 * Gestor en tiempo real del enlace Garita <-> Residente.
 * Permite al guardia notificar al residente con 2 fotografías,
 * y al residente autorizar o negar el acceso en 1 toque.
 */
object AccessAuthorizationManager {
    private const val TAG = "AccessAuthManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeRequests = MutableStateFlow<List<LiveAccessRequest>>(emptyList())
    val activeRequests: StateFlow<List<LiveAccessRequest>> = _activeRequests.asStateFlow()

    // Solicitud más reciente para feedback ágil en Caseta
    private val _lastSubmittedRequest = MutableStateFlow<LiveAccessRequest?>(null)
    val lastSubmittedRequest: StateFlow<LiveAccessRequest?> = _lastSubmittedRequest.asStateFlow()

    /**
     * Registra una nueva solicitud de acceso en Caseta y envía la Notificación Push al residente.
     */
    fun submitAccessRequest(
        context: Context,
        db: AppDatabase,
        condominiumId: String = "PRADOS_1",
        visitorName: String,
        accessType: String,
        destinationHouse: String,
        vehiclePlate: String?,
        idPhotoPath: String?,
        vehiclePhotoPath: String?,
        guardName: String = "Agente Garita Principal",
        hostResidentName: String = "Residente"
    ): LiveAccessRequest {
        val request = LiveAccessRequest(
            visitorName = visitorName.trim(),
            accessType = accessType.trim().uppercase(),
            destinationHouse = destinationHouse.trim(),
            vehiclePlate = vehiclePlate?.trim()?.takeIf { it.isNotBlank() },
            idPhotoPath = idPhotoPath,
            vehiclePhotoPath = vehiclePhotoPath,
            guardName = guardName,
            status = LiveAccessStatus.PENDING,
            hostResidentName = hostResidentName
        )

        val updated = listOf(request) + _activeRequests.value.filter { it.id != request.id }
        _activeRequests.value = updated
        _lastSubmittedRequest.value = request

        scope.launch {
            try {
                // 1. Guardar en SQLite Room local como PENDIENTE
                val combinedPhotos = listOfNotNull(idPhotoPath, vehiclePhotoPath).joinToString("|")
                val checkIn = VisitorCheckIn(
                    id = 0,
                    folio = request.id,
                    visitorName = request.visitorName,
                    visitorDocument = "INE/Identificación en foto adjunta",
                    destinationHouse = request.destinationHouse,
                    passCode = request.id,
                    passTypeLabel = "${request.accessType} (Garita Express)",
                    vehiclePlate = request.vehiclePlate,
                    status = "PENDIENTE_AUTORIZACION",
                    timestampMillis = request.timestampMillis,
                    guardNotes = "Esperando decisión del residente. Fotos tomadas en garita.",
                    guardName = request.guardName,
                    photoPath = combinedPhotos.ifBlank { null },
                    hostResidentName = request.hostResidentName
                )
                db.visitorCheckInDao().insertCheckIn(checkIn)

                // 2. Sincronizar en Firebase Firestore
                val firestore = FirebaseConfigHelper.getFirestore()
                if (firestore != null) {
                    val log = FirestoreVisitorLog.fromVisitorCheckIn(checkIn, condominiumId)
                    FirestoreTenantManager.saveVisitorLog(firestore, condominiumId, log)
                    FirestoreTenantManager.saveVisitorCheckIn(firestore, condominiumId, checkIn)
                }

                // 3. Disparar Notificación Push al Residente
                ResidentNotificationManager.notifyAccessAuthorizationRequest(
                    context = context,
                    requestId = request.id,
                    visitorName = request.visitorName,
                    accessType = request.accessType,
                    destinationHouse = request.destinationHouse,
                    vehiclePlate = request.vehiclePlate,
                    hasIdPhoto = !request.idPhotoPath.isNullOrBlank(),
                    hasVehiclePhoto = !request.vehiclePhotoPath.isNullOrBlank()
                )

                Log.i(TAG, "Solicitud de acceso enviada: ${request.id} para ${request.destinationHouse}")
            } catch (e: Exception) {
                Log.e(TAG, "Error persistiendo solicitud de acceso: ${e.message}", e)
            }
        }

        return request
    }

    /**
     * Resuelve la solicitud de acceso: DAR ACCESO (true) o NEGAR ACCESO (false).
     */
    fun resolveAccessRequest(
        context: Context,
        db: AppDatabase,
        condominiumId: String = "PRADOS_1",
        requestId: String,
        authorized: Boolean,
        resolutionNotes: String? = null
    ) {
        val currentList = _activeRequests.value
        val target = currentList.find { it.id == requestId } ?: return
        val newStatus = if (authorized) LiveAccessStatus.AUTHORIZED else LiveAccessStatus.DENIED
        val resolvedRequest = target.copy(
            status = newStatus,
            resolvedAtMillis = System.currentTimeMillis(),
            resolutionNotes = resolutionNotes ?: if (authorized) "Acceso autorizado por residente" else "Acceso denegado por residente"
        )

        _activeRequests.value = currentList.map { if (it.id == requestId) resolvedRequest else it }
        if (_lastSubmittedRequest.value?.id == requestId) {
            _lastSubmittedRequest.value = resolvedRequest
        }

        scope.launch {
            try {
                val checkInStatus = if (authorized) "CHECKED_IN" else "DENEGADO"
                val existing = db.visitorCheckInDao().getCheckInByFolio(requestId)
                if (existing != null) {
                    db.visitorCheckInDao().updateCheckInStatus(
                        id = existing.id,
                        status = checkInStatus,
                        notes = resolvedRequest.resolutionNotes
                    )
                }

                // Notificar en caseta/sistema de la decisión
                ResidentNotificationManager.notifyAccessDecisionResult(
                    context = context,
                    requestId = requestId,
                    visitorName = target.visitorName,
                    destinationHouse = target.destinationHouse,
                    authorized = authorized
                )

                // Actualizar Firestore
                val firestore = FirebaseConfigHelper.getFirestore()
                if (firestore != null && existing != null) {
                    val updatedCheckIn = existing.copy(
                        status = checkInStatus,
                        guardNotes = "${existing.guardNotes ?: ""}\n[Residente]: ${resolvedRequest.resolutionNotes}".trim()
                    )
                    val log = FirestoreVisitorLog.fromVisitorCheckIn(updatedCheckIn, condominiumId)
                    FirestoreTenantManager.saveVisitorLog(firestore, condominiumId, log)
                    FirestoreTenantManager.saveVisitorCheckIn(firestore, condominiumId, updatedCheckIn)
                }

                Log.i(TAG, "Solicitud $requestId resuelta: $newStatus por residente de ${target.destinationHouse}")
            } catch (e: Exception) {
                Log.e(TAG, "Error resolviendo solicitud: ${e.message}", e)
            }
        }
    }

    /**
     * Limpia la solicitud activa de caseta.
     */
    fun clearLastSubmitted() {
        _lastSubmittedRequest.value = null
    }
}
