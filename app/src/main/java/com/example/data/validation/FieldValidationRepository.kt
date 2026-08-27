package com.example.data.validation

import kotlinx.coroutines.flow.Flow

/**
 * Repositorio del Checklist de Validación de Campo.
 * Contiene la definición canónica de las 16 pruebas obligatorias en estricto orden.
 * Todas las pruebas inician en estado PENDIENTE.
 */
class FieldValidationRepository(
    private val dao: FieldValidationDao
) {

    val allTestsFlow: Flow<List<FieldValidationTestEntity>> = dao.getAllTestsFlow()

    suspend fun seedInitialTestsIfEmpty() {
        val count = dao.getTestCount()
        if (count == 0) {
            dao.insertInitialTests(getCanonical16Tests())
        }
    }

    suspend fun updateResult(
        testId: String,
        status: String,
        evidence: String,
        observations: String
    ) {
        dao.updateTestResult(testId, status, evidence, observations)
    }

    suspend fun resetAll() {
        dao.resetAllTests()
    }

    companion object {
        fun getCanonical16Tests(): List<FieldValidationTestEntity> {
            return listOf(
                FieldValidationTestEntity(
                    testId = "CAS-01",
                    orderIndex = 1,
                    category = "CASETA",
                    title = "Ingreso Manual en Garita",
                    procedure = "Ingreso manual de visitante de prueba en pantalla de garita.",
                    acceptanceCriteria = "Folio canónico generado con prefijo MED- y estado CHECKED_IN guardado en Room.",
                    evidenceRequired = "Captura de pantalla de la app mostrando folio y hora de entrada.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "CAS-02",
                    orderIndex = 2,
                    category = "CASETA",
                    title = "Registro de Salida en Garita",
                    procedure = "Registro de salida de visitante de prueba tras tiempo de estancia.",
                    acceptanceCriteria = "Estado transiciona a DEPARTED y se almacena checkOutMillis en Room.",
                    evidenceRequired = "Captura de pantalla del cambio de estado en la lista de caseta.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "CAS-03",
                    orderIndex = 3,
                    category = "CASETA",
                    title = "Cálculo de Permanencia",
                    procedure = "Cálculo de permanencia del visitante tras su salida.",
                    acceptanceCriteria = "Minutos calculados exactamente a partir de (salida - entrada) sin recaptura ni proyecciones ficticias.",
                    evidenceRequired = "Captura de pantalla del detalle con tiempo de permanencia visible.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "CAS-04",
                    orderIndex = 4,
                    category = "CASETA",
                    title = "Persistencia en Reinicio de App",
                    procedure = "Reinicio forzado de app con visitantes activos adentro.",
                    acceptanceCriteria = "Al reabrir la app, el visitante permanece en lista como CHECKED_IN sin pérdida de datos.",
                    evidenceRequired = "Captura post-reinicio de la lista de visitantes activos.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "QR-01",
                    orderIndex = 5,
                    category = "PASE QR",
                    title = "Emisión de Pase QR",
                    procedure = "Residente emite pase QR simulado con vigencia de 24h.",
                    acceptanceCriteria = "Código QR generado con datos canónicos, destino y firma SHA-256 intacta.",
                    evidenceRequired = "Imagen/PDF del código QR emitido con código visible.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "QR-02",
                    orderIndex = 6,
                    category = "PASE QR",
                    title = "Escaneo Óptico con Cámara",
                    procedure = "Guardia enfoca con la cámara trasera del dispositivo el código QR físico.",
                    acceptanceCriteria = "Lectura óptica en < 1.5s, autoenfoque correcto y validación de vigencia en pantalla.",
                    evidenceRequired = "Fotografía del dispositivo escaneando físicamente el código.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "QR-03",
                    orderIndex = 7,
                    category = "PASE QR",
                    title = "Rechazo de Pase Reusado / Vencido",
                    procedure = "Intento de reuso de un pase QR ya validado o vencido.",
                    acceptanceCriteria = "La app rechaza el acceso o notifica su estado previo sin generar doble check-in.",
                    evidenceRequired = "Captura de alerta en pantalla de caseta.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "GPS-01",
                    orderIndex = 8,
                    category = "UBICACIÓN GPS",
                    title = "GPS Real en Exterior",
                    procedure = "Registro de rondín o incidencia en exterior a cielo despejado.",
                    acceptanceCriteria = "Coordenadas decimales reales (Lat/Lon) capturadas del sensor GNSS del equipo.",
                    evidenceRequired = "Captura del reporte mostrando coordenadas decimales válidas.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "GPS-02",
                    orderIndex = 9,
                    category = "UBICACIÓN GPS",
                    title = "Fallback GPS en Interior (Sin Señal)",
                    procedure = "Registro de evento en interior, caseta techada o con GPS desactivado.",
                    acceptanceCriteria = "PROHIBIDO inventar coordenadas. El sistema asigna null y muestra 'UBICACIÓN NO DISPONIBLE'.",
                    evidenceRequired = "Captura del reporte mostrando la leyenda literal sin números inventados.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "GPS-03",
                    orderIndex = 10,
                    category = "UBICACIÓN GPS",
                    title = "Manejo de Permisos Denegados",
                    procedure = "Rechazo o revocación de permisos de ubicación en el sistema Android.",
                    acceptanceCriteria = "La app no se cierra (cero crashes), continúa operando y etiqueta 'UBICACIÓN NO DISPONIBLE'.",
                    evidenceRequired = "Captura de pantalla de la app operando con permisos denegados.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "OFF-01",
                    orderIndex = 11,
                    category = "OFFLINE / RECONEXIÓN",
                    title = "Registro en Modo Avión",
                    procedure = "Activar Modo Avión en el dispositivo y registrar 3 operaciones consecutivas.",
                    acceptanceCriteria = "Las 3 operaciones quedan persistidas en Room SQLite con estado PENDIENTE en SyncQueue.",
                    evidenceRequired = "Captura con icono de Modo Avión y lista de pendientes (3 items).",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "OFF-02",
                    orderIndex = 12,
                    category = "OFFLINE / RECONEXIÓN",
                    title = "Idempotencia ante Tap Repetido",
                    procedure = "Múltiples pulsaciones rápidas sobre el botón de guardar sin red.",
                    acceptanceCriteria = "Se genera exactamente UN solo registro canónico en base de datos (idempotencia comprobada).",
                    evidenceRequired = "Captura de la base local verificando ausencia de duplicados.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "OFF-03",
                    orderIndex = 13,
                    category = "OFFLINE / RECONEXIÓN",
                    title = "Sincronización Automática al Reconectar",
                    procedure = "Desactivar Modo Avión / Restablecer conexión Wi-Fi o 4G.",
                    acceptanceCriteria = "La app detecta la red, inicia sincronización y transiciona PENDIENTE → SINCRONIZANDO → SINCRONIZADO.",
                    evidenceRequired = "Captura de la cola mostrando todos los elementos en SINCRONIZADO.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "OFF-04",
                    orderIndex = 14,
                    category = "OFFLINE / RECONEXIÓN",
                    title = "Recuperación tras Apagado Abrupto",
                    procedure = "Apagado abrupto del teléfono con operaciones en cola offline.",
                    acceptanceCriteria = "Al encender el equipo, todos los registros pendientes continúan intactos en Room SQLite.",
                    evidenceRequired = "Captura de la app tras reinicio de hardware mostrando cola recuperada.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "TME-01",
                    orderIndex = 15,
                    category = "TIEMPO DEVUELTO",
                    title = "Tiempo Devuelto Acumulado",
                    procedure = "Consulta del panel de Tiempo Devuelto al concluir la jornada de prueba.",
                    acceptanceCriteria = "Sumatoria real de minutos ahorrados calculados sobre eventos registrados en el piloto.",
                    evidenceRequired = "Captura de pantalla del panel con minutos auditables desplegados.",
                    status = "PENDIENTE"
                ),
                FieldValidationTestEntity(
                    testId = "TME-02",
                    orderIndex = 16,
                    category = "TIEMPO DEVUELTO",
                    title = "Fallback sin Datos Suficientes",
                    procedure = "Consulta en módulo sin eventos o con datos incompletos.",
                    acceptanceCriteria = "Despliegue obligatorio de 'DATOS INSUFICIENTES PARA CÁLCULO' sin inventar porcentajes.",
                    evidenceRequired = "Captura de pantalla del mensaje de fallback.",
                    status = "PENDIENTE"
                )
            )
        }
    }
}
