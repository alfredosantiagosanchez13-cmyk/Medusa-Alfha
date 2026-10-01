package com.example.data.firebase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.auth.AlfhaRole
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.profile.FirestoreUserProfile
import com.example.data.profile.UserProfileEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Gestor central de inicialización, verificación y aprovisionamiento de Firebase en MEDUSA ALFHA.
 * Conexión en producción al proyecto oficial 'medusa-alfha' y sincronización con Cloud Firestore.
 */
object FirebaseConfigHelper {

    private const val TAG = "FirebaseConfigHelper"
    private const val PREFS_NAME = "medusa_firebase_config_prefs"
    private const val KEY_API_KEY = "firebase_api_key"
    private const val KEY_APP_ID = "firebase_app_id"
    private const val KEY_PROJECT_ID = "firebase_project_id"
    private const val KEY_STORAGE_BUCKET = "firebase_storage_bucket"
    private const val KEY_CUSTOM_CONFIGURED = "firebase_custom_configured"

    // Credenciales oficiales de producción del proyecto 'medusa-alfha'
    const val OFFICIAL_PROJECT_ID = "medusa-alfha"
    const val OFFICIAL_APP_ID = "1:848355661410:android:9b6a76e3936ac98a47a33b"
    const val OFFICIAL_API_KEY = "AIzaSyByqVLU6HMB2zErHa4F9QQeVQWz9Xkq4Oc"
    const val OFFICIAL_STORAGE_BUCKET = "medusa-alfha.firebasestorage.app"
    const val OFFICIAL_PROJECT_NUMBER = "848355661410"
    const val OFFICIAL_PACKAGE_NAME = "com.aistudio.medusaalpha.qxvtkm"
    const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"

    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable.asStateFlow()

    private val _initializationStatusMessage = MutableStateFlow("Iniciando conexión con Firebase Cloud Firestore...")
    val initializationStatusMessage: StateFlow<String> = _initializationStatusMessage.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Inicializa Firebase conectando de forma predeterminada al proyecto oficial 'medusa-alfha'.
     */
    fun initialize(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            val hasCustom = prefs.getBoolean(KEY_CUSTOM_CONFIGURED, false)
            val savedApiKey = prefs.getString(KEY_API_KEY, "") ?: ""
            val savedAppId = prefs.getString(KEY_APP_ID, "") ?: ""
            val savedProjectId = prefs.getString(KEY_PROJECT_ID, "") ?: ""
            val savedBucket = prefs.getString(KEY_STORAGE_BUCKET, "") ?: ""

            val apiKey = if (hasCustom && savedApiKey.isNotBlank()) savedApiKey else OFFICIAL_API_KEY
            val appId = if (hasCustom && savedAppId.isNotBlank()) savedAppId else OFFICIAL_APP_ID
            val projectId = if (hasCustom && savedProjectId.isNotBlank()) savedProjectId else OFFICIAL_PROJECT_ID
            val storageBucket = if (hasCustom && savedBucket.isNotBlank()) savedBucket else OFFICIAL_STORAGE_BUCKET

            if (FirebaseApp.getApps(context).isEmpty()) {
                val optionsBuilder = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setProjectId(projectId)
                    .setStorageBucket(storageBucket)

                val app = FirebaseApp.initializeApp(context, optionsBuilder.build())
                if (app != null) {
                    _isFirebaseAvailable.value = true
                    _initializationStatusMessage.value = "Cloud Firestore activo en proyecto $projectId."
                    configureFirestoreSettings()
                    Log.i(TAG, "Firebase inicializado con éxito en $projectId ($appId)")
                    true
                } else {
                    _isFirebaseAvailable.value = false
                    _initializationStatusMessage.value = "Modo Local Autónomo (Room SQLite)."
                    false
                }
            } else {
                _isFirebaseAvailable.value = true
                _initializationStatusMessage.value = "Cloud Firestore activo y conectado."
                configureFirestoreSettings()
                true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error inicializando Firebase: ${e.message}")
            _isFirebaseAvailable.value = false
            _initializationStatusMessage.value = "Modo Local Autónomo (Room SQLite). Error: ${e.message}"
            false
        }
    }

    private fun configureFirestoreSettings() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true) // Persistencia en disco para soporte offline
                .build()
            firestore.firestoreSettings = settings
            Log.i(TAG, "Firestore configurado con persistencia offline activa.")
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo aplicar configuración de Firestore: ${e.message}")
        }
    }

    /**
     * Guarda y vincula dinámicamente credenciales de Firebase personalizadas.
     */
    fun configureCustomFirebase(
        context: Context,
        apiKey: String,
        appId: String,
        projectId: String,
        storageBucket: String? = null
    ): Result<Boolean> {
        val cleanApiKey = apiKey.trim()
        val cleanAppId = appId.trim()
        val cleanProjectId = projectId.trim()
        val cleanBucket = storageBucket?.trim() ?: ""

        if (cleanApiKey.isBlank() || cleanAppId.isBlank() || cleanProjectId.isBlank()) {
            return Result.failure(IllegalArgumentException("API Key, Application ID y Project ID son obligatorios."))
        }

        return try {
            val prefs = getPrefs(context)
            prefs.edit()
                .putBoolean(KEY_CUSTOM_CONFIGURED, true)
                .putString(KEY_API_KEY, cleanApiKey)
                .putString(KEY_APP_ID, cleanAppId)
                .putString(KEY_PROJECT_ID, cleanProjectId)
                .putString(KEY_STORAGE_BUCKET, cleanBucket)
                .apply()

            val optionsBuilder = FirebaseOptions.Builder()
                .setApiKey(cleanApiKey)
                .setApplicationId(cleanAppId)
                .setProjectId(cleanProjectId)

            if (cleanBucket.isNotBlank()) {
                optionsBuilder.setStorageBucket(cleanBucket)
            }

            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context, optionsBuilder.build())
            } else {
                val existingApp = FirebaseApp.getInstance()
                try {
                    existingApp.delete()
                } catch (t: Throwable) {
                    Log.w(TAG, "No se pudo eliminar app anterior: ${t.message}")
                }
                FirebaseApp.initializeApp(context, optionsBuilder.build())
            }

            if (app != null) {
                _isFirebaseAvailable.value = true
                _initializationStatusMessage.value = "Firebase vinculado con éxito al proyecto $cleanProjectId."
                configureFirestoreSettings()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("No se pudo instanciar FirebaseApp con las opciones proporcionadas."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando Firebase: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parsea y configura Firebase directamente pegando el contenido del archivo google-services.json.
     */
    fun configureFromGoogleServicesJson(context: Context, jsonText: String): Result<Boolean> {
        return try {
            val cleanJson = jsonText.trim()
            if (!cleanJson.startsWith("{")) {
                return Result.failure(IllegalArgumentException("El texto introducido no parece ser un JSON válido de google-services.json."))
            }

            val root = JSONObject(cleanJson)
            val projectInfo = root.getJSONObject("project_info")
            val projectId = projectInfo.getString("project_id")
            val storageBucket = projectInfo.optString("storage_bucket", "")

            val clientArray = root.getJSONArray("client")
            if (clientArray.length() == 0) {
                return Result.failure(IllegalArgumentException("El archivo google-services.json no contiene clientes registrados."))
            }

            val client0 = clientArray.getJSONObject(0)
            val clientInfo = client0.getJSONObject("client_info")
            val appId = clientInfo.getString("mobilesdk_app_id")

            val apiKeyArray = client0.getJSONArray("api_key")
            val apiKey = apiKeyArray.getJSONObject(0).getString("current_key")

            configureCustomFirebase(
                context = context,
                apiKey = apiKey,
                appId = appId,
                projectId = projectId,
                storageBucket = storageBucket
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear google-services.json: ${e.message}", e)
            Result.failure(IllegalArgumentException("Error al parsear JSON: ${e.message}"))
        }
    }

    /**
     * Devuelve las credenciales almacenadas actualmente en SharedPreferences.
     */
    fun getSavedConfig(context: Context): Map<String, String> {
        val prefs = getPrefs(context)
        return mapOf(
            "apiKey" to (prefs.getString(KEY_API_KEY, OFFICIAL_API_KEY) ?: OFFICIAL_API_KEY),
            "appId" to (prefs.getString(KEY_APP_ID, OFFICIAL_APP_ID) ?: OFFICIAL_APP_ID),
            "projectId" to (prefs.getString(KEY_PROJECT_ID, OFFICIAL_PROJECT_ID) ?: OFFICIAL_PROJECT_ID),
            "storageBucket" to (prefs.getString(KEY_STORAGE_BUCKET, OFFICIAL_STORAGE_BUCKET) ?: OFFICIAL_STORAGE_BUCKET),
            "isConfigured" to (prefs.getBoolean(KEY_CUSTOM_CONFIGURED, true).toString())
        )
    }

    /**
     * Prueba la conexión a Cloud Firestore escribiendo un documento de prueba en tiempo real.
     */
    suspend fun testFirestoreConnection(condominiumId: String = DEFAULT_CONDOMINIUM_ID): Result<String> {
        return withContext(Dispatchers.IO) {
            val fs = getFirestore() ?: return@withContext Result.failure(
                IllegalStateException("Firebase Firestore no está activo o inicializado.")
            )

            try {
                val start = System.currentTimeMillis()
                val testPayload = hashMapOf(
                    "status" to "ONLINE",
                    "system" to "MEDUSA ALFHA",
                    "condominiumId" to condominiumId,
                    "packageName" to OFFICIAL_PACKAGE_NAME,
                    "pingTimestamp" to System.currentTimeMillis(),
                    "diagnostics" to "Prueba de enlace bidireccional exitosa a medusa-alfha"
                )

                fs.collection(FirestoreTenantManager.ROOT_CONDOMINIUMS)
                    .document(condominiumId)
                    .collection("system_diagnostics")
                    .document("ping_test")
                    .set(testPayload)
                    .await()

                val duration = System.currentTimeMillis() - start
                Result.success("¡Enlace verificado con éxito! Proyecto: $OFFICIAL_PROJECT_ID · Latencia Firestore: ${duration}ms. Nube activa.")
            } catch (e: Exception) {
                Log.e(TAG, "Fallo en prueba de Firestore: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Da de alta oficialmente las cuentas operativas de MEDUSA ALFHA en Room SQLite
     * y las sincroniza en Cloud Firestore si la nube está disponible.
     */
    suspend fun provisionOfficialAccounts(
        context: Context,
        db: AppDatabase,
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Result<List<AlfhaUserEntity>> {
        return withContext(Dispatchers.IO) {
            try {
                val userDao = db.alfhaUserDao()
                val profileDao = db.userProfileDao()
                val auditDao = db.auditLogDao()

                val officialUsers = listOf(
                    AlfhaUserEntity(
                        id = "USR-ALFHA-000",
                        name = "Ing. Carlos Mendoza (Comando Central ALFHA)",
                        email = "alfhaseguridad070@gmail.com",
                        role = AlfhaRole.MAESTRO_ALFHA.name,
                        unitOrDepartment = "Comando Central ALFHA",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    ),
                    AlfhaUserEntity(
                        id = "USR-ALFHA-001",
                        name = "Ing. Carlos Mendoza (Maestro Alfa)",
                        email = "carlos.mendoza@alfhaseguridad.com",
                        role = AlfhaRole.MAESTRO_ALFHA.name,
                        unitOrDepartment = "Comando Central ALFHA",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    ),
                    AlfhaUserEntity(
                        id = "USR-ALFHA-002",
                        name = "Lic. Roberto Garza (Presidente Mesa Directiva)",
                        email = "mesa.directiva@condominio.com",
                        role = AlfhaRole.MESA_DIRECTIVA.name,
                        unitOrDepartment = "Presidencia y Consejo Directivo",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    ),
                    AlfhaUserEntity(
                        id = "USR-ALFHA-003",
                        name = "Lic. Patricia Ruiz (Administradora General)",
                        email = "administracion@condominio.com",
                        role = AlfhaRole.ADMINISTRACION.name,
                        unitOrDepartment = "Oficina de Administración General",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    ),
                    AlfhaUserEntity(
                        id = "USR-ALFHA-004",
                        name = "Comandante Roberto Gómez (Supervisión Táctica)",
                        email = "roberto.gomez@alfhaseguridad.com",
                        role = AlfhaRole.SUPERVISOR.name,
                        unitOrDepartment = "Supervisión Operativa Táctica y Rondas",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    ),
                    AlfhaUserEntity(
                        id = "USR-ALFHA-005",
                        name = "Oficial Juan Pérez (Caseta Principal)",
                        email = "caseta1@alfhaseguridad.com",
                        role = AlfhaRole.GUARDIA.name,
                        unitOrDepartment = "Garita de Acceso Vehicular Los Prados",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    ),
                    AlfhaUserEntity(
                        id = "USR-ALFHA-006",
                        name = "Familia Arismendi (Residente Titular)",
                        email = "arismendi.residente@condominio.com",
                        role = AlfhaRole.RESIDENTE.name,
                        unitOrDepartment = "Casa 54 · Circuito Los Álamos (Prados)",
                        permissionsCsv = "",
                        isActive = true,
                        updatedBy = "ALTA_OFICIAL_MEDUSA"
                    )
                )

                // 1. Guardar en Room SQLite (AlfhaUserEntity)
                for (user in officialUsers) {
                    val existing = userDao.getUserByEmail(user.email)
                    if (existing == null) {
                        userDao.insertUser(user)
                    } else {
                        userDao.updateUser(user)
                    }

                    // 2. Guardar en Room SQLite (UserProfileEntity)
                    val profileEntity = UserProfileEntity(
                        userId = user.id,
                        condominiumId = condominiumId,
                        email = user.email,
                        displayName = user.name,
                        role = user.role,
                        authorizedUnitNumber = user.unitOrDepartment,
                        phoneNumber = "555-010-ALFHA",
                        photoUrl = null,
                        occupancyType = if (user.role == AlfhaRole.RESIDENTE.name) "PROPIETARIO" else "OPERATIVO",
                        isActive = true
                    )
                    profileDao.insertOrUpdateProfile(profileEntity)
                }

                // 3. Sincronizar en Cloud Firestore si está activo
                val fs = getFirestore()
                if (fs != null) {
                    // Documento raíz del condominio Los Prados
                    try {
                        val condoData = hashMapOf(
                            "condominiumId" to condominiumId,
                            "name" to "Los Prados Residencial",
                            "totalHouses" to 261,
                            "circuits" to listOf("Circuito Los Álamos", "Circuito Prados 1", "Circuito Prados 2", "Circuito Prados 3"),
                            "system" to "MEDUSA ALFHA",
                            "status" to "OPERATIVO_PRODUCCION",
                            "adminEmail" to "alfhaseguridad070@gmail.com",
                            "lastSyncTimestamp" to System.currentTimeMillis()
                        )
                        fs.collection(FirestoreTenantManager.ROOT_CONDOMINIUMS)
                            .document(condominiumId)
                            .set(condoData, SetOptions.merge())
                            .await()
                    } catch (t: Throwable) {
                        Log.w(TAG, "No se pudo actualizar doc de condominio en Firestore: ${t.message}")
                    }

                    for (user in officialUsers) {
                        try {
                            val firestoreModel = FirestoreUserProfile(
                                userId = user.id,
                                condominiumId = condominiumId,
                                email = user.email,
                                displayName = user.name,
                                role = user.role,
                                authorizedUnitNumber = user.unitOrDepartment,
                                phoneNumber = "555-010-ALFHA",
                                occupancyType = if (user.role == AlfhaRole.RESIDENTE.name) "PROPIETARIO" else "OPERATIVO",
                                isActive = true
                            )
                            FirestoreTenantManager.saveUserProfile(fs, condominiumId, firestoreModel)
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo sincronizar usuario ${user.email} a Firestore: ${e.message}")
                        }
                    }
                }

                // 4. Registro inmutable de auditoría
                val audit = AuditLogEntity(
                    operatorId = "SISTEMA_MEDUSA_ALFHA",
                    eventDescription = "Aprovisionamiento y alta oficial de 7 cuentas operativas (Comando, Directiva, Admin, Caseta, Residente)",
                    severity = AuditLogEntity.Severity.INFO,
                    forensicPayload = "{\"cuentas_altas\": ${officialUsers.size}, \"condominiumId\": \"$condominiumId\", \"cloudSynced\": ${fs != null}}"
                )
                auditDao.insertAuditLog(audit)

                Result.success(officialUsers)
            } catch (e: Exception) {
                Log.e(TAG, "Error en aprovisionamiento de cuentas: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    fun getAuth(): FirebaseAuth? {
        return try {
            if (_isFirebaseAvailable.value) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            null
        }
    }

    fun getFirestore(): FirebaseFirestore? {
        return try {
            if (_isFirebaseAvailable.value) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            null
        }
    }

    fun getFirestoreInstance(): FirebaseFirestore? = getFirestore()
}
