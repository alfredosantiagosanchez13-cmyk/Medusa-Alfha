package com.example.data.resident

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entidad de Residente en Room SQLite.
 * Fuente Única de Verdad para el Directorio de Residentes y Domicilios.
 */
@Entity(
    tableName = "residents",
    indices = [
        Index(value = ["unitId"]),
        Index(value = ["email"]),
        Index(value = ["phone"]),
        Index(value = ["status"]),
        Index(value = ["isDeleted"])
    ]
)
data class ResidentEntity(
    @PrimaryKey
    val id: String, // e.g. "RES-2026-A104"
    val unitId: String, // e.g. "Casa 104", "Torre A - Depto 302"
    val fullName: String,
    val occupancyType: String = "PROPIETARIO", // PROPIETARIO, ARRENDATARIO, FAMILIAR, HABITANTE
    val phone: String = "",
    val email: String = "",
    val status: String = "ACTIVO", // ACTIVO, INACTIVO, SUSPENDIDO, BAJA_LOGICA, EN_MUDANZA
    val vehiclesJson: String = "[]",
    val authorizedPersonsJson: String = "[]",
    val emergencyContactsJson: String = "[]",
    val notes: String = "",
    val linkedUserId: String = "", // Vínculo opcional con AlfhaUserEntity
    val isDeleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val updatedBy: String = "ADMINISTRACION"
) {
    fun parseVehicles(): List<ResidentVehicle> {
        return try {
            if (vehiclesJson.isBlank()) return emptyList()
            val array = JSONArray(vehiclesJson)
            val list = mutableListOf<ResidentVehicle>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ResidentVehicle(
                        plates = obj.optString("plates", ""),
                        brand = obj.optString("brand", ""),
                        model = obj.optString("model", ""),
                        color = obj.optString("color", ""),
                        tagRfid = obj.optString("tagRfid", ""),
                        isPrimary = obj.optBoolean("isPrimary", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseAuthorizedPersons(): List<AuthorizedPerson> {
        return try {
            if (authorizedPersonsJson.isBlank()) return emptyList()
            val array = JSONArray(authorizedPersonsJson)
            val list = mutableListOf<AuthorizedPerson>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AuthorizedPerson(
                        name = obj.optString("name", ""),
                        relation = obj.optString("relation", "Visita Frecuente"),
                        idDocument = obj.optString("idDocument", ""),
                        phone = obj.optString("phone", ""),
                        canAuthorizeVisits = obj.optBoolean("canAuthorizeVisits", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseEmergencyContacts(): List<EmergencyContact> {
        return try {
            if (emergencyContactsJson.isBlank()) return emptyList()
            val array = JSONArray(emergencyContactsJson)
            val list = mutableListOf<EmergencyContact>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    EmergencyContact(
                        name = obj.optString("name", ""),
                        relation = obj.optString("relation", "Familiar"),
                        phone = obj.optString("phone", ""),
                        isPrimary = obj.optBoolean("isPrimary", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun encodeVehicles(vehicles: List<ResidentVehicle>): String {
            val array = JSONArray()
            vehicles.forEach { v ->
                val obj = JSONObject()
                obj.put("plates", v.plates)
                obj.put("brand", v.brand)
                obj.put("model", v.model)
                obj.put("color", v.color)
                obj.put("tagRfid", v.tagRfid)
                obj.put("isPrimary", v.isPrimary)
                array.put(obj)
            }
            return array.toString()
        }

        fun encodeAuthorizedPersons(persons: List<AuthorizedPerson>): String {
            val array = JSONArray()
            persons.forEach { p ->
                val obj = JSONObject()
                obj.put("name", p.name)
                obj.put("relation", p.relation)
                obj.put("idDocument", p.idDocument)
                obj.put("phone", p.phone)
                obj.put("canAuthorizeVisits", p.canAuthorizeVisits)
                array.put(obj)
            }
            return array.toString()
        }

        fun encodeEmergencyContacts(contacts: List<EmergencyContact>): String {
            val array = JSONArray()
            contacts.forEach { c ->
                val obj = JSONObject()
                obj.put("name", c.name)
                obj.put("relation", c.relation)
                obj.put("phone", c.phone)
                obj.put("isPrimary", c.isPrimary)
                array.put(obj)
            }
            return array.toString()
        }
    }
}

data class ResidentVehicle(
    val plates: String,
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val tagRfid: String = "",
    val isPrimary: Boolean = false
)

data class AuthorizedPerson(
    val name: String,
    val relation: String = "Visita Frecuente", // Familiar, Empleado Doméstico, Visita Frecuente, Tutor
    val idDocument: String = "",
    val phone: String = "",
    val canAuthorizeVisits: Boolean = false
)

data class EmergencyContact(
    val name: String,
    val relation: String = "Familiar", // Cónyuge, Padre/Madre, Hermano, Vecino, Médico
    val phone: String,
    val isPrimary: Boolean = false
)
