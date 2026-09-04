package com.example.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * ESQUEMA OFICIAL DE FIRESTORE PARA PERFILES DE USUARIO (USER PROFILES)
 *
 * Rutas Oficiales en Firestore:
 *   - Partición Multi-Inquilino: `/condominiums/{condominiumId}/users/{userId}`
 *   - Colección Canónica de Perfiles: `/condominiums/{condominiumId}/user_profiles/{userId}`
 *
 * Atributos Principales:
 *   - userId: UID único de Firebase Authentication
 *   - condominiumId: Identificador del condominio para aislamiento estricto (e.g., "PRADOS_1")
 *   - email: Correo electrónico corporativo o residencial
 *   - displayName: Nombre completo del usuario
 *   - role: Rol de seguridad RBAC ("RESIDENTE", "GUARDIA", "SUPERVISOR", "ADMINISTRADOR", "MESA_DIRECTIVA")
 *   - authorizedUnitNumber: Unidad habitacional asignada ("Casa 208", "Torre A - 302")
 *   - phoneNumber: Teléfono de contacto
 *   - photoUrl: Enlace a fotografía de perfil en Firebase Storage
 *   - occupancyType: Condición de habitabilidad ("PROPIETARIO", "ARRENDATARIO", "HABITANTE")
 *   - isActive: Bandera de habilitación en el sistema
 *   - emergencyContactName: Nombre de contacto de emergencia
 *   - emergencyContactPhone: Teléfono de contacto de emergencia
 *   - fcmToken: Token de Firebase Cloud Messaging para notificaciones push
 *   - createdAt: Timestamp nativo de creación en Firestore
 *   - updatedAt: Timestamp nativo de última modificación en Firestore
 *   - syncedAtMillis: Marca de tiempo numérica de sincronización
 */
data class FirestoreUserProfile(
    val userId: String,
    val condominiumId: String = "PRADOS_1",
    val email: String,
    val displayName: String,
    val role: String = "RESIDENTE",
    val authorizedUnitNumber: String = "",
    val phoneNumber: String = "",
    val photoUrl: String? = null,
    val occupancyType: String = "PROPIETARIO",
    val isActive: Boolean = true,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val fcmToken: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    val syncedAtMillis: Long = System.currentTimeMillis()
) {
    val unitNumber: String
        get() = authorizedUnitNumber

    /**
     * Serializa a Map para persistencia en Firestore.
     */
    fun toMap(): Map<String, Any?> = hashMapOf(
        "userId" to userId,
        "uid" to userId,
        "condominiumId" to condominiumId,
        "email" to email,
        "displayName" to displayName,
        "role" to role,
        "authorizedUnitNumber" to authorizedUnitNumber,
        "unitNumber" to authorizedUnitNumber,
        "unitId" to authorizedUnitNumber,
        "phoneNumber" to phoneNumber,
        "phone" to phoneNumber,
        "photoUrl" to (photoUrl ?: ""),
        "occupancyType" to occupancyType,
        "isActive" to isActive,
        "emergencyContactName" to (emergencyContactName ?: ""),
        "emergencyContactPhone" to (emergencyContactPhone ?: ""),
        "fcmToken" to (fcmToken ?: ""),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "syncedAtMillis" to syncedAtMillis
    )

    /**
     * Convierte hacia la entidad de Room (UserProfileEntity).
     */
    fun toRoomEntity(): UserProfileEntity {
        return UserProfileEntity(
            userId = userId,
            condominiumId = condominiumId,
            email = email,
            displayName = displayName,
            role = role,
            authorizedUnitNumber = authorizedUnitNumber,
            phoneNumber = phoneNumber,
            photoUrl = photoUrl,
            occupancyType = occupancyType,
            isActive = isActive,
            emergencyContactName = emergencyContactName,
            emergencyContactPhone = emergencyContactPhone,
            fcmToken = fcmToken,
            createdAtMillis = createdAt.toDate().time,
            updatedAtMillis = updatedAt.toDate().time
        )
    }

    companion object {
        /**
         * Crea un FirestoreUserProfile a partir de la entidad Room UserProfileEntity.
         */
        fun fromRoomEntity(entity: UserProfileEntity): FirestoreUserProfile {
            return FirestoreUserProfile(
                userId = entity.userId,
                condominiumId = entity.condominiumId,
                email = entity.email,
                displayName = entity.displayName,
                role = entity.role,
                authorizedUnitNumber = entity.authorizedUnitNumber,
                phoneNumber = entity.phoneNumber,
                photoUrl = entity.photoUrl,
                occupancyType = entity.occupancyType,
                isActive = entity.isActive,
                emergencyContactName = entity.emergencyContactName,
                emergencyContactPhone = entity.emergencyContactPhone,
                fcmToken = entity.fcmToken,
                createdAt = Timestamp(Date(entity.createdAtMillis)),
                updatedAt = Timestamp(Date(entity.updatedAtMillis)),
                syncedAtMillis = System.currentTimeMillis()
            )
        }

        /**
         * Deserializa un DocumentSnapshot de Firestore al modelo fuertemente tipado FirestoreUserProfile.
         */
        fun fromDocumentSnapshot(doc: DocumentSnapshot, defaultCondoId: String = "PRADOS_1"): FirestoreUserProfile? {
            return try {
                val userId = doc.getString("userId") ?: doc.getString("uid") ?: doc.id
                val email = doc.getString("email") ?: ""
                val displayName = doc.getString("displayName") ?: doc.getString("name") ?: "Usuario"
                val condoId = doc.getString("condominiumId") ?: defaultCondoId
                val role = doc.getString("role") ?: "RESIDENTE"
                val unitNumber = doc.getString("authorizedUnitNumber")
                    ?: doc.getString("unitNumber")
                    ?: doc.getString("unitId")
                    ?: ""
                val phone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: ""
                val photo = doc.getString("photoUrl")
                val occType = doc.getString("occupancyType") ?: "PROPIETARIO"
                val active = doc.getBoolean("isActive") ?: true
                val emerName = doc.getString("emergencyContactName")
                val emerPhone = doc.getString("emergencyContactPhone")
                val token = doc.getString("fcmToken")

                val createdTs = doc.getTimestamp("createdAt") ?: Timestamp.now()
                val updatedTs = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                val syncedAt = doc.getLong("syncedAtMillis") ?: System.currentTimeMillis()

                FirestoreUserProfile(
                    userId = userId,
                    condominiumId = condoId,
                    email = email,
                    displayName = displayName,
                    role = role,
                    authorizedUnitNumber = unitNumber,
                    phoneNumber = phone,
                    photoUrl = photo,
                    occupancyType = occType,
                    isActive = active,
                    emergencyContactName = emerName,
                    emergencyContactPhone = emerPhone,
                    fcmToken = token,
                    createdAt = createdTs,
                    updatedAt = updatedTs,
                    syncedAtMillis = syncedAt
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
