package com.example.data.auth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlfhaUserDao {

    @Query("SELECT * FROM alfha_users ORDER BY role ASC, name ASC")
    fun getAllUsersFlow(): Flow<List<AlfhaUserEntity>>

    @Query("SELECT * FROM alfha_users WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getActiveUsers(): List<AlfhaUserEntity>

    @Query("SELECT * FROM alfha_users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): AlfhaUserEntity?

    @Query("SELECT * FROM alfha_users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): AlfhaUserEntity?

    @Query("SELECT * FROM alfha_users WHERE role = :roleName")
    suspend fun getUsersByRole(roleName: String): List<AlfhaUserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: AlfhaUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<AlfhaUserEntity>)

    @Update
    suspend fun updateUser(user: AlfhaUserEntity)

    @Query("UPDATE alfha_users SET role = :newRole, permissionsCsv = :newPermissionsCsv, updatedAtMillis = :updatedAt, updatedBy = :updatedBy WHERE id = :userId")
    suspend fun updateUserRoleAndPermissions(userId: String, newRole: String, newPermissionsCsv: String, updatedAt: Long, updatedBy: String)

    @Query("UPDATE alfha_users SET lastLoginMillis = :loginTime WHERE id = :userId")
    suspend fun updateLastLogin(userId: String, loginTime: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM alfha_users")
    suspend fun getUserCount(): Int
}
