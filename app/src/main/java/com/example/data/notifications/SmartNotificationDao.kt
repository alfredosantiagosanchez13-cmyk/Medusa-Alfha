package com.example.data.notifications

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: SmartNotificationEntity): Long

    @Query("SELECT * FROM smart_notifications ORDER BY timestampMillis DESC")
    fun getAllNotificationsFlow(): Flow<List<SmartNotificationEntity>>

    @Query("SELECT * FROM smart_notifications WHERE targetRole = :role OR targetRole = 'ALL' ORDER BY timestampMillis DESC")
    fun getNotificationsForRoleFlow(role: String): Flow<List<SmartNotificationEntity>>

    @Query("SELECT * FROM smart_notifications WHERE (targetRole = :role OR targetRole = 'ALL') AND (targetUnitId = :unitId OR targetUnitId IS NULL) ORDER BY timestampMillis DESC")
    fun getNotificationsForResidentFlow(role: String, unitId: String): Flow<List<SmartNotificationEntity>>

    @Query("SELECT * FROM smart_notifications WHERE requiresHumanAction = 1 AND isResolved = 0 ORDER BY timestampMillis DESC")
    fun getActiveActionableNotificationsFlow(): Flow<List<SmartNotificationEntity>>

    @Query("SELECT * FROM smart_notifications WHERE isResolved = 0 ORDER BY timestampMillis DESC")
    fun getUnresolvedNotificationsFlow(): Flow<List<SmartNotificationEntity>>

    @Query("SELECT COUNT(*) FROM smart_notifications WHERE (targetRole = :role OR targetRole = 'ALL') AND isRead = 0")
    fun getUnreadCountForRole(role: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM smart_notifications WHERE requiresHumanAction = 1 AND isResolved = 0")
    fun getPendingActionCountFlow(): Flow<Int>

    @Query("SELECT * FROM smart_notifications WHERE deduplicationKey = :dedupKey LIMIT 1")
    suspend fun getByDeduplicationKey(dedupKey: String): SmartNotificationEntity?

    @Query("SELECT COUNT(*) FROM smart_notifications WHERE deduplicationKey = :dedupKey AND timestampMillis > :sinceMillis")
    suspend fun countRecentWithKey(dedupKey: String, sinceMillis: Long): Int

    @Query("UPDATE smart_notifications SET isRead = 1, readAtMillis = :readAt WHERE id = :id")
    suspend fun markAsRead(id: Long, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE smart_notifications SET isRead = 1, readAtMillis = :readAt WHERE targetRole = :role AND isRead = 0")
    suspend fun markAllAsReadForRole(role: String, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE smart_notifications SET isResolved = 1, isRead = 1, resolvedAtMillis = :resolvedAt, resolvedBy = :resolvedBy WHERE id = :id")
    suspend fun resolveNotification(id: Long, resolvedBy: String, resolvedAt: Long = System.currentTimeMillis())

    @Query("UPDATE smart_notifications SET isResolved = 1, isRead = 1, resolvedAtMillis = :resolvedAt, resolvedBy = :resolvedBy WHERE relatedFolio = :relatedFolio")
    suspend fun resolveNotificationsByFolio(relatedFolio: String, resolvedBy: String, resolvedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM smart_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM smart_notifications WHERE isResolved = 1 AND timestampMillis < :beforeMillis")
    suspend fun deleteOldResolved(beforeMillis: Long)

    @Query("SELECT * FROM smart_notifications WHERE id = :id")
    suspend fun getById(id: Long): SmartNotificationEntity?
}
