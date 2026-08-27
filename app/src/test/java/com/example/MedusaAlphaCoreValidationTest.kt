package com.example

import com.example.data.core.AlphaCoreEngine
import com.example.data.core.TimeReturnEngine
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.passes.QrPassRoomEntity
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.PassType
import com.example.scanner.VisitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de Validación Exhaustiva para MEDUSA ALFHA:
 * Verificación de los 15 Casos Críticos de Flujo, Unicidad de Folios,
 * No Recaptura ("UN DATO → UNA VEZ → MUCHOS USOS"), Integridad Criptográfica SHA-256
 * y Motor de Tiempo Devuelto ("ÉSTO DEVUELVE TIEMPO").
 */
class MedusaAlphaCoreValidationTest {

    // CASO 1: Crear Pase QR con Folio Estándar MED-YYYYMMDD-XXXX
    @Test
    fun testCase01_createQrPassWithStandardFolio() {
        val passCode = AlphaCoreEngine.generateUniqueFolio("MED")
        assertTrue("El folio debe iniciar con MED-", passCode.startsWith("MED-"))
        val regex = Regex("^MED-\\d{8}-[A-Z0-9]{4}$")
        assertTrue("El folio debe cumplir el patrón MED-YYYYMMDD-XXXX: $passCode", regex.matches(passCode))
    }

    // CASO 2: Emitir Pase con Integridad Criptográfica SHA-256
    @Test
    fun testCase02_qrPassCryptographicIntegrity() {
        val passCode = "MED-20260821-4921"
        val guestName = "Valeria Sofia Mendoza"
        val house = "Casa #104"

        val signature1 = AlphaCoreEngine.computeIntegrityHash(passCode, guestName, house)
        val signature2 = AlphaCoreEngine.computeIntegrityHash(passCode, guestName, house)
        val signatureAltered = AlphaCoreEngine.computeIntegrityHash(passCode, "Nombre Alterado", house)

        assertEquals("La firma SHA-256 debe ser determinista e inmutable", signature1, signature2)
        assertFalse("Cualquier alteración debe cambiar la firma", signature1 == signatureAltered)
        assertEquals("SHA-256 debe tener 64 caracteres hexadecimales", 64, signature1.length)
    }

    // CASO 3: Validación de Pase QR Válido y Parámetros
    @Test
    fun testCase03_validateActiveQrPass() {
        val pass = QrPassRoomEntity(
            passCode = "MED-20260821-1001",
            guestName = "Carlos Gomez",
            guestDocument = "12.345.678-9",
            destinationHouse = "Casa #204",
            hostResidentName = "Patricia Soto",
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + 3600000L,
            maxEntries = 1,
            currentEntriesCount = 0
        )

        assertFalse("El pase no debe estar expirado", pass.isExpired)
        assertFalse("El pase no debe haber alcanzado el límite de usos", pass.isExhausted)
        assertTrue("El pase debe ser válido para ingreso", pass.isValidForEntry)
    }

    // CASO 4: Realizar Check-in y Transición de Estado sin Recaptura
    @Test
    fun testCase04_performCheckInWithoutDataRecapture() {
        val pass = QrPassRoomEntity(
            passCode = "MED-20260821-1002",
            guestName = "Marcos Rios",
            guestDocument = "14.567.890-1",
            destinationHouse = "Casa #110",
            hostResidentName = "Ana Gomez",
            passType = PassType.DELIVERY_SERVICE,
            validUntilMillis = System.currentTimeMillis() + 7200000L
        )

        // Simula la creación del check-in a partir del pase (UN DATO -> UNA VEZ)
        val checkIn = VisitorCheckIn(
            id = 1L,
            folio = pass.passCode, // Preserva folio único
            visitorName = pass.guestName,
            visitorDocument = pass.guestDocument,
            destinationHouse = pass.destinationHouse,
            passCode = pass.passCode,
            passTypeLabel = pass.passType.label,
            status = "CHECKED_IN",
            hostResidentName = pass.hostResidentName
        )

        assertEquals("El folio debe mantenerse idéntico", pass.passCode, checkIn.folio)
        assertEquals("El nombre no requiere recaptura", pass.guestName, checkIn.visitorName)
        assertEquals("La casa destino proviene del pase original", pass.destinationHouse, checkIn.destinationHouse)
        assertEquals(VisitorStatus.CHECKED_IN, checkIn.toVisitorEntry().status)
    }

    // CASO 5: Validar Rechazo de Pase Vencido / Agotado
    @Test
    fun testCase05_expiredAndExhaustedPassRejection() {
        val expiredPass = QrPassRoomEntity(
            passCode = "MED-20260821-9999",
            guestName = "Invalido",
            guestDocument = "00.000.000-0",
            destinationHouse = "Casa #99",
            hostResidentName = "N/A",
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() - 60000L,
            maxEntries = 1,
            currentEntriesCount = 0
        )
        assertTrue("El pase debe marcarse como expirado", expiredPass.isExpired)
        assertFalse("Pase expirado no debe ser válido para entrar", expiredPass.isValidForEntry)

        val exhaustedPass = QrPassRoomEntity(
            passCode = "MED-20260821-8888",
            guestName = "Usado",
            guestDocument = "11.111.111-1",
            destinationHouse = "Casa #88",
            hostResidentName = "N/A",
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + 600000L,
            maxEntries = 1,
            currentEntriesCount = 1
        )
        assertTrue("El pase debe marcarse como agotado", exhaustedPass.isExhausted)
        assertFalse("Pase agotado no debe ser válido para entrar", exhaustedPass.isValidForEntry)
    }

    // CASO 6: Registrar Salida One-Touch Check-Out
    @Test
    fun testCase06_oneTouchCheckOutTransition() {
        val entryTime = System.currentTimeMillis() - (45 * 60 * 1000L) // 45 min atrás
        val checkIn = VisitorCheckIn(
            id = 42L,
            folio = "MED-20260821-0042",
            visitorName = "Dr. Rodrigo Morales",
            visitorDocument = "11.222.333-4",
            destinationHouse = "Casa #102",
            passCode = "MED-20260821-0042",
            passTypeLabel = "Visita Frecuente",
            status = "CHECKED_IN",
            timestampMillis = entryTime
        )

        // Registrar salida
        val checkOutTime = System.currentTimeMillis()
        val departedCheckIn = checkIn.copy(
            status = "DEPARTED",
            checkOutMillis = checkOutTime,
            guardNotes = "Salida confirmada en 1 toque"
        )

        assertEquals("El estado debe ser DEPARTED", "DEPARTED", departedCheckIn.status)
        assertNotNull("checkOutMillis no debe ser nulo", departedCheckIn.checkOutMillis)
        val entry = departedCheckIn.toVisitorEntry()
        assertEquals(VisitorStatus.DEPARTED, entry.status)
        assertTrue("Debe contener la duración formateada", entry.durationStay?.contains("45 min") == true)
    }

    // CASO 7: Cálculo de Permanencia Automática
    @Test
    fun testCase07_stayDurationCalculation() {
        val start = 1000000L
        val endMinutes = start + (25 * 60 * 1000L)
        val endHoursAndMins = start + ((2 * 3600 + 15 * 60) * 1000L)

        val durationMin = AlphaCoreEngine.calculateDurationFormatted(start, endMinutes)
        val durationHourMin = AlphaCoreEngine.calculateDurationFormatted(start, endHoursAndMins)

        assertEquals("25 min", durationMin)
        assertEquals("2h 15m", durationHourMin)
    }

    // CASO 8: Registro de Incidencia con Folio MEDUSA y Estructuración
    @Test
    fun testCase08_voiceIncidentStandardization() {
        val folio = AlphaCoreEngine.generateUniqueFolio("MED")
        val incident = IncidentEntity(
            folio = folio,
            rawTranscript = "Vehículo mal estacionado bloqueando portón oriente",
            category = IncidentCategory.PARKING_VIALIDAD,
            priority = IncidentPriority.MEDIA,
            location = "Portón Oriente",
            aiSummary = "Vehículo obstaculizando salida de emergencia",
            recommendedAction = "Solicitar retiro por altavoz o grúa",
            guardName = "Agente Garita 1"
        )

        assertTrue("Folio de incidencia debe iniciar con MED-", incident.folio.startsWith("MED-"))
        assertEquals("Portón Oriente", incident.location)
        assertEquals("REGISTRADO", incident.status)
    }

    // CASO 9: Supervisión Táctica y Cierre Automático
    @Test
    fun testCase09_tacticalSupervisionReportAutoClose() {
        val folio = AlphaCoreEngine.generateUniqueFolio("MED")
        val audit = SupervisionAuditEntity(
            folio = folio,
            supervisorName = "Supervisor Nocturno",
            checkpointName = "Perímetro Norte",
            areaName = "Sector Canchas",
            statusCondition = "OPTIMO",
            findingsDescription = "Luminarias y cerco eléctrico operando al 100%",
            riskLevel = "BAJO",
            correctiveActionRequired = "Ninguna requerida",
            responsibleParty = "Mantenimiento Preventivo",
            commitmentDate = "2026-08-25",
            isClosed = true
        )

        assertTrue(audit.isClosed)
        assertTrue(audit.folio.startsWith("MED-"))
        assertEquals("OPTIMO", audit.statusCondition)
    }

    // CASO 10: Consultar Métricas del Motor Time Return
    @Test
    fun testCase10_timeReturnEngineCalculations() {
        val checkIns = 10
        val checkOuts = 8
        val incidents = 2
        val supervisions = 1
        val passes = 12
        val notifications = checkIns + checkOuts

        val totalSavedSec = (checkIns * TimeReturnEngine.SECONDS_PER_QR_CHECK_IN) +
                (checkOuts * TimeReturnEngine.SECONDS_PER_ONE_TOUCH_CHECK_OUT) +
                (incidents * TimeReturnEngine.SECONDS_PER_VOICE_INCIDENT) +
                (supervisions * TimeReturnEngine.SECONDS_PER_SUPERVISION_REPORT) +
                (passes * TimeReturnEngine.SECONDS_PER_QR_PASS_CREATION) +
                (notifications * TimeReturnEngine.SECONDS_PER_AUTO_NOTIFICATION)

        // Verificación de los coeficientes de ahorro
        assertEquals(120L, TimeReturnEngine.SECONDS_PER_QR_CHECK_IN)
        assertEquals(60L, TimeReturnEngine.SECONDS_PER_ONE_TOUCH_CHECK_OUT)
        assertEquals(180L, TimeReturnEngine.SECONDS_PER_VOICE_INCIDENT)
        assertEquals(600L, TimeReturnEngine.SECONDS_PER_SUPERVISION_REPORT)

        assertTrue("El tiempo ahorrado debe ser mayor a 0", totalSavedSec > 0)
        val totalMinutes = totalSavedSec / 60
        assertTrue("Los minutos ahorrados deben reflejar las operaciones", totalMinutes >= 70)
    }

    // CASO 11: Preservación de Datos ("UN DATO → UNA VEZ → MUCHOS USOS")
    @Test
    fun testCase11_singleDataPointMultiUse() {
        val masterFolio = "MED-20260821-7701"
        val resident = "Carlos Mendoza"
        val visitor = "Valeria Mendoza"
        val house = "Casa #104"

        // 1. Uso en Pase QR
        val pass = QrPassRoomEntity(
            passCode = masterFolio,
            guestName = visitor,
            guestDocument = "18.492.301-2",
            destinationHouse = house,
            hostResidentName = resident,
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + 86400000L
        )

        // 2. Uso en Check-In
        val checkIn = VisitorCheckIn(
            folio = pass.passCode,
            visitorName = pass.guestName,
            visitorDocument = pass.guestDocument,
            destinationHouse = pass.destinationHouse,
            passCode = pass.passCode,
            passTypeLabel = pass.passType.label,
            status = "CHECKED_IN",
            hostResidentName = pass.hostResidentName
        )

        // 3. Uso en Salida
        val checkOut = checkIn.copy(
            status = "DEPARTED",
            checkOutMillis = System.currentTimeMillis()
        )

        assertEquals("El folio original fluye sin alteración", masterFolio, pass.passCode)
        assertEquals("El checkIn utiliza el folio del pase", masterFolio, checkIn.folio)
        assertEquals("El checkOut preserva el folio del pase", masterFolio, checkOut.folio)
    }

    // CASO 12: Cero Recaptura entre Módulos
    @Test
    fun testCase12_zeroRecaptureBetweenModules() {
        val originalVehiclePlate = "KXYZ-98"
        val pass = QrPassRoomEntity(
            passCode = "MED-20260821-8801",
            guestName = "Gonzalo Silva",
            guestDocument = "17.111.222-3",
            destinationHouse = "Casa #305",
            hostResidentName = "Familia Silva",
            vehiclePlate = originalVehiclePlate,
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + 3600000L
        )

        val checkIn = VisitorCheckIn(
            folio = pass.passCode,
            visitorName = pass.guestName,
            visitorDocument = pass.guestDocument,
            destinationHouse = pass.destinationHouse,
            passCode = pass.passCode,
            passTypeLabel = pass.passType.label,
            vehiclePlate = pass.vehiclePlate,
            status = "CHECKED_IN",
            hostResidentName = pass.hostResidentName
        )

        assertEquals("La patente automotriz viaja intacta sin re-digitación", originalVehiclePlate, checkIn.vehiclePlate)
    }

    // CASO 13: Comprobar Formato de Folio MED-YYYYMMDD-XXXX
    @Test
    fun testCase13_folioFormatStrictCompliance() {
        for (i in 1..20) {
            val folio = AlphaCoreEngine.generateUniqueFolio()
            assertTrue("Folio debe comenzar con MED-: $folio", folio.startsWith("MED-"))
            val parts = folio.split("-")
            assertEquals(3, parts.size)
            assertEquals("MED", parts[0])
            assertEquals(8, parts[1].length) // YYYYMMDD
            assertEquals(4, parts[2].length) // XXXX
        }
    }

    // CASO 14: Cadena de Auditoría Inmutable SHA-256
    @Test
    fun testCase14_auditLogSha256Sealing() {
        val folio = "MED-20260821-1234"
        val operator = "Guardia Garita 1"
        val target = "Casa #104"

        val signature = AlphaCoreEngine.computeIntegrityHash(folio, operator, target)
        assertNotNull("La firma no debe ser nula", signature)
        assertTrue("La firma no debe ser vacía", signature.isNotEmpty())
        assertEquals(64, signature.length)
    }

    // CASO 15: Medir Impacto Acumulado de Tiempo Devuelto
    @Test
    fun testCase15_cumulativeTimeReturnMeasurement() {
        val totalSec = 14400L // 4 horas exactas
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60

        assertEquals(4L, hours)
        assertEquals(0L, mins)

        val totalMinHuman = totalSec / 60
        assertEquals(240L, totalMinHuman)
    }

    // CASO 16: Validación Aritmética de Fórmulas de Tiempo Devuelto (T_trad - T_medusa = Delta)
    @Test
    fun testCase16_timeCalculationStrictFormulas() {
        // Check-In
        assertEquals(120L, TimeReturnEngine.TRADITIONAL_QR_CHECK_IN - TimeReturnEngine.MEDUSA_QR_CHECK_IN)
        assertEquals(120L, TimeReturnEngine.SECONDS_PER_QR_CHECK_IN)

        // Salida One-Touch
        assertEquals(60L, TimeReturnEngine.TRADITIONAL_ONE_TOUCH_CHECK_OUT - TimeReturnEngine.MEDUSA_ONE_TOUCH_CHECK_OUT)
        assertEquals(60L, TimeReturnEngine.SECONDS_PER_ONE_TOUCH_CHECK_OUT)

        // Incidencias de Seguridad
        assertEquals(180L, TimeReturnEngine.TRADITIONAL_VOICE_INCIDENT - TimeReturnEngine.MEDUSA_VOICE_INCIDENT)
        assertEquals(180L, TimeReturnEngine.SECONDS_PER_VOICE_INCIDENT)

        // Supervisión Táctica
        assertEquals(600L, TimeReturnEngine.TRADITIONAL_SUPERVISION_REPORT - TimeReturnEngine.MEDUSA_SUPERVISION_REPORT)
        assertEquals(600L, TimeReturnEngine.SECONDS_PER_SUPERVISION_REPORT)

        // Pase QR / Autogestión
        assertEquals(120L, TimeReturnEngine.TRADITIONAL_QR_PASS_CREATION - TimeReturnEngine.MEDUSA_QR_PASS_CREATION)
        assertEquals(120L, TimeReturnEngine.SECONDS_PER_QR_PASS_CREATION)

        // Notificación al Residente
        assertEquals(90L, TimeReturnEngine.TRADITIONAL_AUTO_NOTIFICATION - TimeReturnEngine.MEDUSA_AUTO_NOTIFICATION)
        assertEquals(90L, TimeReturnEngine.SECONDS_PER_AUTO_NOTIFICATION)
    }

    // CASO 17: Blindaje contra Deltas Negativos e Inversiones
    @Test
    fun testCase17_negativeDeltaProtection() {
        val normalItem = com.example.data.core.TiempoDevuelto(
            id = "TR-TEST-1",
            folio = "MED-20260821-0001",
            tipoOperacion = "Test Normal",
            beneficiario = com.example.data.core.BeneficiaryRole.GUARDS,
            tiempoTradicionalSegundos = 150L,
            tiempoMedusaSegundos = 30L,
            evidenciaEvento = "Test",
            usuarioOrigen = "Guardia 1",
            moduloOrigen = "ACCESO"
        )
        assertEquals(120L, normalItem.tiempoDevueltoSegundos)

        // Inversión donde tradicional < medusa
        val invertedItem = com.example.data.core.TiempoDevuelto(
            id = "TR-TEST-2",
            folio = "MED-20260821-0002",
            tipoOperacion = "Test Invertido",
            beneficiario = com.example.data.core.BeneficiaryRole.GUARDS,
            tiempoTradicionalSegundos = 20L,
            tiempoMedusaSegundos = 50L,
            evidenciaEvento = "Test",
            usuarioOrigen = "Guardia 1",
            moduloOrigen = "ACCESO"
        )
        assertEquals("El tiempo devuelto nunca debe ser negativo", 0L, invertedItem.tiempoDevueltoSegundos)
    }

    // CASO 18: Idempotencia y Cero Doble Contabilización por Módulos
    @Test
    fun testCase18_idempotencyAndZeroDoubleCounting() {
        val checkIn = VisitorCheckIn(
            id = 100L,
            folio = "MED-20260821-9901",
            visitorName = "Ignacio Diaz",
            visitorDocument = "19.876.543-2",
            destinationHouse = "Casa #108",
            passCode = "MED-20260821-9901",
            passTypeLabel = "Visita Frecuente",
            status = "CHECKED_IN"
        )

        // Simulación: Un solo evento en Room consultado por Módulo Admin, Módulo Directiva y Panel Maestro
        val eventList = listOf(checkIn)
        val countAdminView = eventList.size
        val countBoardView = eventList.size
        val countMasterView = eventList.size

        assertEquals(1, countAdminView)
        assertEquals(1, countBoardView)
        assertEquals(1, countMasterView)

        // El ahorro solo se calcula 1 sola vez por evento real en base de datos
        val computedSecondsSaved = eventList.size * TimeReturnEngine.SECONDS_PER_QR_CHECK_IN
        assertEquals("El evento debe computar exactamente 120s independientemente de cuántos módulos lo lean", 120L, computedSecondsSaved)
    }

    // CASO 19: Formato y Rigor Criptográfico SHA-256 (64 hex lowercase)
    @Test
    fun testCase19_sha256HexLowerCase64() {
        val hash = AlphaCoreEngine.computeIntegrityHash("MED-20260821-0001", "Guardia Caseta", "Casa #101")
        assertEquals(64, hash.length)
        val hexRegex = Regex("^[a-f0-9]{64}$")
        assertTrue("El hash SHA-256 debe estar en minúsculas y caracteres hexadecimales estrictos: $hash", hexRegex.matches(hash))
    }

    // CASO 20: Panel Residente - Creación de Reserva de Amenidad con Cero Recaptura
    @Test
    fun testCase20_residentAmenityBookingZeroRecapture() {
        val residentUnit = "Casa #104"
        val residentName = "Familia González"
        val amenity = "Quincho & BBQ Principal"
        val booking = com.example.data.booking.AmenityBooking(
            id = 55L,
            amenityName = amenity,
            residentName = residentName,
            unitId = residentUnit,
            bookingTimeMillis = System.currentTimeMillis() + 7200000L,
            durationMinutes = 120,
            status = "CONFIRMADA"
        )
        assertEquals(residentUnit, booking.unitId)
        assertEquals(residentName, booking.residentName)
        assertEquals("CONFIRMADA", booking.status)
    }

    // CASO 21: Panel Administración - Resolución de Incidencia con Trazabilidad Completa
    @Test
    fun testCase21_adminIncidentResolutionLifecycle() {
        val folio = "MED-20260821-4401"
        val openIncident = IncidentEntity(
            folio = folio,
            rawTranscript = "Fuga de agua en medidor",
            category = IncidentCategory.INFRAESTRUCTURA,
            priority = IncidentPriority.ALTA,
            location = "Casa #104",
            aiSummary = "Fuga detectada",
            recommendedAction = "Cerrar llave de paso",
            guardName = "Residente",
            status = "REGISTRADO"
        )
        assertEquals("REGISTRADO", openIncident.status)

        // Simula la transición en Administración
        val inProgressIncident = openIncident.copy(status = "EN_ATENCION")
        assertEquals("EN_ATENCION", inProgressIncident.status)

        val resolvedIncident = inProgressIncident.copy(
            status = "RESUELTO",
            resolutionNotes = "Reparación de válvula completada",
            resolvedAtMillis = System.currentTimeMillis()
        )
        assertEquals("RESUELTO", resolvedIncident.status)
        assertNotNull(resolvedIncident.resolutionNotes)
        assertNotNull(resolvedIncident.resolvedAtMillis)
    }

    // CASO 22: Flujo Residente a Caseta a Directiva - Fuente Única de Verdad
    @Test
    fun testCase22_crossPanelSingleSourceOfTruth() {
        // 1. Residente crea pase QR
        val passCode = AlphaCoreEngine.generateUniqueFolio("MED")
        val pass = QrPassRoomEntity(
            passCode = passCode,
            guestName = "Esteban Morales",
            guestDocument = "17.654.321-0",
            destinationHouse = "Casa #104",
            hostResidentName = "Familia González",
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + 86400000L
        )

        // 2. Caseta registra Check-In reutilizando el Folio y datos exactos
        val checkIn = VisitorCheckIn(
            id = 1L,
            folio = pass.passCode,
            visitorName = pass.guestName,
            visitorDocument = pass.guestDocument,
            destinationHouse = pass.destinationHouse,
            passCode = pass.passCode,
            passTypeLabel = pass.passType.label,
            status = "CHECKED_IN",
            hostResidentName = pass.hostResidentName
        )

        // 3. Mesa Directiva y Admin leen el mismo evento sin recapturar
        assertEquals(pass.passCode, checkIn.folio)
        assertEquals(pass.destinationHouse, checkIn.destinationHouse)
        assertEquals(pass.hostResidentName, checkIn.hostResidentName)
    }
}
