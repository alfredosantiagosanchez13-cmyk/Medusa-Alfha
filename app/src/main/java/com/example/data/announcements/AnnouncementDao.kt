package com.example.data.announcements

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO para Comunicados y Documentos Inteligentes (FASE 14).
 * Fuente Única de Verdad en SQLite Room.
 */
@Dao
interface AnnouncementDao {

    @Query("SELECT * FROM announcements ORDER BY timestampMillis DESC")
    fun getAllAnnouncements(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements ORDER BY timestampMillis DESC")
    suspend fun getAllAnnouncementsList(): List<AnnouncementEntity>

    @Query("SELECT * FROM announcements WHERE status = :status ORDER BY timestampMillis DESC")
    fun getAnnouncementsByStatus(status: String): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements WHERE folio = :folio LIMIT 1")
    suspend fun getAnnouncementByFolio(folio: String): AnnouncementEntity?

    @Query("SELECT * FROM announcements WHERE category = :category ORDER BY timestampMillis DESC")
    fun getAnnouncementsByCategory(category: AnnouncementCategory): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements WHERE targetScope = 'CONDOMINIO' OR targetUnits LIKE '%' || :unitId || '%' ORDER BY timestampMillis DESC")
    fun getAnnouncementsForUnit(unitId: String): Flow<List<AnnouncementEntity>>

    @Query("""
        SELECT * FROM announcements 
        WHERE folio LIKE '%' || :query || '%' 
           OR title LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%' 
           OR senderName LIKE '%' || :query || '%'
           OR targetUnits LIKE '%' || :query || '%'
        ORDER BY timestampMillis DESC
    """)
    fun searchAnnouncements(query: String): Flow<List<AnnouncementEntity>>

    @Query("SELECT COUNT(*) FROM announcements")
    suspend fun countTotal(): Int

    @Query("SELECT COUNT(*) FROM announcements WHERE priority = 'URGENTE' AND status = 'PUBLICADO'")
    suspend fun countUrgentActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: AnnouncementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncements(announcements: List<AnnouncementEntity>)

    @Update
    suspend fun updateAnnouncement(announcement: AnnouncementEntity)

    @Delete
    suspend fun deleteAnnouncement(announcement: AnnouncementEntity)

    @Query("DELETE FROM announcements WHERE folio = :folio")
    suspend fun deleteByFolio(folio: String)
}
