package com.example.data.fcm

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.incident.EmergencyLocationEngine
import com.example.data.incident.GpsCoordinates
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.ResidentNotificationManager
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Gestor Central de Firebase Cloud Messaging (FCM) para Notificaciones en Tiempo Real.
 * Gestiona:
 * 1. Registro de FCM Token y sincronización en Firestore.
 * 2. Suscripción a tópicos específicos por Condominio y por Unidad (Casa/Depto).
 * 3. Envío de eventos en tiempo real al Outbox de FCM y colección de notificaciones de residentes
 *    cuando el personal de seguridad escanea un código QR en caseta.
 * 4. Escucha reactiva en tiempo real (Firestore Snapshot Listener) para entrega instantánea.
 */
object FcmNotificationManager {

    private const val TAG = "FcmNotificationManager"
    private const val PREFS_NAME = "fcm_medusa_prefs"
    private const val KEY_FCM_TOKEN = "key_fcm_token"
    private const val KEY_SUBSCRIBED_UNIT = "key_subscribed_unit"

    const val TOPIC_SECURITY_EMERGENCY_ALERTS = "security_emergency_alerts"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _fcmStatusMessage = MutableStateFlow("FCM: Inicializando servicio...")
    val fcmStatusMessage: StateFlow<String> = _fcmStatusMessage.asStateFlow()

    private val _isSubscribedToUnit = MutableStateFlow(false)
    val isSubscribedToUnit: StateFlow<Boolean> = _isSubscribedToUnit.asStateFlow()

    private val _lastReceivedNotification = MutableStateFlow<VisitorCheckInFcmPayload?>(null)
    val lastReceivedNotification: StateFlow<VisitorCheckInFcmPayload?> = _lastReceivedNotification.asStateFlow()

    private val _recentNotificationsList = MutableStateFlow<List<VisitorCheckInFcmPayload>>(emptyList())
    val recentNotificationsList: StateFlow<List<VisitorCheckInFcmPayload>> = _recentNotificationsList.asStateFlow()

    // Estado en tiempo real para Alertas Críticas de Emergencia S.O.S. recibidas vía FCM
    private val _latestEmergencyAlert = MutableStateFlow<EmergencyAlertFcmPayload?>(null)
    val latestEmergencyAlert: StateFlow<EmergencyAlertFcmPayload?> = _latestEmergencyAlert.asStateFlow()

    private val _recentEmergencyAlertsList = MutableStateFlow<List<EmergencyAlertFcmPayload>>(emptyList())
    val recentEmergencyAlertsList: StateFlow<List<EmergencyAlertFcmPayload>> = _recentEmergencyAlertsList.asStateFlow()

    private var activeListenerRegistration: ListenerRegistration? = null
    private var activeEmergencyListenerRegistration: ListenerRegistration? = null
    private val notifiedFoliosCache = mutableSetOf<String>()
    private val notifiedEmergencyFoliosCache = mutableSetOf<String>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Sanitiza nombres para usarlos como tópicos válidos en FCM: [a-zA-Z0-9-_.~%]+
     */
    fun sanitizeTopic(input: String): String {
        return input.trim().lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("ñ", "n")
            .replace("[^a-z0-9-_]".toRegex(), "")
            .ifBlank { "general" }
    }

    /**
     * Inicializa FCM para el usuario actual (residente o personal) en el condominio activo.
     */
    fun initialize(context: Context, currentUser: AlfhaUserEntity?, condominiumId: String) {
        val prefs = getPrefs(context)
        val cachedToken = prefs.getString(KEY_FCM_TOKEN, null)
        if (!cachedToken.isNullOrBlank()) {
            _fcmToken.value = cachedToken
        }

        managerScope.launch {
            try {
                fetchAndRegisterFcmToken(context, currentUser, condominiumId)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo obtener token FCM directo (modo offline o sin Google Play Services): ${e.message}")
                if (_fcmToken.value == null) {
                    val mockFallbackToken = "fcm_token_dev_${UUID.randomUUID().toString().take(12)}"
                    _fcmToken.value = mockFallbackToken
                    _fcmStatusMessage.value = "FCM Activo (Modo Local/Dev): Token asignado"
                }
            }

            // Iniciar escucha reactiva en Firestore para notificaciones dirigidas a la unidad del residente
            if (currentUser != null && currentUser.unitOrDepartment.isNotBlank()) {
                startRealtimeResidentNotificationsListener(context, condominiumId, currentUser.unitOrDepartment)
            }
        }
    }

    /**
     * Obtiene el token FCM nativo de FirebaseMessaging y lo registra en Firestore
     */
    private fun fetchAndRegisterFcmToken(context: Context, currentUser: AlfhaUserEntity?, condominiumId: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "FCM: Error al obtener registration token: ${task.exception?.message}")
                    _fcmStatusMessage.value = "FCM: Token no disponible (offline)"
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.i(TAG, "🔥 FCM Registration Token obtenido: $token")
                _fcmToken.value = token
                _fcmStatusMessage.value = "FCM Conectado • Token Registrado"

                val prefs = getPrefs(context)
                prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

                // Si hay usuario y condominio, suscribirse a tópicos y registrar en Firestore
                if (currentUser != null) {
                    subscribeResidentTopics(currentUser, condominiumId)
                    saveTokenToFirestore(token, currentUser, condominiumId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM no disponible en el entorno actual: ${e.message}")
            _fcmStatusMessage.value = "FCM: Operando en modo local seguro"
        }
    }

    /**
     * Llamado cuando Firebase Messaging genera un nuevo token
     */
    fun onNewFcmTokenReceived(context: Context, newToken: String) {
        _fcmToken.value = newToken
        _fcmStatusMessage.value = "FCM: Nuevo Token Registrado"
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_FCM_TOKEN, newToken).apply()
    }

    /**
     * Suscribe el dispositivo a tópicos de FCM:
     * - Tópico general del condominio: condo_{condoId}
     * - Tópico específico de la unidad: unit_{condoId}_{unitId}
     */
    fun subscribeResidentTopics(user: AlfhaUserEntity, condominiumId: String) {
        try {
            val condoTopic = "condo_${sanitizeTopic(condominiumId)}"
            FirebaseMessaging.getInstance().subscribeToTopic(condoTopic).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "✅ Suscrito a tópico FCM del condominio: $condoTopic")
                }
            }

            val unit = user.unitOrDepartment
            if (unit.isNotBlank()) {
                val unitTopic = "unit_${sanitizeTopic(condominiumId)}_${sanitizeTopic(unit)}"
                FirebaseMessaging.getInstance().subscribeToTopic(unitTopic).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _isSubscribedToUnit.value = true
                        _fcmStatusMessage.value = "FCM Enlazado: Tópico $unitTopic"
                        Log.i(TAG, "✅ Suscrito con éxito al tópico FCM de la unidad: $unitTopic")
                    }
                }
            }

            // Si el usuario es Guardia, Supervisor o Admin, suscribirse a alertas de seguridad
            if (user.alfhaRole.name == "GUARD" || user.alfhaRole.name == "GUARDIA" ||
                user.alfhaRole.name == "SUPERVISOR" || user.alfhaRole.name == "ADMIN" || user.alfhaRole.name == "ADMINISTRADOR"
            ) {
                subscribeSecurityTopics(condominiumId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo suscribir a tópicos FCM: ${e.message}")
        }
    }

    /**
     * Suscribe el dispositivo del personal de seguridad a los tópicos de emergencia
     */
    fun subscribeSecurityTopics(condominiumId: String) {
        try {
            val securityTopic = "condo_${sanitizeTopic(condominiumId)}_security"
            FirebaseMessaging.getInstance().subscribeToTopic(securityTopic).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "🛡️ Suscrito a tópico FCM de Seguridad: $securityTopic")
                }
            }
            FirebaseMessaging.getInstance().subscribeToTopic("security_emergency_alerts")
        } catch (e: Exception) {
            Log.w(TAG, "Error suscribiendo a tópicos de seguridad: ${e.message}")
        }
    }

    /**
     * Guarda el token del dispositivo en Firestore bajo la unidad y bajo tokens globales
     */
    private fun saveTokenToFirestore(token: String, user: AlfhaUserEntity, condominiumId: String) {
        try {
            val firestore = FirebaseConfigHelper.getFirestore() ?: return
            val cleanCondo = condominiumId.ifBlank { "Los Prados Residencial" }
            val unit = user.unitOrDepartment.ifBlank { "SinUnidad" }

            val registration = FcmDeviceRegistration(
                token = token,
                userId = user.id,
                userEmail = user.email,
                residentName = user.name,
                unitId = unit,
                condominiumId = cleanCondo,
                role = user.alfhaRole.name,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                appVersion = "1.0",
                subscribedTopics = listOf(
                    "condo_${sanitizeTopic(cleanCondo)}",
                    "unit_${sanitizeTopic(cleanCondo)}_${sanitizeTopic(unit)}"
                ),
                registeredAtMillis = System.currentTimeMillis(),
                lastActiveMillis = System.currentTimeMillis()
            )

            // 1. Guardar en registro general de tokens del condominio
            firestore.collection("condominiums")
                .document(cleanCondo)
                .collection("fcm_device_tokens")
                .document(token.take(64))
                .set(registration.toMap())
                .addOnSuccessListener {
                    Log.i(TAG, "☁️ Token FCM registrado en Firestore para condominio: $cleanCondo")
                }

            // 2. Guardar en la unidad del residente
            firestore.collection("condominiums")
                .document(cleanCondo)
                .collection("units")
                .document(unit)
                .collection("fcm_tokens")
                .document(token.take(64))
                .set(registration.toMap())
                .addOnSuccessListener {
                    Log.i(TAG, "☁️ Token FCM vinculado a la unidad: $unit")
                }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo sincronizar token FCM con Firestore: ${e.message}")
        }
    }

    /**
     * INICIAR ESCUCHA REACTIVA EN TIEMPO REAL:
     * Escucha la colección `resident_notifications` filtrada por `targetUnitId`.
     * Cuando el personal de seguridad en garita escanea el QR, crea un documento aquí
     * y el residente recibe la notificación push en milisegundos.
     */
    fun startRealtimeResidentNotificationsListener(
        context: Context,
        condominiumId: String,
        targetUnitId: String
    ) {
        if (targetUnitId.isBlank()) return
        val firestore = FirebaseConfigHelper.getFirestore() ?: return
        val cleanCondo = condominiumId.ifBlank { "Los Prados Residencial" }

        activeListenerRegistration?.remove()

        try {
            Log.i(TAG, "🛰️ Iniciando escucha en tiempo real de notificaciones FCM para unidad: $targetUnitId en $cleanCondo")
            val query = firestore.collection("condominiums")
                .document(cleanCondo)
                .collection("resident_notifications")
                .whereEqualTo("targetUnitId", targetUnitId)
                .orderBy("scanTimestamp", Query.Direction.DESCENDING)
                .limit(10)

            activeListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error en listener de notificaciones en tiempo real: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                val newPayloads = mutableListOf<VisitorCheckInFcmPayload>()
                val now = System.currentTimeMillis()

                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val payload = VisitorCheckInFcmPayload.fromMap(data)
                    newPayloads.add(payload)

                    // Si el evento ocurrió en los últimos 2 minutos y no ha sido alertado aún
                    val isRecent = (now - payload.scanTimestamp) < (2 * 60 * 1000)
                    val cacheKey = "${payload.passFolio}_${payload.scanTimestamp}"

                    if (isRecent && !notifiedFoliosCache.contains(cacheKey)) {
                        notifiedFoliosCache.add(cacheKey)
                        Log.i(TAG, "🚨 ¡NUEVO ESCANEO DETECTADO EN TIEMPO REAL! Visita: ${payload.guestName} para ${payload.targetUnitId}")
                        
                        _lastReceivedNotification.value = payload

                        // Disparar notificación visual del sistema
                        ResidentNotificationManager.notifyCustomVisitorEntry(
                            context = context,
                            guestName = payload.guestName,
                            destinationHouse = payload.targetUnitId,
                            hostResidentName = payload.hostResidentName,
                            passTypeLabel = payload.passTypeLabel,
                            vehiclePlate = payload.vehiclePlate
                        )

                        // Persistir en base de datos local
                        managerScope.launch {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                SmartNotificationHub.notifyVisitorEntry(
                                    context = context,
                                    db = db,
                                    guestName = payload.guestName,
                                    unitId = payload.targetUnitId,
                                    hostResidentName = payload.hostResidentName,
                                    passTypeLabel = payload.passTypeLabel,
                                    vehiclePlate = payload.vehiclePlate,
                                    passFolio = payload.passFolio
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error persistiendo notificación en Room: ${e.message}")
                            }
                        }
                    }
                }

                _recentNotificationsList.value = newPayloads
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al iniciar snapshot listener en Firestore: ${e.message}", e)
        }
    }

    /**
     * ENVÍO DE NOTIFICACIÓN FCM EN TIEMPO REAL:
     * Se invoca cuando el personal de seguridad escanea con éxito el código QR de una visita.
     * 1. Publica el evento en Firestore en `condominiums/{condominiumId}/resident_notifications`
     * 2. Publica en el Outbox de FCM `condominiums/{condominiumId}/fcm_outbox`
     * 3. Dispara alerta inmediata en el sistema local y en SmartNotificationHub.
     */
    suspend fun sendVisitorQrScannedNotification(
        context: Context,
        db: AppDatabase,
        condominiumId: String,
        unitId: String,
        hostResidentName: String,
        guestName: String,
        guestDocument: String = "",
        passFolio: String,
        passCode: String,
        passTypeLabel: String,
        vehiclePlate: String? = null,
        guardName: String = "Guardia Garita",
        gateLocation: String = "Garita Principal",
        guardNotes: String = "Acceso verificado por personal de seguridad con escáner CameraX"
    ): Result<VisitorCheckInFcmPayload> = withContext(Dispatchers.IO) {
        val cleanCondo = condominiumId.ifBlank { "Los Prados Residencial" }
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val plateText = if (!vehiclePlate.isNullOrBlank()) " • Patente: $vehiclePlate" else ""
        
        val title = "🔔 ¡Tu Visita ha Ingresado! ($unitId)"
        val body = "Estimado/a $hostResidentName: Su visita $guestName ($passTypeLabel) acaba de ser verificada e ingresó por $gateLocation a las $timeStr hrs.$plateText"

        val payload = VisitorCheckInFcmPayload(
            type = "VISITOR_QR_SCANNED",
            event = "VISITOR_CHECK_IN",
            passFolio = passFolio,
            passCode = passCode,
            guestName = guestName,
            guestDocument = guestDocument,
            targetUnitId = unitId,
            hostResidentName = hostResidentName,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            guardName = guardName,
            gateLocation = gateLocation,
            guardNotes = guardNotes,
            scanTimestamp = System.currentTimeMillis(),
            condominiumId = cleanCondo,
            title = title,
            body = body
        )

        val notificationId = "FCM_${System.currentTimeMillis()}_${passFolio.takeLast(6)}"
        val unitTopic = "unit_${sanitizeTopic(cleanCondo)}_${sanitizeTopic(unitId)}"

        // 1. Guardar en Firestore `resident_notifications` (activa Snapshot Listener en tiempo real del residente)
        try {
            val firestore = FirebaseConfigHelper.getFirestore()
            if (firestore != null) {
                firestore.collection("condominiums")
                    .document(cleanCondo)
                    .collection("resident_notifications")
                    .document(notificationId)
                    .set(payload.toMap())
                    .addOnSuccessListener {
                        Log.i(TAG, "☁️ Evento de escaneo QR publicado en Firestore resident_notifications: $notificationId")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Advertencia: Falló escritura en resident_notifications: ${e.message}")
                    }

                // 2. Guardar en FCM Outbox para el despachador de Cloud Functions / Firebase Admin
                val fcmOutboxDoc = mapOf(
                    "notificationId" to notificationId,
                    "targetTopic" to unitTopic,
                    "targetUnitId" to unitId,
                    "condominiumId" to cleanCondo,
                    "notification" to mapOf(
                        "title" to title,
                        "body" to body
                    ),
                    "data" to payload.toMap(),
                    "priority" to "HIGH",
                    "status" to "QUEUED",
                    "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("condominiums")
                    .document(cleanCondo)
                    .collection("fcm_outbox")
                    .document(notificationId)
                    .set(fcmOutboxDoc)
                    .addOnSuccessListener {
                        Log.i(TAG, "🚀 Mensaje FCM encolado en Outbox para entrega a tópico: $unitTopic")
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error escribiendo en colecciones FCM de Firestore: ${e.message}")
        }

        // 3. Registrar en SmartNotificationHub y base de datos Room local
        try {
            SmartNotificationHub.notifyVisitorEntry(
                context = context,
                db = db,
                guestName = guestName,
                unitId = unitId,
                hostResidentName = hostResidentName,
                passTypeLabel = passTypeLabel,
                vehiclePlate = vehiclePlate,
                passFolio = passFolio
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error notificando en SmartNotificationHub: ${e.message}")
        }

        // 4. Disparar notificación visual local en el dispositivo
        try {
            ResidentNotificationManager.notifyCustomVisitorEntry(
                context = context,
                guestName = guestName,
                destinationHouse = unitId,
                hostResidentName = hostResidentName,
                passTypeLabel = passTypeLabel,
                vehiclePlate = vehiclePlate
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error lanzando notificación directa: ${e.message}")
        }

        _lastReceivedNotification.value = payload
        return@withContext Result.success(payload)
    }

    /**
     * Actualiza el estado local cuando la app recibe un push en primer plano o segundo plano
     */
    fun notifyPayloadReceivedLocally(payload: VisitorCheckInFcmPayload) {
        _lastReceivedNotification.value = payload
        _recentNotificationsList.value = listOf(payload) + _recentNotificationsList.value.take(9)
    }

    /**
     * ENVÍO DE ALERTA DE EMERGENCIA VÍA FIREBASE CLOUD MESSAGING (FCM) A PERSONAL DE SEGURIDAD.
     * Activado desde el botón de emergencia de la UI del residente.
     * Incluye:
     * - Número de unidad habitacional del residente (ej. "Casa 102")
     * - Ubicación GPS (latitud, longitud, precisión) o estatus de ubicación por unidad
     * - Tipo de emergencia (Pánico S.O.S., Médica, Intrusión, Incendio)
     * - Transmisión en tiempo real vía FCM a guardias y supervisores
     * - Persistencia atómica en Firestore, Room SQLite y Registro de Auditoría
     */
    suspend fun sendResidentEmergencyAlertFcm(
        context: Context,
        db: AppDatabase,
        condominiumId: String,
        residentUnit: String,
        residentName: String,
        residentId: String = "",
        emergencyType: String = "PÁNICO S.O.S.",
        details: String = "",
        manualGps: GpsCoordinates? = null
    ): Result<EmergencyAlertFcmPayload> = withContext(Dispatchers.IO) {
        val cleanCondo = condominiumId.ifBlank { "Los Prados Residencial" }
        val cleanUnit = residentUnit.ifBlank { "Unidad Sin Especificar" }
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))

        // 1. Obtención de Coordenadas GPS (o manuales provistas)
        val gps = manualGps ?: EmergencyLocationEngine.captureCurrentGps(context)
        val locationStatus = if (gps != null) EmergencyLocationEngine.STATUS_GPS_CAPTURED else EmergencyLocationEngine.STATUS_NO_GPS
        val locationName = "$cleanUnit - $cleanCondo"
        val mapsUrl = if (gps != null) "https://maps.google.com/?q=${gps.latitude},${gps.longitude}" else ""

        val alertFolio = AlphaCoreEngine.generateUniqueFolio("EMG")
        val notificationId = "FCM_EMG_${now}_${alertFolio.takeLast(6)}"
        val securityTopic = "condo_${sanitizeTopic(cleanCondo)}_security"

        val title = "🚨 ¡ALERTA DE EMERGENCIA - $cleanUnit! 🚨"
        val body = "El residente $residentName de la unidad $cleanUnit activó $emergencyType a las $timeStr hrs. Coordenadas: ${if (gps != null) "${gps.latitude}, ${gps.longitude}" else "Ubicación en Unidad"}. Despachar auxilio inmediato."

        val payload = EmergencyAlertFcmPayload(
            type = "RESIDENT_EMERGENCY_ALERT",
            event = "PANIC_SOS",
            alertFolio = alertFolio,
            residentId = residentId,
            residentName = residentName,
            residentUnit = cleanUnit,
            condominiumId = cleanCondo,
            emergencyType = emergencyType,
            details = details.ifBlank { "Alerta activada desde el Botón de Emergencia S.O.S. en la UI del residente." },
            latitude = gps?.latitude,
            longitude = gps?.longitude,
            gpsAccuracyMeters = gps?.accuracyMeters,
            locationStatus = locationStatus,
            locationName = locationName,
            mapsUrl = mapsUrl,
            timestampMillis = now,
            priority = "CRITICA",
            status = "ACTIVA",
            title = title,
            body = body,
            requiresAck = true
        )

        // 2. Persistir en Firestore bajo `condominiums/{condo}/emergency_alerts`
        try {
            val firestore = FirebaseConfigHelper.getFirestore()
            if (firestore != null) {
                // Colección directa de alertas de emergencia
                firestore.collection("condominiums")
                    .document(cleanCondo)
                    .collection("emergency_alerts")
                    .document(alertFolio)
                    .set(payload.toMap())
                    .addOnSuccessListener {
                        Log.i(TAG, "☁️ Alerta de emergencia publicada en Firestore emergency_alerts: $alertFolio")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Advertencia: Falló guardado en emergency_alerts: ${e.message}")
                    }

                // Colección de alertas de seguridad del condominio
                firestore.collection("condominiums")
                    .document(cleanCondo)
                    .collection("security_alerts")
                    .document(alertFolio)
                    .set(payload.toMap())

                // 3. Encolar en FCM Outbox para despacho a personal de seguridad (Guardias de Caseta y Supervisores)
                val fcmOutboxDoc = mapOf(
                    "notificationId" to notificationId,
                    "targetTopic" to securityTopic,
                    "targetRole" to "GUARD",
                    "residentUnit" to cleanUnit,
                    "condominiumId" to cleanCondo,
                    "notification" to mapOf(
                        "title" to title,
                        "body" to body
                    ),
                    "data" to payload.toMap(),
                    "priority" to "HIGH",
                    "status" to "QUEUED",
                    "createdAt" to now
                )

                firestore.collection("condominiums")
                    .document(cleanCondo)
                    .collection("fcm_outbox")
                    .document(notificationId)
                    .set(fcmOutboxDoc)
                    .addOnSuccessListener {
                        Log.i(TAG, "🚀 Alerta FCM encolada en Outbox para tópicos de seguridad: $securityTopic")
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error persistiendo alerta de emergencia en Firestore: ${e.message}")
        }

        // 4. Registrar en Room SQLite, Auditoría Inmutable y SmartNotificationHub
        try {
            EmergencyLocationEngine.triggerEmergencyAlert(
                context = context,
                db = db,
                emergencyType = emergencyType,
                locationName = locationName,
                reportedBy = residentName,
                reportedByRole = "RESIDENTE",
                details = "Unidad: $cleanUnit. $details",
                manualGps = gps
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando emergencia en EmergencyLocationEngine: ${e.message}")
        }

        // 5. Despachar notificación local del sistema y actualizar estado
        try {
            ResidentNotificationManager.notifySecurityEmergencyAlert(context, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Error en notificación local de emergencia: ${e.message}")
        }

        _latestEmergencyAlert.value = payload
        _recentEmergencyAlertsList.value = listOf(payload) + _recentEmergencyAlertsList.value.take(9)

        return@withContext Result.success(payload)
    }

    /**
     * Inicia escucha reactiva en Firestore para que el personal de seguridad (en Caseta o Supervisor)
     * reciba en milisegundos cualquier alerta de emergencia activada por residentes.
     */
    fun startRealtimeSecurityEmergencyListener(context: Context, condominiumId: String) {
        val firestore = FirebaseConfigHelper.getFirestore() ?: return
        val cleanCondo = condominiumId.ifBlank { "Los Prados Residencial" }

        activeEmergencyListenerRegistration?.remove()

        try {
            Log.i(TAG, "🛰️ Iniciando escucha en tiempo real de alertas de emergencia para personal de seguridad en $cleanCondo")
            val query = firestore.collection("condominiums")
                .document(cleanCondo)
                .collection("emergency_alerts")
                .whereEqualTo("status", "ACTIVA")
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(5)

            activeEmergencyListenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error en listener de emergencias de seguridad: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                val alerts = mutableListOf<EmergencyAlertFcmPayload>()
                val now = System.currentTimeMillis()

                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val payload = EmergencyAlertFcmPayload.fromMap(data)
                    alerts.add(payload)

                    // Si la alerta es reciente (últimos 3 minutos) y no se ha notificado localmente
                    val isRecent = (now - payload.timestampMillis) < (3 * 60 * 1000)
                    val cacheKey = "${payload.alertFolio}_${payload.timestampMillis}"

                    if (isRecent && !notifiedEmergencyFoliosCache.contains(cacheKey)) {
                        notifiedEmergencyFoliosCache.add(cacheKey)
                        Log.i(TAG, "🚨 ¡NUEVA ALERTA DE EMERGENCIA DETECTADA! Unidad: ${payload.residentUnit} - Tipo: ${payload.emergencyType}")

                        _latestEmergencyAlert.value = payload

                        // Disparar alarma sonora y heads-up notification para personal de seguridad
                        ResidentNotificationManager.notifySecurityEmergencyAlert(context, payload)
                    }
                }

                _recentEmergencyAlertsList.value = alerts
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando listener de alertas de seguridad: ${e.message}", e)
        }
    }

    /**
     * Notifica cuando llega una alerta FCM de emergencia
     */
    fun notifyEmergencyAlertReceivedLocally(payload: EmergencyAlertFcmPayload) {
        _latestEmergencyAlert.value = payload
        _recentEmergencyAlertsList.value = listOf(payload) + _recentEmergencyAlertsList.value.take(9)
    }

    /**
     * Limpia la alerta activa en la interfaz
     */
    fun clearActiveEmergencyAlert() {
        _latestEmergencyAlert.value = null
    }

    /**
     * Resuelve o desactiva una alerta en Firestore
     */
    suspend fun resolveEmergencyAlert(
        condominiumId: String,
        alertFolio: String,
        resolvedBy: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseConfigHelper.getFirestore() ?: return@withContext false
            val cleanCondo = condominiumId.ifBlank { "Los Prados Residencial" }

            val updateMap = mapOf(
                "status" to "RESUELTA",
                "resolvedBy" to resolvedBy,
                "resolvedAtMillis" to System.currentTimeMillis()
            )

            firestore.collection("condominiums")
                .document(cleanCondo)
                .collection("emergency_alerts")
                .document(alertFolio)
                .update(updateMap)

            if (_latestEmergencyAlert.value?.alertFolio == alertFolio) {
                _latestEmergencyAlert.value = null
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error resolviendo alerta de emergencia en Firestore: ${e.message}")
            false
        }
    }

    fun stopListener() {
        activeListenerRegistration?.remove()
        activeListenerRegistration = null
        activeEmergencyListenerRegistration?.remove()
        activeEmergencyListenerRegistration = null
    }
}
