package com.example.data.profile

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para el Perfil de Usuario en Room SQLite.
 */
@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun getUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfileSync(userId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE email = :email LIMIT 1")
    suspend fun getProfileByEmail(email: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL') AND isActive = 1 ORDER BY displayName ASC")
    fun getActiveProfilesByCondominium(condominiumId: String): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL') AND authorizedUnitNumber = :unitNumber ORDER BY displayName ASC")
    fun getProfilesByUnit(condominiumId: String, unitNumber: String): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL') AND role = :role ORDER BY displayName ASC")
    fun getProfilesByRole(condominiumId: String, role: String): Flow<List<UserProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfileEntity>)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET fcmToken = :token, updatedAtMillis = :updatedAt WHERE userId = :userId")
    suspend fun updateFcmToken(userId: String, token: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET isActive = :isActive, updatedAtMillis = :updatedAt WHERE userId = :userId")
    suspend fun setUserActiveStatus(userId: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfileById(userId: String)

    @Query("SELECT COUNT(*) FROM user_profiles WHERE (condominiumId = :condominiumId OR condominiumId = 'GENERAL')")
    suspend fun countProfiles(condominiumId: String): Int
}
