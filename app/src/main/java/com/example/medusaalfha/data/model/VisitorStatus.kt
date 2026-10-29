package com.example.medusaalfha.data.model

import androidx.annotation.Keep
import java.util.Locale

@Keep
enum class VisitorStatus(val label: String) {
    DENTRO("En Condominio"),
    SALIDA("Salida Registrada"),
    AUTORIZADO("Pase Aprobado"),
    DENEGADO("Acceso Denegado");

    companion object {
        fun fromString(value: String): VisitorStatus {
            return when (value.uppercase(Locale.ROOT)) {
                "DENTRO", "INGRESADO", "ACTIVO", "EN_SITIO" -> DENTRO
                "SALIDA", "FINALIZADO", "COMPLETADO" -> SALIDA
                "AUTORIZADO", "APROBADO", "VALIDO" -> AUTORIZADO
                "DENEGADO", "RECHAZADO", "BLOQUEADO" -> DENEGADO
                else -> DENTRO
            }
        }
    }
}
