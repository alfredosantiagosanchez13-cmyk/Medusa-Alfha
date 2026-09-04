package com.example.data.booking

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.alerts.OperationalAlertDao
import com.example.data.alerts.OperationalAlertEntity
import com.example.data.audit.AuditLogDao
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.AlfhaUserDao
import com.example.data.auth.AlfhaUserEntity
import com.example.data.chat.AiGuardChatLog
import com.example.data.chat.AiGuardChatLogDao
import com.example.data.incident.IncidentDao
import com.example.data.incident.IncidentEntity
import com.example.data.notifications.SmartNotificationDao
import com.example.data.notifications.SmartNotificationEntity
import com.example.data.packages.PackageDao
import com.example.data.packages.PackageEntity
import com.example.data.maintenance.MaintenanceDao
import com.example.data.maintenance.MaintenanceOrderEntity
import com.example.data.passes.QrPassDao
import com.example.data.passes.QrPassRoomEntity
import com.example.data.resident.ResidentDao
import com.example.data.resident.ResidentEntity
import com.example.data.resident.UnitDao
import com.example.data.resident.UnitEntity
import com.example.data.supervision.SupervisionAuditDao
import com.example.data.announcements.AnnouncementDao
import com.example.data.announcements.AnnouncementEntity
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.validation.FieldValidationDao
import com.example.data.validation.FieldValidationTestEntity
import com.example.data.vehicle.VehicleAccessLogEntity
import com.example.data.vehicle.VehicleDao
import com.example.data.sync.SyncQueueDao
import com.example.data.sync.SyncQueueEntity
import com.example.data.vehicle.VehicleEntity
import com.example.data.profile.UserProfileDao
import com.example.data.profile.UserProfileEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInDao

@Database(
    entities = [
        AmenityBooking::class,
        VisitorCheckIn::class,
        UserProfileEntity::class,
        AiGuardChatLog::class,
        QrPassRoomEntity::class,
        IncidentEntity::class,
        SupervisionAuditEntity::class,
        AuditLogEntity::class,
        OperationalAlertEntity::class,
        AlfhaUserEntity::class,
        SmartNotificationEntity::class,
        PackageEntity::class,
        ResidentEntity::class,
        UnitEntity::class,
        MaintenanceOrderEntity::class,
        AnnouncementEntity::class,
        VehicleEntity::class,
        VehicleAccessLogEntity::class,
        SyncQueueEntity::class,
        FieldValidationTestEntity::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun amenityBookingDao(): AmenityBookingDao
    abstract fun visitorCheckInDao(): VisitorCheckInDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun aiGuardChatLogDao(): AiGuardChatLogDao
    abstract fun qrPassDao(): QrPassDao
    abstract fun incidentDao(): IncidentDao
    abstract fun supervisionAuditDao(): SupervisionAuditDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun operationalAlertDao(): OperationalAlertDao
    abstract fun alfhaUserDao(): AlfhaUserDao
    abstract fun smartNotificationDao(): SmartNotificationDao
    abstract fun packageDao(): PackageDao
    abstract fun residentDao(): ResidentDao
    abstract fun unitDao(): UnitDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun fieldValidationDao(): FieldValidationDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "medusa_security_database"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error initializing persistent database, falling back to in-memory: ${e.message}", e)
                    try {
                        Room.inMemoryDatabaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                    } catch (fallbackEx: Exception) {
                        android.util.Log.e(TAG, "Critical failure initializing fallback database: ${fallbackEx.message}", fallbackEx)
                        throw fallbackEx
                    }
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
