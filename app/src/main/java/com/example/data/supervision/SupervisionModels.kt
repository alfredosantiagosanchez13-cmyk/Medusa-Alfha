package com.example.data.supervision

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FASE 17: SUPERVISIÓN TÁCTICA Y RONDINES INTELIGENTES
 * Modelos de datos para Rutas, Puntos de Control, Validación GPS y Estados de Inspección.
 */

data class SupervisionCheckpoint(
    val id: String,
    val name: String,
    val area: String,
    val targetLat: Double,
    val targetLng: Double,
    val sequence: Int,
    val checklistCriteria: List<String>,
    val criticalRiskFactors: String
)

data class SupervisionRoute(
    val id: String,
    val name: String,
    val code: String,
    val description: String,
    val targetDurationMinutes: Int,
    val checkpoints: List<SupervisionCheckpoint>
)

enum class CheckpointInspectionStatus(val label: String, val isFinalized: Boolean) {
    PENDIENTE("Pendiente", false),
    OPTIMO("Óptimo", true),
    REGULAR("Regular", true),
    CRITICO("Crítico", true),
    OMITIDO("Omitido", true),
    FUERA_UBICACION("Fuera de Ubicación", true)
}

data class GpsValidationResult(
    val distanceMeters: Float,
    val isWithinTolerance: Boolean,
    val statusLabel: String,
    val targetCoordinatesFormatted: String,
    val capturedCoordinatesFormatted: String,
    val accuracyMeters: Float?
)

/**
 * Catálogo maestro de Rutas Predefinidas de Supervisión y Rondines.
 */
object SupervisionRoutesCatalog {

    val ROUTE_PERIMETER = SupervisionRoute(
        id = "ROUTE-PERIMETER-01",
        name = "Ronda Perimetral & Cerco Eléctrico",
        code = "PERIM",
        description = "Inspección de barreras físicas, cercos electrificados, iluminación perimetral y cámaras.",
        targetDurationMinutes = 25,
        checkpoints = listOf(
            SupervisionCheckpoint(
                id = "CP-01",
                name = "Garita Principal (Caseta 1)",
                area = "Acceso Principal",
                targetLat = -33.43720,
                targetLng = -70.65060,
                sequence = 1,
                checklistCriteria = listOf("Bitácora de visitas al día", "Cámaras ANPR activas", "Barrera vehicular operativa", "Alarma de pánico conectada"),
                criticalRiskFactors = "Falla en barreras o sistema ANPR inoperativo"
            ),
            SupervisionCheckpoint(
                id = "CP-02",
                name = "Perímetro Norte & Cerco Eléctrico",
                area = "Perímetro Norte",
                targetLat = -33.43650,
                targetLng = -70.65010,
                sequence = 2,
                checklistCriteria = listOf("Voltaje de cerco > 8.5 kV", "Sin ramas rozando alambres", "Luminarias LED perimetrales encendidas"),
                criticalRiskFactors = "Corte de cerco eléctrico o pérdida de energización"
            ),
            SupervisionCheckpoint(
                id = "CP-03",
                name = "Portón Vehicular Poniente",
                area = "Acceso Secundario",
                targetLat = -33.43680,
                targetLng = -70.65150,
                sequence = 3,
                checklistCriteria = listOf("Cierre electromagnético firme", "Sensor fotoeléctrico limpio", "Intercomunicador operativo"),
                criticalRiskFactors = "Portón trabado o chapa forzada"
            ),
            SupervisionCheckpoint(
                id = "CP-04",
                name = "Perímetro Sur & Calle Interior",
                area = "Perímetro Sur",
                targetLat = -33.43790,
                targetLng = -70.65110,
                sequence = 4,
                checklistCriteria = listOf("Muro perimetral sin fisuras", "Concertina alineada", "Reflectores de movimiento funcionales"),
                criticalRiskFactors = "Intrusión visible o concertina vandalizada"
            ),
            SupervisionCheckpoint(
                id = "CP-05",
                name = "Sector Estacionamiento Visitas",
                area = "Vialidad Interna",
                targetLat = -33.43760,
                targetLng = -70.65030,
                sequence = 5,
                checklistCriteria = listOf("Autos en cajones autorizados", "Sin vehículos sospechosos pernoctando", "Iluminación general 100%"),
                criticalRiskFactors = "Vehículo no registrado sin tarjetón o cristal roto"
            ),
            SupervisionCheckpoint(
                id = "CP-06",
                name = "Cámaras Perimetrales Este",
                area = "Perímetro Este",
                targetLat = -33.43710,
                targetLng = -70.64960,
                sequence = 6,
                checklistCriteria = listOf("Domo PTZ con movimiento fluido", "Lente libre de polvo/telarañas", "Gabinete NVR cerrado"),
                criticalRiskFactors = "Pérdida de señal de video o gabinete abierto"
            )
        )
    )

    val ROUTE_CRITICAL_INFRASTRUCTURE = SupervisionRoute(
        id = "ROUTE-INFRA-02",
        name = "Ronda de Instalaciones Críticas",
        code = "INFRA",
        description = "Verificación de subestación eléctrica, sala de bombas hidroneumáticas, generador y calderas.",
        targetDurationMinutes = 20,
        checkpoints = listOf(
            SupervisionCheckpoint(
                id = "CP-101",
                name = "Subestación & Tableros Eléctricos",
                area = "Servicios Generales",
                targetLat = -33.43630,
                targetLng = -70.65080,
                sequence = 1,
                checklistCriteria = listOf("Puerta con candado de seguridad", "Sin olor a recalentamiento", "Voltaje trifásico nominal", "Extintor CO2 vigente"),
                criticalRiskFactors = "Sobrecalentamiento de tablero o chisporroteo"
            ),
            SupervisionCheckpoint(
                id = "CP-102",
                name = "Sala de Bombas e Hidroneumático",
                area = "Servicios Hidráulicos",
                targetLat = -33.43670,
                targetLng = -70.65090,
                sequence = 2,
                checklistCriteria = listOf("Presión de red entre 45-60 PSI", "Sin fugas visibles en sellos", "Bomba de respaldo en automático"),
                criticalRiskFactors = "Pérdida total de presión o inundación en sala"
            ),
            SupervisionCheckpoint(
                id = "CP-103",
                name = "Generador Eléctrico de Respaldo",
                area = "Generación Auxiliar",
                targetLat = -33.43640,
                targetLng = -70.65120,
                sequence = 3,
                checklistCriteria = listOf("Nivel de combustible > 85%", "Batería de arranque cargada", "Transferencia automática lista"),
                criticalRiskFactors = "Falla de batería de arranque o fuga de diésel"
            ),
            SupervisionCheckpoint(
                id = "CP-104",
                name = "Cuarto de Basuras & Reciclaje",
                area = "Aseo y Ornato",
                targetLat = -33.43810,
                targetLng = -70.65050,
                sequence = 4,
                checklistCriteria = listOf("Contenedores cerrados", "Extractores de aire funcionando", "Sin acumulación fuera de tolvas"),
                criticalRiskFactors = "Riesgo de plagas o conato de fuego en residuos"
            ),
            SupervisionCheckpoint(
                id = "CP-105",
                name = "Azotea & Tanques de Reserva",
                area = "Cubierta Superior",
                targetLat = -33.43700,
                targetLng = -70.65040,
                sequence = 5,
                checklistCriteria = listOf("Escotilla con llave", "Tapas de estanques herméticas", "Luces de balizamiento aeronáutico"),
                criticalRiskFactors = "Acceso no autorizado a azotea o desborde de tanque"
            )
        )
    )

    val ROUTE_AMENITIES = SupervisionRoute(
        id = "ROUTE-AMENITIES-03",
        name = "Ronda de Amenidades & Convivencia",
        code = "AMEN",
        description = "Revisión de Club House, piscina, gimnasio, quinchos y áreas recreativas.",
        targetDurationMinutes = 18,
        checkpoints = listOf(
            SupervisionCheckpoint(
                id = "CP-201",
                name = "Club House & Salón de Eventos",
                area = "Amenidades",
                targetLat = -33.43740,
                targetLng = -70.65040,
                sequence = 1,
                checklistCriteria = listOf("Mobiliario en orden", "Luces apagadas post-reserva", "Puertas cerradas", "Control de ruido"),
                criticalRiskFactors = "Daños a instalaciones o evento clandestino sin reserva"
            ),
            SupervisionCheckpoint(
                id = "CP-202",
                name = "Área de Piscina & Solárium",
                area = "Zona Húmeda",
                targetLat = -33.43730,
                targetLng = -70.65010,
                sequence = 2,
                checklistCriteria = listOf("Reja perimetral de piscina con pestillo alto", "Salvavidas en posición", "Químicos guardados bajo llave"),
                criticalRiskFactors = "Niños sin supervisión o portón de alberca abierto"
            ),
            SupervisionCheckpoint(
                id = "CP-203",
                name = "Gimnasio & Sala Fitness",
                area = "Deportes",
                targetLat = -33.43750,
                targetLng = -70.65070,
                sequence = 3,
                checklistCriteria = listOf("Máquinas con mantenimiento al día", "Aire acondicionado apagado al cierre", "Botiquín equipado"),
                criticalRiskFactors = "Máquina rota con riesgo de accidente"
            ),
            SupervisionCheckpoint(
                id = "CP-204",
                name = "Canchas de Pádel & Tenis",
                area = "Recreación",
                targetLat = -33.43800,
                targetLng = -70.64990,
                sequence = 4,
                checklistCriteria = listOf("Redes tensas", "Cristales templados íntegros", "Temporizadores de iluminación"),
                criticalRiskFactors = "Cristal astillado o reflectores encendidos fuera de horario"
            ),
            SupervisionCheckpoint(
                id = "CP-205",
                name = "Parque Infantil & Áreas Verdes",
                area = "Juegos Infantiles",
                targetLat = -33.43770,
                targetLng = -70.65080,
                sequence = 5,
                checklistCriteria = listOf("Juegos de madera sin astillas", "Columpios seguros", "Riego programado"),
                criticalRiskFactors = "Juego infantil roto con peligro de caída"
            )
        )
    )

    val ALL_ROUTES = listOf(
        ROUTE_PERIMETER,
        ROUTE_CRITICAL_INFRASTRUCTURE,
        ROUTE_AMENITIES
    )

    fun getRouteById(id: String): SupervisionRoute {
        return ALL_ROUTES.find { it.id == id } ?: ROUTE_PERIMETER
    }
}
