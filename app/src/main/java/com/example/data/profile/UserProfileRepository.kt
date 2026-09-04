package com.example.data.profile

import android.util.Log
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repositorio de Perfiles de Usuario.
 * Aplica el Repository Pattern para abstraer Room SQLite y la sincronización con Firestore.
 */
class UserProfileRepository(
    private val userProfileDao: UserProfileDao,
    var activeCondominiumId: String = "PRADOS_1"
) {
    private val tag = "UserProfileRepository"

    fun getUserProfile(userId: String): Flow<UserProfileEntity?> =
        userProfileDao.getUserProfile(userId)

    fun getActiveProfiles(condominiumId: String = activeCondominiumId): Flow<List<UserProfileEntity>> =
        userProfileDao.getActiveProfilesByCondominium(condominiumId)

    fun getProfilesByUnit(unitNumber: String, condominiumId: String = activeCondominiumId): Flow<List<UserProfileEntity>> =
        userProfileDao.getProfilesByUnit(condominiumId, unitNumber)

    fun getProfilesByRole(role: String, condominiumId: String = activeCondominiumId): Flow<List<UserProfileEntity>> =
        userProfileDao.getProfilesByRole(condominiumId, role)

    suspend fun saveProfile(profile: UserProfileEntity): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Guardado local en Room
                userProfileDao.insertOrUpdateProfile(profile)

                // 2. Sincronización en la nube con Firestore (si está disponible)
                val fs = FirebaseConfigHelper.getFirestore()
                if (fs != null) {
                    val firestoreModel = FirestoreUserProfile.fromRoomEntity(profile)
                    FirestoreTenantManager.saveUserProfile(fs, profile.condominiumId, firestoreModel)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Error al guardar perfil de usuario: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateFcmToken(userId: String, token: String) {
        withContext(Dispatchers.IO) {
            userProfileDao.updateFcmToken(userId, token)
        }
    }

    suspend fun setUserActive(userId: String, isActive: Boolean) {
        withContext(Dispatchers.IO) {
            userProfileDao.setUserActiveStatus(userId, isActive)
        }
    }

    suspend fun deleteProfile(userId: String) {
        withContext(Dispatchers.IO) {
            userProfileDao.deleteProfileById(userId)
        }
    }
}
