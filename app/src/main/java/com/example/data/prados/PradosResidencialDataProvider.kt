package com.example.data.prados

import com.example.data.booking.AppDatabase
import com.example.data.booking.CommonAreaBooking
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.passes.QrPassRoomEntity
import com.example.data.vecinos.LosPradosCroquisData
import com.example.data.vecinos.LoteCroquis
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.PassType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Proveedor Oficial de Datos Operativos para RESIDENCIAL LOS PRADOS
 * Ubicación: 2750 Avenida de la Cantera, Santiago de Querétaro, Qro.
 *
 * Vincula exclusivamente las casas y prototipos reales de LosPradosCroquisData
 * con Pases QR, Accesos por Garita e Incidencias Condominales.
 */
object PradosResidencialDataProvider {

    val CONDOMINIO_NOMBRE = "Los Prados Residencial"
    val DIRECCION_OFICIAL = LosPradosCroquisData.DIRECCION_DESARROLLO

    /**
     * Obtiene el LoteCroquis correspondiente a una casa y calle, o null si no se encuentra
     */
    fun findLoteByCasaAndCalle(numeroCasa: Int, calle: String): LoteCroquis? {
        val cleanCalle = calle.trim().lowercase()
        return LosPradosCroquisData.TODOS_LOS_LOTES.find {
            it.numero == numeroCasa && it.calle.lowercase().contains(cleanCalle)
        }
    }

    /**
     * Extrae información del prototipo y condominio a partir de un string de ubicación
     */
    fun getHouseDescriptor(locationText: String): String {
        val match = Regex("""Casa\s+(\d+)""", RegexOption.IGNORE_CASE).find(locationText)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull()
            if (num != null) {
                val lote = LosPradosCroquisData.TODOS_LOS_LOTES.find { it.numero == num }
                if (lote != null) {
                    return "${lote.labelCasa} · ${lote.calle} (${lote.prototipo.codigo} - ${lote.nombreCondominio})"
                }
            }
        }
        return locationText
    }

    /**
     * Garantiza el sembrado inicial en Room de datos 100% reales de Prados Residencial
     * vinculados estrictamente a casas del fraccionamiento.
     */
    suspend fun seedPradosInitialDataIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val qrDao = db.qrPassDao()
        val visitorDao = db.visitorCheckInDao()
        val incidentDao = db.incidentDao()
        val commonAreaDao = db.commonAreaBookingDao()

        val now = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

        // 1. Sembrado de Pases QR Reales de Prados Residencial
        val existingPasses = qrDao.getAllPassesList()
        if (existingPasses.isEmpty()) {
            val samplePasses = listOf(
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR012",
                    guestName = "Lic. Valeria Sofía Mendoza",
                    guestDocument = "INE-89412039",
                    destinationHouse = "Casa 12 · Calle 1 (Bali 2r)",
                    hostResidentName = "Familia Arismendi",
                    vehiclePlate = "ULM-842-A",
                    passType = PassType.RESIDENT_PERMANENT,
                    validUntilMillis = now + (72 * 3600 * 1000L),
                    maxEntries = 10,
                    currentEntriesCount = 1,
                    note = "Asesora fiscal de la familia, acceso vehicular autorizado",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR028",
                    guestName = "Repartidor Amazon Logistics",
                    guestDocument = "GAF-AMZ-9821",
                    destinationHouse = "Casa 28 · Calle 1 (Bali 2r)",
                    hostResidentName = "Dr. Alejandro Ramos",
                    vehiclePlate = "SS-1029-B",
                    passType = PassType.DELIVERY_SERVICE,
                    validUntilMillis = now + (4 * 3600 * 1000L),
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Entrega de paquetería express en puerta principal",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR054",
                    guestName = "Técnico Totalplay Fibra Óptica",
                    guestDocument = "RFC-TOT-8812",
                    destinationHouse = "Casa 54 · Calle 2 (Bali 2r)",
                    hostResidentName = "Ing. Roberto Valenzuela",
                    vehiclePlate = "UKM-771-C",
                    passType = PassType.EVENT_GUEST,
                    validUntilMillis = now + (8 * 3600 * 1000L),
                    maxEntries = 2,
                    currentEntriesCount = 1,
                    note = "Instalación de acometida de fibra óptica en fachada",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR071",
                    guestName = "Arq. Sergio Villalobos",
                    guestDocument = "CED-761209",
                    destinationHouse = "Casa 71 · Calle 2 (Bali 3r)",
                    hostResidentName = "Sra. Claudia Morales",
                    vehiclePlate = "UPM-332-D",
                    passType = PassType.VISITOR_SINGLE,
                    validUntilMillis = now + (12 * 3600 * 1000L),
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Reunión de asesoría de diseño interior",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR034",
                    guestName = "Enfermera Andrea Ruiz",
                    guestDocument = "REG-ENF-441",
                    destinationHouse = "Casa 34 · Calle 3 (Bali 2r)",
                    hostResidentName = "Don Fernando Soto",
                    vehiclePlate = null,
                    passType = PassType.RESIDENT_PERMANENT,
                    validUntilMillis = now + (48 * 3600 * 1000L),
                    maxEntries = 4,
                    currentEntriesCount = 1,
                    note = "Asistencia y control médico ambulatorio",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR082",
                    guestName = "Cuadrilla Jardinería VerdeSur",
                    guestDocument = "INE-661902",
                    destinationHouse = "Casa 82 · Calle 4 (Bali 3r)",
                    hostResidentName = "Lic. Patricia Delgado",
                    vehiclePlate = "SY-4482-E",
                    passType = PassType.EVENT_GUEST,
                    validUntilMillis = now + (10 * 3600 * 1000L),
                    maxEntries = 2,
                    currentEntriesCount = 0,
                    note = "Poda y diseño de jardín posterior en lote 82",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR015",
                    guestName = "C.P. Mauricio Garza",
                    guestDocument = "INE-319982",
                    destinationHouse = "Casa 15 · Calle 5 (Topacio 3r)",
                    hostResidentName = "Ing. Carlos Mendoza",
                    vehiclePlate = "ULM-910-K",
                    passType = PassType.VISITOR_SINGLE,
                    validUntilMillis = now + (6 * 3600 * 1000L),
                    maxEntries = 1,
                    currentEntriesCount = 0,
                    note = "Invitado a comida de trabajo",
                    isActive = true
                ),
                QrPassRoomEntity(
                    passCode = "MED-PRADOS-QR052",
                    guestName = "Repartidor Uber Eats",
                    guestDocument = "APP-UBR-771",
                    destinationHouse = "Casa 52 · Calle 6 (Topacio 3r)",
                    hostResidentName = "Dra. Marcela Lozano",
                    vehiclePlate = "22-DFG-1",
                    passType = PassType.DELIVERY_SERVICE,
                    validUntilMillis = now + (2 * 3600 * 1000L),
                    maxEntries = 1,
                    currentEntriesCount = 1,
                    note = "Entrega de alimentos en puerta",
                    isActive = true
                )
            )

            for (pass in samplePasses) {
                qrDao.insertPass(pass)
            }
        }

        // 2. Sembrado de Check-Ins Reales en Garita
        val existingCheckIns = visitorDao.getAllCheckInsList()
        if (existingCheckIns.isEmpty()) {
            val sampleCheckIns = listOf(
                VisitorCheckIn(
                    folio = "MED-CK-20260917-001",
                    visitorName = "Lic. Valeria Sofía Mendoza",
                    visitorDocument = "INE-89412039",
                    destinationHouse = "Casa 12 · Calle 1 (Bali 2r)",
                    passCode = "MED-PRADOS-QR012",
                    passTypeLabel = "Visita Frecuente",
                    vehiclePlate = "ULM-842-A",
                    status = "CHECKED_IN",
                    timestampMillis = now - (35 * 60 * 1000L),
                    guardNotes = "Ingreso autorizado con código QR verificado en Garita 1",
                    guardName = "Oficial Ramírez (Garita 1)",
                    hostResidentName = "Familia Arismendi"
                ),
                VisitorCheckIn(
                    folio = "MED-CK-20260917-002",
                    visitorName = "Técnico Totalplay Fibra Óptica",
                    visitorDocument = "RFC-TOT-8812",
                    destinationHouse = "Casa 54 · Calle 2 (Bali 2r)",
                    passCode = "MED-PRADOS-QR054",
                    passTypeLabel = "Servicio Técnico",
                    vehiclePlate = "UKM-771-C",
                    status = "CHECKED_IN",
                    timestampMillis = now - (50 * 60 * 1000L),
                    guardNotes = "Ingreso con equipo técnico y escalera de servicio",
                    guardName = "Oficial Ramírez (Garita 1)",
                    hostResidentName = "Ing. Roberto Valenzuela"
                ),
                VisitorCheckIn(
                    folio = "MED-CK-20260917-003",
                    visitorName = "Repartidor Amazon Logistics",
                    visitorDocument = "GAF-AMZ-9821",
                    destinationHouse = "Casa 28 · Calle 1 (Bali 2r)",
                    passCode = "MED-PRADOS-QR028",
                    passTypeLabel = "Paquetería Express",
                    vehiclePlate = "SS-1029-B",
                    status = "DEPARTED",
                    timestampMillis = now - (120 * 60 * 1000L),
                    checkOutMillis = now - (102 * 60 * 1000L),
                    guardNotes = "Entrega completada; salida registrada por carril 2",
                    guardName = "Oficial Ramírez (Garita 1)",
                    hostResidentName = "Dr. Alejandro Ramos"
                ),
                VisitorCheckIn(
                    folio = "MED-CK-20260917-004",
                    visitorName = "Enfermera Andrea Ruiz",
                    visitorDocument = "REG-ENF-441",
                    destinationHouse = "Casa 34 · Calle 3 (Bali 2r)",
                    passCode = "MED-PRADOS-QR034",
                    passTypeLabel = "Asistencia Médica",
                    vehiclePlate = null,
                    status = "CHECKED_IN",
                    timestampMillis = now - (15 * 60 * 1000L),
                    guardNotes = "Acceso peatonal por torniquete biométrico Garita 1",
                    guardName = "Oficial Velázquez (Garita 1)",
                    hostResidentName = "Don Fernando Soto"
                )
            )

            for (ck in sampleCheckIns) {
                visitorDao.insertCheckIn(ck)
            }
        }

        // 3. Sembrado de Incidencias Reales vinculadas exclusivamente a Casas de Prados Residencial
        val existingIncidents = incidentDao.getAllIncidentsList()
        if (existingIncidents.isEmpty()) {
            val sampleIncidents = listOf(
                IncidentEntity(
                    folio = "INC-PRADOS-001",
                    rawTranscript = "Vehículo sedán Volkswagen Jetta color plata obstruyendo la rampa de cochera frente al lote 14 en Calle 1.",
                    category = IncidentCategory.PARKING_VIALIDAD,
                    priority = IncidentPriority.ALTA,
                    location = "Casa 14 · Calle 1 (Bali 2r)",
                    aiSummary = "Vehículo sedán gris bloqueando rampa de cochera particular en Calle 1 lote 14.",
                    recommendedAction = "Oficial de ronda en cuatrimoto localiza propietario y libera acceso vehicular de inmediato.",
                    timestampMillis = now - (22 * 60 * 1000L),
                    guardName = "Oficial Ramírez (Garita 1)",
                    reportedBy = "Familia Gómez (Casa 14)",
                    reportedByRole = "RESIDENTE",
                    status = "EN_ATENCION",
                    assignedTo = "Oficial de Ronda Perimetral",
                    assignedRole = "GUARDIA",
                    targetSlaMinutes = 45,
                    attendedAtMillis = now - (10 * 60 * 1000L),
                    attendedBy = "Oficial de Ronda Perimetral",
                    evidenceNotes = "Fotografía de vehículo placas UKL-190-F con luces preventivas apagadas.",
                    locationStatus = "CONFIRMADO_EN_LOTE"
                ),
                IncidentEntity(
                    folio = "INC-PRADOS-002",
                    rawTranscript = "Música a volumen excesivo fuera de horario reglamentario reportada en terraza posterior colindante con lote 68.",
                    category = IncidentCategory.RUIDO_CONVIVENCIA,
                    priority = IncidentPriority.MEDIA,
                    location = "Casa 68 · Calle 2 (Bali 3r)",
                    aiSummary = "Exceso de decibeles en terraza privada posterior fuera del horario de convivencia condominal.",
                    recommendedAction = "Visita cordial de mediación y exhorto de apego al reglamento interior.",
                    timestampMillis = now - (45 * 60 * 1000L),
                    guardName = "Guardia de Turno",
                    reportedBy = "Vecino Colindante Lote 67",
                    reportedByRole = "RESIDENTE",
                    status = "REGISTRADO",
                    assignedTo = "Administración & Mediador",
                    assignedRole = "ADMINISTRACION",
                    targetSlaMinutes = 180,
                    locationStatus = "CONFIRMADO_EN_LOTE"
                ),
                IncidentEntity(
                    folio = "INC-PRADOS-003",
                    rawTranscript = "Sensor vehicular de portón automático no leyó antena TAG al ingreso de residente registrado.",
                    category = IncidentCategory.CONTROL_ACCESO,
                    priority = IncidentPriority.ALTA,
                    location = "Casa 24 · Calle 3 (Bali 2r)",
                    aiSummary = "Falla de lectura óptica/RFID en barrera de acceso rápido en carril residentes.",
                    recommendedAction = "Calibración del cabezal receptor RFID e inspección del tag del vehículo.",
                    timestampMillis = now - (60 * 60 * 1000L),
                    guardName = "Operador de Garita",
                    reportedBy = "Operador de Garita Principal",
                    reportedByRole = "GUARDIA",
                    status = "EN_ATENCION",
                    assignedTo = "Técnico de Accesos Automatizados",
                    assignedRole = "GUARDIA",
                    targetSlaMinutes = 45,
                    attendedAtMillis = now - (25 * 60 * 1000L),
                    attendedBy = "Técnico de Accesos",
                    evidenceNotes = "Sensor RFID carril 1 limpiado y probado con 3 vehículos de control.",
                    locationStatus = "CONFIRMADO_EN_LOTE"
                ),
                IncidentEntity(
                    folio = "INC-PRADOS-004",
                    rawTranscript = "Disparo de sensor perimetral en concertina posterior de barda oriente colindante con lote 79.",
                    category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                    priority = IncidentPriority.CRITICA,
                    location = "Casa 79 · Calle 4 (Bali 3r)",
                    aiSummary = "Alerta de sensor perimetral en barda posterior oriente colindante con lote 79.",
                    recommendedAction = "Patrullaje táctico inmediato con linterna y cámara térmica; barda inspeccionada e intacta.",
                    timestampMillis = now - (90 * 60 * 1000L),
                    guardName = "Supervisor Táctico",
                    reportedBy = "Sensor Perimetral Barda Oriente",
                    reportedByRole = "SISTEMA",
                    status = "RESUELTO",
                    assignedTo = "Supervisor Táctico Alfa",
                    assignedRole = "SUPERVISOR",
                    targetSlaMinutes = 15,
                    attendedAtMillis = now - (85 * 60 * 1000L),
                    attendedBy = "Supervisor Táctico Alfa",
                    resolvedAtMillis = now - (75 * 60 * 1000L),
                    resolvedBy = "Supervisor Táctico Alfa",
                    resolutionNotes = "Verificación en sitio: Falso disparo generado por rama de eucalipto empujada por rachas de viento. Se retiró la rama y la barda permanece 100% segura y con sensor activo.",
                    locationStatus = "CONFIRMADO_EN_LOTE"
                ),
                IncidentEntity(
                    folio = "INC-PRADOS-005",
                    rawTranscript = "Luminaria exterior de alumbrado parpadeando intermitente frente a glorieta en Calle 5.",
                    category = IncidentCategory.INFRAESTRUCTURA,
                    priority = IncidentPriority.MEDIA,
                    location = "Casa 18 · Calle 5 (Topacio 3r)",
                    aiSummary = "Falla de luminaria pública exterior poste LP3-09 frente al lote 18.",
                    recommendedAction = "Sustitución de balastra y foco LED por la cuadrilla de mantenimiento diurno.",
                    timestampMillis = now - (150 * 60 * 1000L),
                    guardName = "Guardia de Ronda",
                    reportedBy = "Ing. Carlos Mendoza (Casa 18)",
                    reportedByRole = "RESIDENTE",
                    status = "REGISTRADO",
                    assignedTo = "Coordinación de Mantenimiento",
                    assignedRole = "ADMINISTRACION",
                    targetSlaMinutes = 180,
                    locationStatus = "CONFIRMADO_EN_LOTE"
                ),
                IncidentEntity(
                    folio = "INC-PRADOS-006",
                    rawTranscript = "Mascota canina deambulando sin correa en área verde colindante al fondo de Calle 6.",
                    category = IncidentCategory.RUIDO_CONVIVENCIA,
                    priority = IncidentPriority.BAJA,
                    location = "Casa 55 · Calle 6 (Topacio 3r)",
                    aiSummary = "Mascota suelta sin supervisión en área común verde colindante con lote 55.",
                    recommendedAction = "Notificación amistosa a los tutores de la mascota para resguardo en predio privado.",
                    timestampMillis = now - (210 * 60 * 1000L),
                    guardName = "Oficial de Ronda",
                    reportedBy = "Dra. Marcela Lozano (Casa 52)",
                    reportedByRole = "RESIDENTE",
                    status = "RESUELTO",
                    assignedTo = "Oficial de Ronda",
                    assignedRole = "GUARDIA",
                    targetSlaMinutes = 1440,
                    attendedAtMillis = now - (200 * 60 * 1000L),
                    attendedBy = "Oficial de Ronda",
                    resolvedAtMillis = now - (190 * 60 * 1000L),
                    resolvedBy = "Oficial de Ronda",
                    resolutionNotes = "Se dialogó con los residentes del lote 55, resguardaron a la mascota en su jardín privado de manera colaborativa.",
                    locationStatus = "CONFIRMADO_EN_LOTE"
                ),
                IncidentEntity(
                    folio = "INC-PRADOS-007",
                    rawTranscript = "Lector de código QR en tótem vehicular de garita principal presenta suciedad en cristal protector.",
                    category = IncidentCategory.CONTROL_ACCESO,
                    priority = IncidentPriority.MEDIA,
                    location = "Garita Principal · Av. de la Cantera 2750",
                    aiSummary = "Lector 2D de QR en tótem carril de visitas requiere mantenimiento y limpieza.",
                    recommendedAction = "Limpieza con paño de microfibra y calibración de distancia focal.",
                    timestampMillis = now - (30 * 60 * 1000L),
                    guardName = "Operador de Garita",
                    reportedBy = "Guardia en Turno",
                    reportedByRole = "GUARDIA",
                    status = "EN_ATENCION",
                    assignedTo = "Soporte Técnico de Garita",
                    assignedRole = "GUARDIA",
                    targetSlaMinutes = 180,
                    attendedAtMillis = now - (15 * 60 * 1000L),
                    attendedBy = "Soporte Técnico de Garita",
                    locationStatus = "GARITA_ACCESO"
                )
            )

            for (inc in sampleIncidents) {
                incidentDao.insertIncident(inc)
            }
        }

        // 4. Sembrado de Reservas de Áreas Comunes vinculadas a Casas Reales de Prados
        val bookingsCount = commonAreaDao.getBookingsCount()
        if (bookingsCount == 0) {
            val sampleBookings = listOf(
                CommonAreaBooking(
                    folio = "CAB-PRADOS-101",
                    userId = "RES-012",
                    userName = "Familia Arismendi",
                    userUnit = "Casa 12 · Calle 1 (Bali 2r)",
                    facilityName = "Casa Club & Salón de Eventos",
                    bookingDate = todayStr,
                    timeSlot = "18:00 - 22:00",
                    startTimeMillis = now + (2 * 3600 * 1000L),
                    endTimeMillis = now + (6 * 3600 * 1000L),
                    condominiumId = CONDOMINIO_NOMBRE,
                    status = "CONFIRMED",
                    guestCount = 24,
                    specialRequests = "Montaje de mesas para convivencia familiar de aniversario",
                    notes = "Pase vehicular colectivo autorizado en caseta principal"
                ),
                CommonAreaBooking(
                    folio = "CAB-PRADOS-102",
                    userId = "RES-054",
                    userName = "Ing. Roberto Valenzuela",
                    userUnit = "Casa 54 · Calle 2 (Bali 2r)",
                    facilityName = "Cancha de Pádel #1",
                    bookingDate = todayStr,
                    timeSlot = "19:00 - 21:00",
                    startTimeMillis = now + (3 * 3600 * 1000L),
                    endTimeMillis = now + (5 * 3600 * 1000L),
                    condominiumId = CONDOMINIO_NOMBRE,
                    status = "CONFIRMED",
                    guestCount = 4,
                    specialRequests = "Encendido de iluminación nocturna de alta potencia",
                    notes = "Partido amistoso de residentes"
                ),
                CommonAreaBooking(
                    folio = "CAB-PRADOS-103",
                    userId = "RES-015",
                    userName = "Ing. Carlos Mendoza",
                    userUnit = "Casa 15 · Calle 5 (Topacio 3r)",
                    facilityName = "Área de Asadores & BBQ Terraza",
                    bookingDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now + 86400000L)),
                    timeSlot = "13:00 - 17:00",
                    startTimeMillis = now + (20 * 3600 * 1000L),
                    endTimeMillis = now + (24 * 3600 * 1000L),
                    condominiumId = CONDOMINIO_NOMBRE,
                    status = "CONFIRMED",
                    guestCount = 14,
                    specialRequests = "Uso de parrilla doble de carbón",
                    notes = "Compromiso de entrega limpia y brasas extinguidas"
                ),
                CommonAreaBooking(
                    folio = "CAB-PRADOS-104",
                    userId = "RES-052",
                    userName = "Dra. Marcela Lozano",
                    userUnit = "Casa 52 · Calle 6 (Topacio 3r)",
                    facilityName = "Alberca Semiolímpica & Solárium",
                    bookingDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now + 86400000L)),
                    timeSlot = "10:00 - 13:00",
                    startTimeMillis = now + (18 * 3600 * 1000L),
                    endTimeMillis = now + (21 * 3600 * 1000L),
                    condominiumId = CONDOMINIO_NOMBRE,
                    status = "CONFIRMED",
                    guestCount = 8,
                    specialRequests = "Área de camastros frente a chapoteadero",
                    notes = "Convivencia familiar con menores de edad supervisados"
                )
            )

            commonAreaDao.insertBookings(sampleBookings)
        }
    }
}
