package com.example.utils

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AccessAuthorizationRequest(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val condoId: String,
    val visitorName: String,
    val visitorType: String, // "Visita", "Proveedor", "Paquetería"
    val destinationHouse: String,
    val vehiclePlate: String? = null,
    val idPhotoPath: String? = null,
    val vehiclePhotoPath: String? = null,
    val status: String = "PENDING", // "PENDING", "APPROVED", "DENIED"
    val decidedAt: Long? = null,
    val hostResidentName: String? = null,
    val notes: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Gestor en tiempo real de solicitudes de autorización de acceso Garita-Residente.
 * Permite al guardia tomar 2 fotos rápidas y notificar al residente con un botón,
 * y al residente autorizar o denegar al instante desde su dispositivo.
 */
object AccessAuthorizationManager {
    private const val TAG = "AccessAuthManager"

    private val _requests = MutableStateFlow<List<AccessAuthorizationRequest>>(emptyList())
    val requests: StateFlow<List<AccessAuthorizationRequest>> = _requests.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun submitAccessRequest(
        context: Context,
        condoId: String,
        visitorName: String,
        visitorType: String,
        destinationHouse: String,
        vehiclePlate: String? = null,
        idPhotoPath: String? = null,
        vehiclePhotoPath: String? = null,
        hostResidentName: String? = null,
        notes: String? = null
    ): AccessAuthorizationRequest {
        val request = AccessAuthorizationRequest(
            condoId = condoId,
            visitorName = visitorName.trim(),
            visitorType = visitorType,
            destinationHouse = destinationHouse.trim(),
            vehiclePlate = vehiclePlate?.trim()?.uppercase()?.ifBlank { null },
            idPhotoPath = idPhotoPath,
            vehiclePhotoPath = vehiclePhotoPath,
            hostResidentName = hostResidentName?.trim()?.ifBlank { null },
            notes = notes
        )

        val updated = _requests.value.toMutableList().apply { add(0, request) }
        _requests.value = updated

        // Enviar notificación Push de alta prioridad al residente
        ResidentNotificationManager.notifyAccessAuthorizationRequest(
            context = context,
            requestId = request.id,
            visitorName = request.visitorName,
            accessType = request.visitorType,
            destinationHouse = request.destinationHouse,
            vehiclePlate = request.vehiclePlate,
            hasIdPhoto = !idPhotoPath.isNullOrBlank(),
            hasVehiclePhoto = !vehiclePhotoPath.isNullOrBlank()
        )

        // Sincronizar en Firestore
        syncToFirestore(request)

        return request
    }

    fun authorizeRequest(
        context: Context,
        requestId: String,
        visitorRepo: VisitorCheckInRepository? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val currentList = _requests.value
        val req = currentList.find { it.id == requestId } ?: return

        val updatedReq = req.copy(status = "APPROVED", decidedAt = System.currentTimeMillis())
        _requests.value = currentList.map { if (it.id == requestId) updatedReq else it }

        ResidentNotificationManager.notifyAccessDecisionResult(
            context = context,
            requestId = requestId,
            visitorName = req.visitorName,
            destinationHouse = req.destinationHouse,
            authorized = true
        )

        // Registrar en Room si visitorRepo fue provisto
        if (visitorRepo != null) {
            scope.launch {
                try {
                    val folio = "AUT-${System.currentTimeMillis().toString().takeLast(6)}"
                    visitorRepo.insertCheckIn(
                        VisitorCheckIn(
                            folio = folio,
                            visitorName = req.visitorName,
                            visitorDocument = if (req.idPhotoPath != null) "Foto ID Garita" else "Autorizado por Residente",
                            destinationHouse = req.destinationHouse,
                            passCode = folio,
                            passTypeLabel = req.visitorType,
                            vehiclePlate = req.vehiclePlate ?: "",
                            photoPath = req.idPhotoPath,
                            status = "CHECKED_IN",
                            guardNotes = "Autorizado por residente desde App. Fotos ID y Auto registradas.",
                            hostResidentName = req.hostResidentName ?: "Residente ${req.destinationHouse}"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error persistiendo check-in autorizado: ${e.message}", e)
                }
            }
        }

        updateFirestoreDecision(requestId, "APPROVED")
        onComplete?.invoke(true)
    }

    fun denyRequest(
        context: Context,
        requestId: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val currentList = _requests.value
        val req = currentList.find { it.id == requestId } ?: return

        val updatedReq = req.copy(status = "DENIED", decidedAt = System.currentTimeMillis())
        _requests.value = currentList.map { if (it.id == requestId) updatedReq else it }

        ResidentNotificationManager.notifyAccessDecisionResult(
            context = context,
            requestId = requestId,
            visitorName = req.visitorName,
            destinationHouse = req.destinationHouse,
            authorized = false
        )

        updateFirestoreDecision(requestId, "DENIED")
        onComplete?.invoke(false)
    }

    private fun syncToFirestore(request: AccessAuthorizationRequest) {
        scope.launch {
            try {
                val db = FirebaseConfigHelper.getFirestoreInstance() ?: return@launch
                val map = hashMapOf(
                    "id" to request.id,
                    "condoId" to request.condoId,
                    "visitorName" to request.visitorName,
                    "visitorType" to request.visitorType,
                    "destinationHouse" to request.destinationHouse,
                    "vehiclePlate" to (request.vehiclePlate ?: ""),
                    "hasIdPhoto" to (!request.idPhotoPath.isNullOrBlank()),
                    "hasVehiclePhoto" to (!request.vehiclePhotoPath.isNullOrBlank()),
                    "status" to request.status,
                    "timestamp" to request.timestamp,
                    "hostResidentName" to (request.hostResidentName ?: "")
                )
                db.collection("condominiums")
                    .document(request.condoId)
                    .collection("access_authorizations")
                    .document(request.id)
                    .set(map)
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync skipped: ${e.message}")
            }
        }
    }

    private fun updateFirestoreDecision(requestId: String, decision: String) {
        scope.launch {
            try {
                val db = FirebaseConfigHelper.getFirestoreInstance() ?: return@launch
                val req = _requests.value.find { it.id == requestId } ?: return@launch
                db.collection("condominiums")
                    .document(req.condoId)
                    .collection("access_authorizations")
                    .document(requestId)
                    .update("status", decision, "decidedAt", System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "Firestore decision update skipped: ${e.message}")
            }
        }
    }
}
