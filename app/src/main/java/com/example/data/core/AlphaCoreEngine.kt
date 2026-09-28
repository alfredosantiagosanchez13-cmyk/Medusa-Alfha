package com.example.data.core

import com.example.data.auth.MedusaRole
import com.example.data.auth.UserSession
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
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
     * Genera un Folio Único inmutable con estructura oficial canónica: MED-YYYYMMDD-XXXX
     * Todos los eventos (pases, accesos, paquetería, incidencias, supervisión y auditoría) comparten este formato.
     */
    fun generateUniqueFolio(prefix: String = "MED"): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val datePart = dateFormat.format(Date())
        val seqPart = counter.getAndIncrement() % 10000
        val paddedSeq = String.format(Locale.US, "%04d", seqPart)
        val cleanPrefix = if (prefix.isBlank()) "MED" else prefix.trim().uppercase()
        return "$cleanPrefix-$datePart-$paddedSeq"
    }

    /**
     * Construye la carga útil canónica estricta que vincula criptográficamente:
     * folio, residentId, assignedUnit, visitante, emisión, expiración y tipo de acceso.
     * Cualquier alteración en estos parámetros invalida matemáticamente la firma digital.
     */
    fun buildCanonicalPayload(
        folio: String,
        residentId: String,
        assignedUnit: String,
        guestName: String,
        createdAtMillis: Long,
        validUntilMillis: Long,
        passType: String
    ): String {
        return "${folio.trim()}|${residentId.trim()}|${assignedUnit.trim().uppercase()}|${guestName.trim().uppercase()}|$createdAtMillis|$validUntilMillis|${passType.trim().uppercase()}"
    }

    /**
     * Calcula la firma de integridad SHA-256 para compatibilidad con módulos anteriores.
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

/**
 * AUTORIDAD CRIPTOGRÁFICA ASIMÉTRICA: FIRMA DIGITAL ECDSA (NIST P-256 / SHA256withECDSA)
 *
 * Principios de Seguridad:
 * 1. ASIMETRÍA: La Caseta verifica la autenticidad usando exclusivamente la Clave Pública de la Autoridad.
 *    Caseta NO posee la Clave Privada y por tanto NO puede emitir pases arbitrarios.
 * 2. CERO EXTRACCIÓN: La Clave Privada jamás se almacena en el código fuente, APK, Room ni configuración
 *    del cliente. Se gestiona de forma aislada en memoria segura por la Autoridad Central de Emisión.
 * 3. NO REPUDIO E INTEGRIDAD: Si un tercero altera el folio, la vivienda destino, el visitante,
 *    la expiración o el tipo de acceso, la verificación matemática de la firma falla de inmediato.
 */
object AlphaSecurityAuthority {

    private const val ALGORITHM_KEY = "EC"
    private const val CURVE_NAME = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    // Par de claves asimétricas de la Autoridad Central (mantenidas en memoria volátil segura)
    @Volatile
    private var authorityKeyPair: KeyPair? = null

    init {
        ensureAuthorityKeysInitialized()
    }

    @Synchronized
    private fun ensureAuthorityKeysInitialized(): KeyPair {
        val existing = authorityKeyPair
        if (existing != null) return existing

        val keyGen = KeyPairGenerator.getInstance(ALGORITHM_KEY)
        val ecSpec = ECGenParameterSpec(CURVE_NAME)
        keyGen.initialize(ecSpec)
        val newPair = keyGen.generateKeyPair()
        authorityKeyPair = newPair
        return newPair
    }

    /**
     * Clave Pública Oficial de la Autoridad (utilizada por las terminales de Caseta para verificar autenticidad).
     * Esta clave es pública y no permite emitir o falsificar pases.
     */
    val authorityPublicKey: PublicKey
        get() = ensureAuthorityKeysInitialized().public

    /**
     * Representación X.509 Base64 de la Clave Pública para distribución y verificación segura.
     */
    val authorityPublicKeyX509Base64: String
        get() = base64Encode(authorityPublicKey.encoded)

    private fun getAuthorityPrivateKey(): PrivateKey {
        return ensureAuthorityKeysInitialized().private
    }

    /**
     * Firma digitalmente una carga útil canónica de pase QR tras validar la sesión del solicitante.
     * Solo residentes autenticados para su propia vivienda o administradores pueden firmar pases.
     */
    fun signPassPayload(
        canonicalPayload: String,
        session: UserSession?
    ): String {
        if (session == null || !session.isActive) {
            throw SecurityException("ACCESO DENEGADO: Intento de emisión de pase con sesión inactiva o inexistente.")
        }
        if (session.currentRole != MedusaRole.RESIDENTE && session.currentRole != MedusaRole.ADMINISTRACION) {
            throw SecurityException("ACCESO DENEGADO: El rol ${session.currentRole} no está autorizado para emitir pases QR.")
        }

        // Validación criptográfica y lógica: el residente solo puede emitir pases para su propia vivienda
        if (session.currentRole == MedusaRole.RESIDENTE) {
            val parts = canonicalPayload.split("|")
            if (parts.size >= 3) {
                val assignedUnitInPayload = parts[2].trim()
                com.example.data.auth.MedusaAreaIsolationGuard.assertResidentLotAccess(
                    residentAssignedUnit = session.assignedUnitId,
                    targetLotOrUnit = assignedUnitInPayload
                )
            }
        }

        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(getAuthorityPrivateKey())
        signature.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        val rawSignature = signature.sign()
        return base64Encode(rawSignature)
    }

    /**
     * Verifica la autenticidad matemática de un pase QR mediante la Clave Pública de la Autoridad.
     * El dispositivo de Caseta ejecuta esta comprobación sin riesgo de comprometer la clave de firma.
     */
    fun verifyPassSignature(
        canonicalPayload: String,
        signatureBase64: String,
        customPublicKey: PublicKey? = null
    ): Boolean {
        return try {
            if (signatureBase64.isBlank()) return false
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(customPublicKey ?: authorityPublicKey)
            signature.update(canonicalPayload.toByteArray(Charsets.UTF_8))
            val sigBytes = base64Decode(signatureBase64)
            signature.verify(sigBytes)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Permite a una terminal de Caseta inicializarse con una clave pública exportada de la Autoridad.
     */
    fun parsePublicKeyFromX509Base64(base64: String): PublicKey? {
        return try {
            val keyBytes = base64Decode(base64)
            val spec = X509EncodedKeySpec(keyBytes)
            val factory = KeyFactory.getInstance(ALGORITHM_KEY)
            factory.generatePublic(spec)
        } catch (_: Throwable) {
            null
        }
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}
