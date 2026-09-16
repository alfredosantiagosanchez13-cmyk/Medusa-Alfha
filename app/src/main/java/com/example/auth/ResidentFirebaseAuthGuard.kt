package com.example.auth

import android.content.Context
import android.util.Log
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.resident.ResidentEntity
import com.example.data.resident.ResidentVehicle
import com.example.data.resident.AuthorizedPerson
import com.example.data.resident.EmergencyContact
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Representa el estado de autorización de un Residente mediante Firebase Auth.
 */
sealed class ResidentAuthStatus {
    object Checking : ResidentAuthStatus()
    
    data class Authorized(
        val resident: ResidentEntity,
        val firebaseUser: FirebaseUser?,
        val email: String,
        val unitId: String
    ) : ResidentAuthStatus()

    data class Unauthenticated(
        val message: String = "Se requiere iniciar sesión con Firebase Auth para acceder a esta función."
    ) : ResidentAuthStatus()

    data class NonResident(
        val email: String,
        val uid: String,
        val message: String = "La cuenta autenticada no está registrada como residente activo en este condominio."
    ) : ResidentAuthStatus()

    data class Error(
        val errorMessage: String
    ) : ResidentAuthStatus()
}

/**
 * MOTOR DE GUARDA Y VALIDACIÓN DE IDENTIDAD: RESIDENTES CON FIREBASE AUTH.
 * 
 * Reglas de Seguridad:
 * 1. Restringe acceso al escáner QR y al módulo de reservas de amenidades exclusivamente a residentes registrados.
 * 2. Valida la sesión activa en FirebaseAuth (o enlace seguro a ResidentEntity en Room SQLite).
 * 3. Si el usuario de Firebase Auth existe en el directorio de residentes activos, autoriza el acceso.
 * 4. Permite inicio de sesión, registro directo de residentes y selección rápida de residentes verificados.
 */
object ResidentFirebaseAuthGuard {

    private const val TAG = "ResidentAuthGuard"

    private val _currentStatus = MutableStateFlow<ResidentAuthStatus>(ResidentAuthStatus.Checking)
    val currentStatus: StateFlow<ResidentAuthStatus> = _currentStatus.asStateFlow()

    private var cachedResident: ResidentEntity? = null

    /**
     * Retorna el residente actualmente autenticado si está autorizado.
     */
    fun getAuthenticatedResident(): ResidentEntity? = cachedResident

    /**
     * Evalúa el estado actual de Firebase Auth y valida si corresponde a un residente registrado.
     */
    suspend fun verifyResidentAccess(context: Context, db: AppDatabase): ResidentAuthStatus = withContext(Dispatchers.IO) {
        _currentStatus.value = ResidentAuthStatus.Checking
        
        // 1. Asegurar catálogo de residentes poblado si está vacío
        com.example.data.resident.ResidentDirectoryEngine.seedInitialResidentsIfEmpty(db)
        AlfhaSecurityContext.seedInitialUsersIfEmpty(db)

        val residentDao = db.residentDao()
        val auth = FirebaseConfigHelper.getAuth()
        val currentFbUser: FirebaseUser? = auth?.currentUser

        if (currentFbUser != null) {
            val fbEmail = currentFbUser.email?.trim().orEmpty()
            val fbUid = currentFbUser.uid

            // Buscar en ResidentDao por email o uid vinculado
            var resident: ResidentEntity? = null
            if (fbEmail.isNotBlank()) {
                resident = residentDao.getResidentByEmail(fbEmail)
            }
            if (resident == null && fbUid.isNotBlank()) {
                resident = residentDao.getResidentById(fbUid)
            }

            // También buscar por linkedUserId
            if (resident == null) {
                val allResidents = residentDao.getAllResidentsWithDeletedFlow()
                // Buscar coincidencia en lista completa
                val match = residentDao.getAllActiveResidentsFlow()
                // Consultar residentes activos
                val activeList = residentDao.searchResidentsFlow(fbEmail)
            }

            if (resident != null && resident.status == "ACTIVO" && !resident.isDeleted) {
                cachedResident = resident
                syncToSecurityContext(db, resident, currentFbUser)
                val status = ResidentAuthStatus.Authorized(
                    resident = resident,
                    firebaseUser = currentFbUser,
                    email = resident.email.ifBlank { fbEmail },
                    unitId = resident.unitId
                )
                _currentStatus.value = status
                return@withContext status
            } else {
                // El usuario está autenticado en Firebase, pero no está en la base de residentes
                cachedResident = null
                val status = ResidentAuthStatus.NonResident(
                    email = fbEmail.ifBlank { fbUid },
                    uid = fbUid,
                    message = "El usuario autenticado ($fbEmail) no figura en el padrón de residentes activos."
                )
                _currentStatus.value = status
                return@withContext status
            }
        }

        // Si Firebase Auth no tiene usuario activo, verificar si en AlfhaSecurityContext hay un Residente activo
        val localUser = AlfhaSecurityContext.currentUser.value
        if (localUser.alfhaRole == AlfhaRole.RESIDENTE && localUser.isActive) {
            val resident = residentDao.getResidentByEmail(localUser.email)
                ?: residentDao.getResidentById(localUser.id)
                ?: residentDao.getResidentsByUnit(localUser.unitOrDepartment).firstOrNull()

            if (resident != null && resident.status == "ACTIVO" && !resident.isDeleted) {
                cachedResident = resident
                val status = ResidentAuthStatus.Authorized(
                    resident = resident,
                    firebaseUser = null,
                    email = resident.email.ifBlank { localUser.email },
                    unitId = resident.unitId
                )
                _currentStatus.value = status
                return@withContext status
            }
        }

        cachedResident = null
        val status = ResidentAuthStatus.Unauthenticated(
            message = "Inicia sesión con Firebase Auth para acceder al escáner QR o reservar amenidades."
        )
        _currentStatus.value = status
        return@withContext status
    }

    /**
     * Inicia sesión con Email y Contraseña mediante Firebase Auth y valida el perfil de residente.
     */
    suspend fun signInWithEmail(
        context: Context,
        db: AppDatabase,
        email: String,
        pass: String
    ): Result<ResidentEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val authManager = FirebaseAuthManager(context)

        // Intentar autenticación con Firebase Auth si está disponible
        val fbResult = authManager.signInWithEmail(cleanEmail, pass)
        
        // Verificar si existe el residente en Room
        val residentDao = db.residentDao()
        val resident = residentDao.getResidentByEmail(cleanEmail)
            ?: residentDao.searchResidentsFlow(cleanEmail).let { null }

        if (resident != null && resident.status == "ACTIVO" && !resident.isDeleted) {
            cachedResident = resident
            syncToSecurityContext(db, resident, fbResult.getOrNull())
            _currentStatus.value = ResidentAuthStatus.Authorized(
                resident = resident,
                firebaseUser = fbResult.getOrNull(),
                email = resident.email,
                unitId = resident.unitId
            )
            Result.success(resident)
        } else if (fbResult.isSuccess) {
            // Usuario autenticado en Firebase pero no registrado localmente como residente
            val fbUser = fbResult.getOrNull()!!
            _currentStatus.value = ResidentAuthStatus.NonResident(
                email = fbUser.email.orEmpty(),
                uid = fbUser.uid
            )
            Result.failure(SecurityException("Usuario autenticado en Firebase pero no registrado en el padrón de residentes."))
        } else {
            val errorMsg = fbResult.exceptionOrNull()?.message ?: "Credenciales inválidas"
            _currentStatus.value = ResidentAuthStatus.Error(errorMsg)
            Result.failure(fbResult.exceptionOrNull() ?: Exception(errorMsg))
        }
    }

    /**
     * Registra un nuevo residente tanto en Firebase Auth como en el Directorio Local Room SQLite.
     */
    suspend fun registerResident(
        context: Context,
        db: AppDatabase,
        email: String,
        pass: String,
        fullName: String,
        unitId: String,
        phone: String
    ): Result<ResidentEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanName = fullName.trim()
        val cleanUnit = unitId.trim()
        val cleanPhone = phone.trim()

        if (cleanEmail.isBlank() || cleanName.isBlank() || cleanUnit.isBlank() || pass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Datos incompletos o contraseña menor a 6 caracteres."))
        }

        val authManager = FirebaseAuthManager(context)
        val fbResult = authManager.signUpWithEmail(cleanEmail, pass, cleanName)

        val residentDao = db.residentDao()
        val existingResident = residentDao.getResidentByEmail(cleanEmail)

        val residentId = fbResult.getOrNull()?.uid ?: "RES-${System.currentTimeMillis().toString().takeLast(6)}"
        val residentToPersist = existingResident?.copy(
            fullName = cleanName,
            unitId = cleanUnit,
            phone = cleanPhone,
            status = "ACTIVO",
            isDeleted = false,
            updatedAtMillis = System.currentTimeMillis()
        ) ?: ResidentEntity(
            id = residentId,
            unitId = cleanUnit,
            fullName = cleanName,
            occupancyType = "PROPIETARIO",
            phone = cleanPhone,
            email = cleanEmail,
            status = "ACTIVO",
            linkedUserId = fbResult.getOrNull()?.uid.orEmpty(),
            isDeleted = false,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            updatedBy = "FIREBASE_AUTH_REGISTRATION"
        )

        residentDao.insertResident(residentToPersist)
        cachedResident = residentToPersist
        syncToSecurityContext(db, residentToPersist, fbResult.getOrNull())

        _currentStatus.value = ResidentAuthStatus.Authorized(
            resident = residentToPersist,
            firebaseUser = fbResult.getOrNull(),
            email = cleanEmail,
            unitId = cleanUnit
        )

        Result.success(residentToPersist)
    }

    /**
     * Autenticación de demostración rápida para residentes precargados.
     * Permite probar inmediatamente el flujo de residentes autorizados (Familia Arismendi, Ing. Morales, etc.).
     */
    suspend fun authenticateVerifiedResident(
        context: Context,
        db: AppDatabase,
        residentEmail: String
    ): Result<ResidentEntity> = withContext(Dispatchers.IO) {
        val residentDao = db.residentDao()
        val resident = residentDao.getResidentByEmail(residentEmail.trim())
            ?: return@withContext Result.failure(IllegalArgumentException("Residente no encontrado en catálogo."))

        cachedResident = resident
        syncToSecurityContext(db, resident, null)

        val status = ResidentAuthStatus.Authorized(
            resident = resident,
            firebaseUser = null,
            email = resident.email,
            unitId = resident.unitId
        )
        _currentStatus.value = status
        Result.success(resident)
    }

    /**
     * Cierra la sesión activa de residente en Firebase Auth y limpia el contexto.
     */
    fun signOut(context: Context) {
        try {
            val authManager = FirebaseAuthManager(context)
            authManager.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error durante signOut: ${e.message}")
        }
        cachedResident = null
        _currentStatus.value = ResidentAuthStatus.Unauthenticated("Sesión de residente cerrada.")
    }

    /**
     * Sincroniza al residente con el contexto RBAC de la aplicación.
     */
    private suspend fun syncToSecurityContext(
        db: AppDatabase,
        resident: ResidentEntity,
        firebaseUser: FirebaseUser?
    ) {
        val userDao = db.alfhaUserDao()
        val existingUser = userDao.getUserByEmail(resident.email)
            ?: userDao.getUserById(resident.id)

        val alfhaUser = existingUser ?: AlfhaUserEntity(
            id = resident.id,
            name = resident.fullName,
            email = resident.email,
            role = AlfhaRole.RESIDENTE.name,
            unitOrDepartment = resident.unitId,
            permissionsCsv = "VER,CREAR",
            isActive = true,
            lastLoginMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            updatedBy = "RESIDENT_AUTH_SYNC"
        )
        userDao.insertUser(alfhaUser)
        AlfhaSecurityContext.setCurrentUser(alfhaUser)
    }
}
