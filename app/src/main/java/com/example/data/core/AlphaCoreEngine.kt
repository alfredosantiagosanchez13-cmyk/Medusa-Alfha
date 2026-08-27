package com.example.data.core

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Motor de Trazabilidad, Generación de Folios y Hashing de Integridad para MEDUSA ALFHA.
 * Principio Rector: "ESTO DEVUELVE TIEMPO." (TIEMPO = FAMILIA)
 */
object AlphaCoreEngine {

    private val counter = AtomicInteger(1001)

    /**
     * Genera un Folio Único inmutable con estructura oficial: MED-YYYYMMDD-XXXX
     */
    fun generateUniqueFolio(prefix: String = "MED"): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val datePart = dateFormat.format(Date())
        val seqPart = counter.getAndIncrement() % 10000
        val paddedSeq = String.format(Locale.US, "%04d", seqPart)
        return "$prefix-$datePart-$paddedSeq"
    }

    /**
     * Calcula la firma de integridad SHA-256 para códigos de pase QR y validación de seguridad.
     */
    fun computeIntegrityHash(passCode: String, guestDocument: String, destinationHouse: String): String {
        val payload = "$passCode|$guestDocument|$destinationHouse|MEDUSA_ALFHA_SALT_2026"
        val bytes = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
    }

    /**
     * Calcula el tiempo de permanencia formateado entre dos timestamps en milisegundos.
     */
    fun calculateDurationFormatted(startMillis: Long, endMillis: Long): String {
        if (endMillis <= startMillis) return "0 min"
        val diffMinutes = (endMillis - startMillis) / (60 * 1000)
        val hours = diffMinutes / 60
        val mins = diffMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins} min"
        }
    }
}
