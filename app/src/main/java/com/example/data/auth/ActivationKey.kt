package com.example.data.auth

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Entidad de Firestore para Llaves de Activación en MEDUSA ALFHA.
 * Ruta canónica en Firestore: `/activation_keys/$inputKey`
 *
 * Contiene los metadatos necesarios para vincular el dispositivo al condominio,
 * asignar el rol RBAC correspondiente y delimitar su alcance operativo.
 */
@IgnoreExtraProperties
data class ActivationKey(
    @get:PropertyName("keyId")
    @set:PropertyName("keyId")
    var keyId: String = "",

    @get:PropertyName("role")
    @set:PropertyName("role")
    var role: String = "",

    @get:PropertyName("condominiumId")
    @set:PropertyName("condominiumId")
    var condominiumId: String = "",

    @get:PropertyName("assignedUnit")
    @set:PropertyName("assignedUnit")
    var assignedUnit: String? = null,

    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = false,

    // Metadatos complementarios para auditoría, vigencia y trazabilidad forense
    @get:PropertyName("expirationTimestampMillis")
    @set:PropertyName("expirationTimestampMillis")
    var expirationTimestampMillis: Long? = null,

    @get:PropertyName("activatedAtMillis")
    @set:PropertyName("activatedAtMillis")
    var activatedAtMillis: Long? = null,

    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var notes: String? = null
) {
    /**
     * Mapea la cadena textual del rol al Enum fuertemente tipado MedusaRole.
     */
    val medusaRole: MedusaRole
        get() = MedusaRole.fromString(role)

    /**
     * Determina si la llave ha expirado por tiempo si tiene fecha de vencimiento configurada.
     */
    fun isExpired(): Boolean {
        val exp = expirationTimestampMillis ?: return false
        return exp > 0 && System.currentTimeMillis() > exp
    }

    /**
     * Convierte el modelo a un mapa estructurado para operaciones de persistencia en Firestore.
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "keyId" to keyId,
            "role" to role,
            "condominiumId" to condominiumId,
            "assignedUnit" to assignedUnit,
            "isActive" to isActive,
            "expirationTimestampMillis" to expirationTimestampMillis,
            "activatedAtMillis" to activatedAtMillis,
            "notes" to notes
        )
    }

    companion object {
        /**
         * Deserializa un documento de Firestore de forma segura y tolerante a fallos de esquema.
         */
        fun fromSnapshot(snapshot: DocumentSnapshot): ActivationKey? {
            if (!snapshot.exists()) return null
            return try {
                val direct = snapshot.toObject(ActivationKey::class.java)
                if (direct != null) {
                    if (direct.keyId.isBlank()) direct.keyId = snapshot.id
                    direct
                } else {
                    fallbackMapping(snapshot)
                }
            } catch (_: Exception) {
                fallbackMapping(snapshot)
            }
        }

        private fun fallbackMapping(snapshot: DocumentSnapshot): ActivationKey {
            return ActivationKey(
                keyId = snapshot.getString("keyId") ?: snapshot.id,
                role = snapshot.getString("role") ?: "",
                condominiumId = snapshot.getString("condominiumId") ?: "",
                assignedUnit = snapshot.getString("assignedUnit"),
                isActive = snapshot.getBoolean("isActive") ?: (snapshot.get("isActive") == true),
                expirationTimestampMillis = snapshot.getLong("expirationTimestampMillis"),
                activatedAtMillis = snapshot.getLong("activatedAtMillis"),
                notes = snapshot.getString("notes")
            )
        }
    }
}
