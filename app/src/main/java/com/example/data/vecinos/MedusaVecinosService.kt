package com.example.data.vecinos

import android.content.Context
import com.example.data.booking.AppDatabase
import com.example.data.passes.QrPassRoomEntity
import com.example.data.resident.ResidentEntity
import com.example.data.resident.UnitEntity
import com.example.scanner.PassType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val passCode: String = ""
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
     * Genera y guarda la visita directamente en Room Database (SQLite local).
     */
    suspend fun crearVisitaLocal(
        context: Context,
        idCondominio: String,
        casa: String,
        calle: String = "",
        nombreVisitante: String,
        fechaVisita: String,
        notas: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val qrPassDao = db.qrPassDao()

            val idVisita = UUID.randomUUID().toString().take(6).uppercase()
            val payload = buildQrPayload(idCondominio, casa, idVisita)

            val condoObj = CONDOMINIOS_PREDETERMINADOS.find { it.id == idCondominio }
            val condoName = condoObj?.nombre ?: idCondominio
            val destination = if (calle.isNotBlank()) "Casa $casa · $calle ($condoName)" else "Casa $casa ($condoName)"

            val entidad = QrPassRoomEntity(
                passCode = payload,
                guestName = nombreVisitante.trim(),
                guestDocument = "Verificar en Caseta",
                destinationHouse = destination,
                hostResidentName = "Casa $casa · $calle ($condoName)",
                vehiclePlate = null,
                passType = PassType.VISITOR_SINGLE,
                validUntilMillis = System.currentTimeMillis() + (48 * 3600 * 1000L), // 48 horas de vigencia
                maxEntries = 1,
                currentEntriesCount = 0,
                note = "Pase local MEDUSA: ${notas.trim()} [Fecha: $fechaVisita]"
            )

            qrPassDao.insertPass(entidad)
            Result.success(Pair(idVisita, payload))
        } catch (e: Exception) {
            Result.failure(Exception("Error al guardar en Room: ${e.message}"))
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
            val misVisitas = allPasses
                .filter { it.passCode.startsWith(prefix) || it.destinationHouse.contains("Casa $casa", ignoreCase = true) }
                .map { pass ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val fechaFormateada = sdf.format(Date(pass.createdAtMillis))
                    val estado = if (pass.currentEntriesCount >= pass.maxEntries) "Usado (Ingresó)" else "Pendiente / Activo"
                    
                    VecinoVisita(
                        idVisita = pass.passCode.substringAfterLast("-", pass.passCode.takeLast(6)),
                        nombreVisitante = pass.guestName,
                        fechaVisita = fechaFormateada,
                        notas = pass.note ?: "",
                        estado = estado,
                        passCode = pass.passCode
                    )
                }

            Result.success(misVisitas)
        } catch (e: Exception) {
            Result.failure(Exception("Error consultando visitas locales: ${e.message}"))
        }
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

