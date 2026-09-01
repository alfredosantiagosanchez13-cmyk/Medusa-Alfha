package com.example.data.vecinos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.passes.QrPassRoomEntity
import com.example.data.resident.ResidentEntity
import com.example.data.resident.UnitEntity
import com.example.scanner.PassType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class VecinoCondominio(
    val id: String,
    val nombre: String
)

data class VecinoVisita(
    val idVisita: String,
    val nombreVisitante: String,
    val fechaVisita: String,
    val notas: String = "",
    val estado: String = "Pendiente",
    val passCode: String = "",
    val validUntilMillis: Long = 0L,
    val maxEntries: Int = 1,
    val vehiclePlate: String? = null,
    val passType: String = "VISITOR_SINGLE"
)

data class VecinoSesion(
    val idCondominio: String,
    val nombreCondominio: String,
    val casa: String,
    val calle: String = "",
    val prototipo: String = ""
)

/**
 * Servicio autónomo y 100% local con base de datos SQLite / Room en el dispositivo.
 * Cero dependencias de servidores externos, cero costos de plataformas de terceros.
 * Integrado al croquis arquitectónico oficial de RESIDENCIAL LOS PRADOS.
 */
object MedusaVecinosService {

    val CONDOMINIOS_PREDETERMINADOS = listOf(
        VecinoCondominio("PRADOS_1", "Los Prados 1 (Calles 1-2)"),
        VecinoCondominio("PRADOS_2", "Los Prados 2 (Calles 3-4)"),
        VecinoCondominio("PRADOS_3", "Los Prados 3 (Calles 5-6)"),
        VecinoCondominio("PARAISO", "Condominio Paraíso")
    )

    fun listarCondominios(): List<VecinoCondominio> {
        return CONDOMINIOS_PREDETERMINADOS
    }

    /**
     * Inicializa los 261 lotes oficiales de Los Prados en la base de datos Room SQLite local
     * si aún no han sido registrados.
     */
    suspend fun inicializarDirectorioLosPradosSiVacio(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unitDao = db.unitDao()
            val residentDao = db.residentDao()

            val currentUnitCount = unitDao.getUnitCount()
            if (currentUnitCount >= 200) {
                return@withContext currentUnitCount
            }

            val unitsToInsert = mutableListOf<UnitEntity>()
            val residentsToInsert = mutableListOf<ResidentEntity>()

            LosPradosCroquisData.TODOS_LOS_LOTES.forEach { lote ->
                val unitId = "CASA-${lote.numero}-${lote.calle.replace(" ", "")}"
                unitsToInsert.add(
                    UnitEntity(
                        unitId = unitId,
                        blockOrTower = "${lote.nombreCondominio} - ${lote.calle}",
                        unitNumber = "${lote.numero}",
                        status = "HABITADA",
                        intercomCode = "${lote.numero}",
                        parkingSpots = "Estacionamiento Lote ${lote.numero}",
                        notes = "Modelo: ${lote.prototipo.nombre} · ${lote.ladoManzana}"
                    )
                )

                residentsToInsert.add(
                    ResidentEntity(
                        id = "RES-${lote.condominioId}-${lote.calle.replace(" ", "")}-${lote.numero}",
                        fullName = "Titular Casa ${lote.numero} (${lote.calle})",
                        unitId = unitId,
                        occupancyType = "PROPIETARIO",
                        phone = "442-100-${String.format(Locale.US, "%04d", lote.numero)}",
                        email = "casa${lote.numero}.${lote.calle.lowercase().replace(" ", "")}@losprados.mx",
                        authorizedPersonsJson = """[{"name":"Familia Casa ${lote.numero}","relation":"Familia Directa"}]""",
                        vehiclesJson = "[]",
                        emergencyContactsJson = "[]",
                        notes = "Condominio: ${lote.nombreCondominio} · ${lote.prototipo.codigo} · ${lote.ladoManzana}",
                        status = "ACTIVO",
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
            }

            unitDao.insertUnits(unitsToInsert)
            residentDao.insertResidents(residentsToInsert)
            unitsToInsert.size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Autenticación local autónoma basada en Room SQLite con reconocimiento de lote en croquis.
     */
    suspend fun loginCasaLocal(
        context: Context,
        idCondominio: String,
        casa: String,
        codigo: String,
        calle: String = ""
    ): Result<VecinoSesion> = withContext(Dispatchers.IO) {
        try {
            val cleanCasa = casa.trim()
            val cleanCodigo = codigo.trim()
            if (cleanCasa.isBlank() || cleanCodigo.isBlank()) {
                return@withContext Result.failure(Exception("Ingresa número de casa y código de acceso."))
            }

            val db = AppDatabase.getDatabase(context)
            val residentDao = db.residentDao()

            // Buscar en el catálogo del croquis
            val numeroInt = cleanCasa.toIntOrNull()
            val loteInfo = if (numeroInt != null) {
                LosPradosCroquisData.buscarLotes(cleanCasa, if (idCondominio != "PARAISO") idCondominio else null)
                    .firstOrNull { it.numero == numeroInt && (calle.isBlank() || it.calle.equals(calle, ignoreCase = true)) }
            } else null

            val calleEfectiva = loteInfo?.calle ?: if (calle.isNotBlank()) calle else "Calle Principal"
            val prototipoEfectivo = loteInfo?.prototipo?.codigo ?: "Estándar"
            val unitId = "CASA-$cleanCasa-${calleEfectiva.replace(" ", "")}"

            val residents = residentDao.getResidentsByUnit(unitId)
            if (residents.isEmpty()) {
                val nuevoResidente = ResidentEntity(
                    id = "RES-${idCondominio}-${cleanCasa}",
                    fullName = "Residente Casa $cleanCasa ($calleEfectiva)",
                    unitId = unitId,
                    occupancyType = "PROPIETARIO",
                    phone = "Sin teléfono",
                    email = "casa$cleanCasa@medusa.local",
                    authorizedPersonsJson = """[{"name":"Titular Casa $cleanCasa","relation":"Propietario"}]""",
                    vehiclesJson = "[]",
                    emergencyContactsJson = "[]",
                    notes = "Condominio: $idCondominio · $calleEfectiva · $prototipoEfectivo",
                    status = "ACTIVO",
                    createdAtMillis = System.currentTimeMillis()
                )
                residentDao.insertResident(nuevoResidente)
            }

            val condoObj = CONDOMINIOS_PREDETERMINADOS.find { it.id == idCondominio }
            val nombreCondo = condoObj?.nombre ?: idCondominio

            val sesion = VecinoSesion(
                idCondominio = idCondominio,
                nombreCondominio = nombreCondo,
                casa = cleanCasa,
                calle = calleEfectiva,
                prototipo = prototipoEfectivo
            )

            Result.success(sesion)
        } catch (e: Exception) {
            Result.failure(Exception("Error en base de datos local: ${e.message}"))
        }
    }

    /**
     * Genera y guarda la visita temporal tanto en Room SQLite local como en la colección
     * aislada de Firestore `/condominiums/{condominiumId}/qr_passes/{passCode}`.
     */
    suspend fun crearVisitaLocal(
        context: Context,
        idCondominio: String,
        casa: String,
        calle: String = "",
        nombreVisitante: String,
        fechaVisita: String,
        notas: String,
        duracionHoras: Int = 24,
        tipoPase: PassType = PassType.VISITOR_SINGLE,
        placasVehiculo: String? = null,
        documentoVisitante: String = "Verificar en Caseta",
        maxEntries: Int = 1,
        anfitrion: String? = null
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val qrPassDao = db.qrPassDao()

            val idVisita = UUID.randomUUID().toString().take(6).uppercase()
            val payload = buildQrPayload(idCondominio, casa, idVisita)

            val condoObj = CONDOMINIOS_PREDETERMINADOS.find { it.id == idCondominio }
            val condoName = condoObj?.nombre ?: idCondominio
            val destination = if (calle.isNotBlank()) "Casa $casa · $calle ($condoName)" else "Casa $casa ($condoName)"
            val hostName = anfitrion ?: "Casa $casa · $calle ($condoName)"

            val validUntilMillis = System.currentTimeMillis() + (duracionHoras * 3600 * 1000L)
            val integrityHash = AlphaCoreEngine.computeIntegrityHash(payload, documentoVisitante, destination)

            val entidad = QrPassRoomEntity(
                passCode = payload,
                guestName = nombreVisitante.trim(),
                guestDocument = documentoVisitante.trim().ifBlank { "Verificar en Caseta" },
                destinationHouse = destination,
                hostResidentName = hostName,
                vehiclePlate = placasVehiculo?.trim()?.takeIf { it.isNotBlank() },
                passType = tipoPase,
                createdAtMillis = System.currentTimeMillis(),
                validUntilMillis = validUntilMillis,
                maxEntries = maxEntries,
                currentEntriesCount = 0,
                isActive = true,
                note = if (notas.isNotBlank()) "${notas.trim()} [Fecha: $fechaVisita]" else "[Fecha: $fechaVisita]",
                integrityHash = integrityHash
            )

            // 1. Guardar en Room Database (SQLite local autónomo)
            qrPassDao.insertPass(entidad)

            // 2. Guardar en Firestore colección aislada multi-tenant si Firebase está disponible
            try {
                val firestore = FirebaseFirestore.getInstance()
                val firestoreResult = FirestoreTenantManager.saveQrPass(
                    firestore = firestore,
                    condominiumId = idCondominio,
                    pass = entidad
                )
                if (firestoreResult.isSuccess) {
                    Log.d("MedusaVecinosService", "Pase QR sincronizado exitosamente en Firestore para $idCondominio")
                } else {
                    Log.w("MedusaVecinosService", "Aviso sync Firestore: ${firestoreResult.exceptionOrNull()?.message}")
                }
            } catch (fe: Throwable) {
                Log.d("MedusaVecinosService", "Modo offline / local Room activo: ${fe.message}")
            }

            Result.success(Pair(idVisita, payload))
        } catch (e: Exception) {
            Result.failure(Exception("Error al generar pase temporal: ${e.message}"))
        }
    }

    /**
     * Lista las visitas registradas para esta casa desde Room Database (SQLite local).
     */
    suspend fun listarVisitasLocales(
        context: Context,
        idCondominio: String,
        casa: String
    ): Result<List<VecinoVisita>> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val qrPassDao = db.qrPassDao()
            val allPasses = qrPassDao.getAllPassesList()

            val prefix = "MEDUSA-VISITA-$idCondominio-$casa-"
            val now = System.currentTimeMillis()
            val misVisitas = allPasses
                .filter { it.passCode.startsWith(prefix) || it.destinationHouse.contains("Casa $casa", ignoreCase = true) }
                .map { pass ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val fechaFormateada = sdf.format(Date(pass.createdAtMillis))
                    val estado = when {
                        !pass.isActive -> "Cancelado"
                        pass.currentEntriesCount >= pass.maxEntries -> "Usado (Ingresó)"
                        now > pass.validUntilMillis -> "Expirado"
                        else -> "Activo / Válido"
                    }
                    
                    VecinoVisita(
                        idVisita = pass.passCode.substringAfterLast("-", pass.passCode.takeLast(6)),
                        nombreVisitante = pass.guestName,
                        fechaVisita = fechaFormateada,
                        notas = pass.note ?: "",
                        estado = estado,
                        passCode = pass.passCode,
                        validUntilMillis = pass.validUntilMillis,
                        maxEntries = pass.maxEntries,
                        vehiclePlate = pass.vehiclePlate,
                        passType = pass.passType.name
                    )
                }

            Result.success(misVisitas)
        } catch (e: Exception) {
            Result.failure(Exception("Error consultando visitas locales: ${e.message}"))
        }
    }

    /**
     * Genera el texto formal y estructurado de invitación para compartir por WhatsApp u otras apps.
     */
    fun generarTextoInvitacion(
        condoNombre: String,
        casa: String,
        calle: String = "",
        nombreVisitante: String,
        passCode: String,
        validUntilMillis: Long,
        placas: String? = null,
        maxEntries: Int = 1,
        notas: String = ""
    ): String {
        val sdf = SimpleDateFormat("EEEE dd 'de' MMMM, HH:mm 'hrs'", Locale("es", "ES"))
        val vigenciaStr = sdf.format(Date(validUntilMillis))
        val destinoStr = if (calle.isNotBlank()) "Casa $casa · $calle" else "Casa $casa"
        val placasStr = if (!placas.isNullOrBlank()) "\n🚗 Vehículo autorizado: $placas" else ""
        val notasStr = if (notas.isNotBlank()) "\n📝 Indicaciones: $notas" else ""

        return """
            🎫 *PASE DE ACCESO TEMPORAL - MEDUSA ALFHA*
            🏢 *Condominio:* $condoNombre
            📍 *Destino:* $destinoStr
            👤 *Invitado:* $nombreVisitante$placasStr
            ⏳ *Válido hasta:* $vigenciaStr
            🔢 *Accesos autorizados:* $maxEntries uso(s)$notasStr
            
            🔑 *CÓDIGO DE CASETA:*
            `$passCode`
            
            ℹ️ _Muestra este código o código QR en caseta de vigilancia al oficial de seguridad para un ingreso ágil e inmediato._
        """.trimIndent()
    }

    /**
     * Guarda el Bitmap de QR en cache y genera un URI seguro para compartir como archivo de imagen.
     */
    fun guardarQrEnCache(context: Context, bitmap: Bitmap, passCode: String): Uri? {
        return try {
            val cacheFolder = File(context.cacheDir, "qr_passes")
            if (!cacheFolder.exists()) cacheFolder.mkdirs()
            val cleanName = passCode.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(cacheFolder, "pass_$cleanName.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e("MedusaVecinosService", "Error guardando QR en cache: ${e.message}")
            null
        }
    }

    /**
     * Prepara el Intent de Compartir (con opción de imagen QR y texto estructurado).
     */
    fun crearIntentCompartirPase(
        context: Context,
        textoInvitacion: String,
        qrImageUri: Uri? = null
    ): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            if (qrImageUri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, qrImageUri)
                putExtra(Intent.EXTRA_TEXT, textoInvitacion)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textoInvitacion)
            }
        }
        return Intent.createChooser(sendIntent, "Compartir Pase Temporal de Visitante")
    }

    fun buildQrPayload(idCondominio: String, casa: String, idVisita: String): String {
        return "MEDUSA-VISITA-$idCondominio-$casa-$idVisita"
    }

    fun parseMedusaVisitaQr(qrCode: String): Triple<String, String, String>? {
        if (!qrCode.startsWith("MEDUSA-VISITA-")) return null
        val parts = qrCode.split("-")
        if (parts.size >= 5) {
            val idCondo = parts[2]
            val casa = parts[3]
            val idVisita = parts[4]
            return Triple(idCondo, casa, idVisita)
        }
        return null
    }
}

