package com.example.data.validation

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FASE PILOTO CONTROLADO: ENTIDAD DE VALIDACIÓN DE CAMPO
 * Representa cada una de las 16 pruebas obligatorias del Checklist de Validación de Campo de MEDUSA ALFHA.
 * Todas las pruebas inician en estado PENDIENTE y se persisten en Room SQLite.
 */
@Entity(tableName = "field_validation_tests")
data class FieldValidationTestEntity(
    @PrimaryKey
    val testId: String,               // e.g. "CAS-01"
    val orderIndex: Int,              // 1 a 16
    val category: String,             // "CASETA", "PASE QR", "UBICACIÓN GPS", "OFFLINE / RECONEXIÓN", "TIEMPO DEVUELTO"
    val title: String,                // Título de la prueba
    val procedure: String,            // Procedimiento operativo en campo
    val acceptanceCriteria: String,   // Criterio de aceptación
    val evidenceRequired: String,     // Tipo de evidencia requerida
    val status: String = "PENDIENTE", // "PENDIENTE", "APROBADO", "FALLO"
    val evidenceReference: String = "",// Folio, Foto ID o texto de evidencia
    val observations: String = "",    // Notas del guardia o supervisor
    val updatedAtMillis: Long = System.currentTimeMillis()
)
