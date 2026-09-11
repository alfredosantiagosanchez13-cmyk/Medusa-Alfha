package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.PassType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Modelo representativo de un Código de Acceso Temporal para Visitantes
 * creado por un residente y respaldado tanto en Room como en Firebase Firestore.
 */
data class TemporaryAccessPass(
    val passCode: String,
    val folio: String,
    val visitorName: String,
    val visitorDocument: String,
    val destinationUnit: String,
    val hostResidentName: String,
    val passType: PassType,
    val vehiclePlate: String? = null,
    val durationHours: Int,
    val issuedAtMillis: Long = System.currentTimeMillis(),
    val validUntilMillis: Long,
    val maxEntries: Int = 1,
    val currentEntriesCount: Int = 0,
    val notes: String? = null,
    val integrityHash: String,
    val isActive: Boolean = true,
    val firestoreDocumentPath: String,
    val savedToFirestore: Boolean = false,
    val syncMessage: String = ""
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > validUntilMillis

    val isExhausted: Boolean
        get() = currentEntriesCount >= maxEntries

    val isValidForAccess: Boolean
        get() = isActive && !isExpired && !isExhausted

    val remainingMillis: Long
        get() = (validUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    val remainingFormatted: String
        get() {
            if (isExpired) return "Expirado"
            val totalSecs = remainingMillis / 1000
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            return when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m restantes"
                hours > 0 -> "${hours}h restantes"
                else -> "${mins}m restantes"
            }
        }

    val formattedValidUntil: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(validUntilMillis))

    val formattedIssuedAt: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(issuedAtMillis))

    /**
     * Payload JSON estructurado compatible con los escáneres de seguridad y caseta.
     */
    val qrPayloadJson: String
        get() = """{"passCode":"$passCode","folio":"$folio","visitor":"$visitorName","unit":"$destinationUnit","host":"$hostResidentName","validUntil":$validUntilMillis,"maxEntries":$maxEntries,"hash":"$integrityHash"}"""
}

/**
 * UTILIDAD DE GENERACIÓN DE CÓDIGOS QR Y ACCESOS TEMPORALES PARA RESIDENTES.
 *
 * Permite a los residentes crear códigos de acceso y códigos QR temporales para visitantes,
 * garantizando el guardado directo en Cloud Firestore con partición multi-inquilino
 * (/condominiums/{condominiumId}/qr_passes/{passCode}) y respaldo local en Room SQLite.
 */
object ResidentQrCodeUtility {

    private const val TAG = "ResidentQrCodeUtility"

    /**
     * Crea un nuevo código de acceso temporal para un visitante y lo persiste
     * tanto en la base de datos Room local como en Firebase Firestore.
     */
    suspend fun createTemporaryAccessPass(
        context: Context,
        db: AppDatabase,
        condominiumId: String,
        visitorName: String,
        visitorDocument: String,
        destinationUnit: String,
        hostResidentName: String,
        passType: PassType = PassType.VISITOR_SINGLE,
        vehiclePlate: String? = null,
        durationHours: Int = 4,
        maxEntries: Int = 1,
        notes: String? = null,
        residentUid: String? = null,
        documentPhotoUri: String? = null
    ): TemporaryAccessPass = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val folio = AlphaCoreEngine.generateUniqueFolio("MED")
        val passCode = folio
        val validUntilMillis = now + (durationHours.toLong() * 3600 * 1000L)
        val cleanDoc = visitorDocument.trim().ifBlank { "Verificar en Caseta" }
        val cleanUnit = destinationUnit.trim().ifBlank { "Casa General" }
        val cleanHost = hostResidentName.trim().ifBlank { "Residente Anfitrión" }
        val cleanVisitor = visitorName.trim().ifBlank { "Visitante Invitado" }
        val cleanPlate = vehiclePlate?.trim()?.takeIf { it.isNotBlank() }
        val cleanNotes = notes?.trim()?.takeIf { it.isNotBlank() }

        val integrityHash = AlphaCoreEngine.computeIntegrityHash(passCode, cleanDoc, cleanUnit)
        val firestorePath = "/condominiums/$condominiumId/qr_passes/$passCode"

        // 1. Guardar en Room SQLite local (qr_passes)
        val roomEntity = QrPassRoomEntity(
            passCode = passCode,
            guestName = cleanVisitor,
            guestDocument = cleanDoc,
            destinationHouse = cleanUnit,
            hostResidentName = cleanHost,
            vehiclePlate = cleanPlate,
            passType = passType,
            validUntilMillis = validUntilMillis,
            maxEntries = maxEntries,
            currentEntriesCount = 0,
            note = "Acceso Temporal (${durationHours}h). ${cleanNotes ?: ""}".trim(),
            createdAtMillis = now,
            integrityHash = integrityHash,
            isActive = true
        )
        db.qrPassDao().insertPass(roomEntity)

        // 2. Guardar en VisitorCheckIn para bitácora local
        val expiryStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(validUntilMillis))
        val checkIn = VisitorCheckIn(
            id = 0,
            folio = folio,
            visitorName = cleanVisitor,
            visitorDocument = cleanDoc,
            destinationHouse = cleanUnit,
            passCode = passCode,
            passTypeLabel = "${passType.label} (Temporal ${durationHours}h)",
            vehiclePlate = cleanPlate,
            status = "PRE_REGISTRADO",
            timestampMillis = now,
            guardNotes = "Pase QR emitido por residente. Vence: $expiryStr • Máx: $maxEntries entradas",
            residentNotes = cleanNotes,
            hostResidentName = cleanHost,
            photoPath = documentPhotoUri
        )
        val insertedId = db.visitorCheckInDao().insertCheckIn(checkIn)
        val finalCheckIn = checkIn.copy(id = insertedId)

        // 3. Guardar en Firebase Firestore en subcolección multi-tenant aislada
        var savedToFirestore = false
        var syncMessage = "Guardado localmente en Room SQLite"

        try {
            val firestore = FirebaseFirestore.getInstance()
            // Sincronizar en qr_passes
            val passResult = FirestoreTenantManager.saveQrPass(
                firestore = firestore,
                condominiumId = condominiumId,
                pass = roomEntity,
                userId = residentUid
            )

            // Sincronizar también en visitor_logs y visitor_access para caseta
            val visitorLog = FirestoreVisitorLog.fromVisitorCheckIn(finalCheckIn, condominiumId)
            FirestoreTenantManager.saveVisitorLog(firestore, condominiumId, visitorLog)
            FirestoreTenantManager.saveVisitorCheckIn(firestore, condominiumId, finalCheckIn)

            if (passResult.isSuccess) {
                savedToFirestore = true
                syncMessage = "Guardado exitosamente en Firebase Firestore"
                Log.i(TAG, "Pase $passCode guardado en Firestore: $firestorePath")
            } else {
                syncMessage = "Guardado en Room (Sync pendiente: ${passResult.exceptionOrNull()?.message})"
                Log.w(TAG, "Aviso sync Firestore: ${passResult.exceptionOrNull()?.message}")
            }
        } catch (fe: Throwable) {
            Log.d(TAG, "Operando en modo local seguro: ${fe.message}")
            syncMessage = "Guardado localmente (Offline / Room activo)"
        }

        TemporaryAccessPass(
            passCode = passCode,
            folio = folio,
            visitorName = cleanVisitor,
            visitorDocument = cleanDoc,
            destinationUnit = cleanUnit,
            hostResidentName = cleanHost,
            passType = passType,
            vehiclePlate = cleanPlate,
            durationHours = durationHours,
            issuedAtMillis = now,
            validUntilMillis = validUntilMillis,
            maxEntries = maxEntries,
            currentEntriesCount = 0,
            notes = cleanNotes,
            integrityHash = integrityHash,
            isActive = true,
            firestoreDocumentPath = firestorePath,
            savedToFirestore = savedToFirestore,
            syncMessage = syncMessage
        )
    }

    /**
     * Genera un código QR como mapa de bits (Bitmap) de alta fidelidad.
     */
    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(
                        x,
                        y,
                        if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Error generando Bitmap QR: ${e.message}")
            null
        }
    }

    /**
     * Revoca un pase temporal en Room y en Firebase Firestore.
     */
    suspend fun revokeAccessPass(
        context: Context,
        db: AppDatabase,
        condominiumId: String,
        passCode: String,
        reason: String = "Revocado por el residente"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Desactivar en Room
            db.qrPassDao().deactivatePass(passCode)

            // Desactivar en Firestore
            try {
                val firestore = FirebaseFirestore.getInstance()
                FirestoreTenantManager.revokeQrPass(firestore, condominiumId, passCode, reason)
            } catch (fe: Throwable) {
                Log.d(TAG, "Firestore sync de revocación pendiente: ${fe.message}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error revocando pase $passCode: ${e.message}")
            false
        }
    }

    /**
     * Comparte el pase temporal por WhatsApp, SMS u otras aplicaciones usando el Share Sheet de Android.
     */
    fun shareAccessPass(
        context: Context,
        pass: TemporaryAccessPass,
        condominiumName: String = "Residencial Los Prados"
    ) {
        val shareMessage = buildString {
            appendLine("🔑 *INVITACIÓN DE ACCESO TEMPORAL*")
            appendLine("🏢 *Condominio:* $condominiumName")
            appendLine("👤 *Visitante:* ${pass.visitorName}")
            appendLine("🏠 *Destino:* ${pass.destinationUnit}")
            appendLine("🙋 *Anfitrión:* ${pass.hostResidentName}")
            appendLine("🏷️ *Tipo de Acceso:* ${pass.passType.label}")
            if (!pass.vehiclePlate.isNullOrBlank()) {
                appendLine("🚗 *Placas Autorizadas:* ${pass.vehiclePlate}")
            }
            appendLine()
            appendLine("🎟️ *Código de Acceso:* ${pass.passCode}")
            appendLine("⏳ *Válido hasta:* ${pass.formattedValidUntil} (${pass.durationHours} hrs)")
            appendLine("🔢 *Entradas permitidas:* ${if (pass.maxEntries == 1) "1 (Uso único)" else "${pass.maxEntries} entradas"}")
            if (!pass.notes.isNullOrBlank()) {
                appendLine("📝 *Indicaciones:* ${pass.notes}")
            }
            appendLine()
            appendLine("📱 _Muestra este código o tu código QR en la caseta de vigilancia para validar tu ingreso inmediato._")
            appendLine("🛡️ Sistema de Control de Acceso MEDUSA ALFHA")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            putExtra(Intent.EXTRA_SUBJECT, "Pase de Acceso para ${pass.visitorName} - ${pass.destinationUnit}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartir Pase Temporal con Visitante")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    /**
     * Copia el código al portapapeles del dispositivo.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Código de Acceso") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copiado al portapapeles: $text", Toast.LENGTH_SHORT).show()
    }

    /**
     * Construye un payload JSON estructurado para credencial QR de residente con soporte touchless.
     */
    fun generateResidentTouchlessPayload(
        passCode: String,
        residentName: String,
        unitId: String,
        vehiclePlate: String? = null,
        condominiumId: String = "PARAISO"
    ): String {
        val plateField = if (!vehiclePlate.isNullOrBlank()) ""","plate":"$vehiclePlate"""" else ""
        return """{"passCode":"$passCode","type":"RESIDENT","residentName":"$residentName","unitId":"$unitId","condo":"$condominiumId"$plateField,"touchless":true,"timestamp":${System.currentTimeMillis()}}"""
    }

    /**
     * Asegura que exista el pase de residente en Room para validación offline instantánea.
     */
    suspend fun registerResidentTouchlessCredential(
        db: AppDatabase,
        passCode: String,
        residentName: String,
        unitId: String,
        vehiclePlate: String? = null,
        condominiumId: String = "PARAISO"
    ): QrPassRoomEntity = withContext(Dispatchers.IO) {
        val existing = db.qrPassDao().getPassByCode(passCode)
        if (existing != null) return@withContext existing

        val entity = QrPassRoomEntity(
            passCode = passCode,
            guestName = residentName,
            guestDocument = "Credencial Touchless Residente",
            destinationHouse = unitId,
            hostResidentName = residentName,
            vehiclePlate = vehiclePlate,
            passType = PassType.RESIDENT_PERMANENT,
            validUntilMillis = System.currentTimeMillis() + (365L * 86400 * 1000),
            maxEntries = 99999,
            currentEntriesCount = 0,
            note = "Credencial Touchless de Residente registrada para control de acceso sin contacto"
        )
        db.qrPassDao().insertPass(entity)
        entity
    }
}
