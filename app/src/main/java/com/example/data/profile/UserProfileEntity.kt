package com.example.data.profile

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Perfil de Usuario en Room SQLite.
 *
 * Almacena el perfil local del usuario autenticado y usuarios del condominio,
 * sincronizable bidireccionalmente con Firestore.
 */
@Entity(
    tableName = "user_profiles",
    indices = [
        Index(value = ["condominiumId"]),
        Index(value = ["email"], unique = true),
        Index(value = ["role"]),
        Index(value = ["authorizedUnitNumber"]),
        Index(value = ["isActive"])
    ]
)
data class UserProfileEntity(
    @PrimaryKey
    val userId: String, // Firebase Auth UID o identificador único
    val condominiumId: String = "PRADOS_1", // Aislamiento multi-inquilino
    val email: String,
    val displayName: String,
    val role: String = "RESIDENTE", // RESIDENTE, GUARDIA, SUPERVISOR, ADMINISTRADOR, MESA_DIRECTIVA, MAESTRO_ALFHA
    val authorizedUnitNumber: String = "", // e.g., "Casa 208", "Torre A - 302"
    val phoneNumber: String = "",
    val photoUrl: String? = null,
    val occupancyType: String = "PROPIETARIO", // PROPIETARIO, ARRENDATARIO, FAMILIAR, HABITANTE
    val isActive: Boolean = true,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val fcmToken: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
