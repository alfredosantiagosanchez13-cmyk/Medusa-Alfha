package com.example.data.vecinos

import androidx.compose.ui.graphics.Color

enum class PrototipoCasa(
    val codigo: String,
    val nombre: String,
    val recamaras: Int,
    val color: Color,
    val descripcion: String
) {
    BALI_2R(
        codigo = "Bali 2r",
        nombre = "Modelo Bali (2 Recámaras)",
        recamaras = 2,
        color = Color(0xFF2E7D32), // Verde esmeralda idéntico al plano
        descripcion = "Prototipo 2 recámaras con jardín posterior y estacionamiento frontal"
    ),
    BALI_3R(
        codigo = "Bali 3r",
        nombre = "Modelo Bali (3 Recámaras)",
        recamaras = 3,
        color = Color(0xFFE65100), // Naranja cálido idéntico al plano
        descripcion = "Prototipo ampliado de 3 recámaras en manzanas principales"
    ),
    TOPACIO_3R(
        codigo = "Topacio 3r",
        nombre = "Modelo Topacio (3 Recámaras)",
        recamaras = 3,
        color = Color(0xFF1565C0), // Azul zafiro idéntico al plano
        descripcion = "Prototipo exclusivo Condominio 3 con acabados de alta gama"
    )
}

data class LoteCroquis(
    val numero: Int,
    val condominioId: String,
    val nombreCondominio: String,
    val calle: String,
    val prototipo: PrototipoCasa,
    val ladoManzana: String,
    val notasUbicacion: String = ""
) {
    val idUnico: String get() = "${condominioId}-${calle.replace(" ", "")}-CASA${numero}"
    val labelCasa: String get() = "Casa $numero"
    val labelCompleto: String get() = "Casa $numero · $calle (${prototipo.codigo})"
}

/**
 * Catálogo 100% exacto basado en el croquis arquitectónico oficial de RESIDENCIAL LOS PRADOS:
 * 2750 Avenida de la Cantera, Santiago de Querétaro, Qro.
 *
 * Estructura de Condominios y Calles:
 * - CONDOMINIO 1:
 *    • Calle 1 (49 Bali 2r): Lotes 1..26 (izq), 27..49 (der)
 *    • Calle 2 (28 Bali 2r y 17 Bali 3r): Lotes 50..65 (Bali 2r), 66..72 (Bali 3r), 73..74 (Bali 3r), 75..86 (Bali 2r), 87..94 (Bali 3r), 1..22 (Bali 2r)
 * - CONDOMINIO 2:
 *    • Calle 3 (45 Bali 2r / Bali 3r): Lotes 23..45 (Bali 2r), 46..55 (Bali 2r), 56..67 (Bali 3r)
 *    • Calle 4 (18 Bali 2r y 28 Bali 3r): Lotes 68..77 (Bali 2r), 78..91 (Bali 3r)
 * - CONDOMINIO 3:
 *    • Calle 5 (45 Topacio 3r): Lotes 1..22 (Topacio 3r), 23..45 (Topacio 3r)
 *    • Calle 6 (31 Topacio 3r): Lotes 46..68 (Topacio 3r), 69..76 (Topacio 3r)
 */
object LosPradosCroquisData {

    val DIRECCION_DESARROLLO = "2750 Avenida de la Cantera, Santiago de Querétaro, Qro."
    val LEMA_OPERATIVO = "TIEMPO = FAMILIA · MEDUSA ALFHA"

    val LOTES_PRADOS_1: List<LoteCroquis> by lazy {
        val list = mutableListOf<LoteCroquis>()

        // Calle 1: Lado Izquierdo (1 a 26 - Bali 2r)
        for (i in 1..26) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 1",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Lado Izquierdo (Borde Área Verde)",
                    notasUbicacion = "Frente a Calle 1 Condominio 1"
                )
            )
        }

        // Calle 1: Lado Derecho (27 a 49 - Bali 2r)
        for (i in 27..49) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 1",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Lado Derecho (Manzana Central)",
                    notasUbicacion = "Colindancia dorsal con Calle 2"
                )
            )
        }

        // Calle 2: Manzana Central (50 a 65 Bali 2r, 66 a 72 Bali 3r)
        for (i in 50..65) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 2",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Manzana Central (Tramo Bali 2R)",
                    notasUbicacion = "Frente a Calle 2"
                )
            )
        }
        for (i in 66..72) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 2",
                    prototipo = PrototipoCasa.BALI_3R,
                    ladoManzana = "Manzana Central (Tramo Bali 3R)",
                    notasUbicacion = "Frente a Calle 2 cerca de área verde"
                )
            )
        }

        // Calle 2: Lado Fondo / Manzana Sur (73 a 74 Bali 3r, 75 a 86 Bali 2r, 87 a 94 Bali 3r)
        for (i in 73..74) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 2",
                    prototipo = PrototipoCasa.BALI_3R,
                    ladoManzana = "Manzana Sur (Esquina)",
                    notasUbicacion = "Acceso Calle 2"
                )
            )
        }
        for (i in 75..86) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 2",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Manzana Sur (Tramo Central)",
                    notasUbicacion = "Frente a Calle 2"
                )
            )
        }
        for (i in 87..94) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 2",
                    prototipo = PrototipoCasa.BALI_3R,
                    ladoManzana = "Manzana Sur (Fondo)",
                    notasUbicacion = "Fondo de Calle 2"
                )
            )
        }

        // Calle 2: Manzana Lateral Sur (1 al 22 Bali 2r)
        for (i in 1..22) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_1",
                    nombreCondominio = "Los Prados 1",
                    calle = "Calle 2",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Manzana Sur Lateral",
                    notasUbicacion = "Colindancia divisoria Condominio 2"
                )
            )
        }

        list
    }

    val LOTES_PRADOS_2: List<LoteCroquis> by lazy {
        val list = mutableListOf<LoteCroquis>()

        // Calle 3: Lado Izquierdo (23 a 45 - Bali 2r)
        for (i in 23..45) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_2",
                    nombreCondominio = "Los Prados 2",
                    calle = "Calle 3",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Lado Izquierdo Calle 3",
                    notasUbicacion = "Frente a Calle 3 Condominio 2"
                )
            )
        }

        // Calle 3: Lado Derecho (46 a 55 Bali 2r, 56 a 67 Bali 3r)
        for (i in 46..55) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_2",
                    nombreCondominio = "Los Prados 2",
                    calle = "Calle 3",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Manzana Central Calle 3 (Bali 2R)",
                    notasUbicacion = "Colindancia dorsal con Calle 4"
                )
            )
        }
        for (i in 56..67) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_2",
                    nombreCondominio = "Los Prados 2",
                    calle = "Calle 3",
                    prototipo = PrototipoCasa.BALI_3R,
                    ladoManzana = "Manzana Central Calle 3 (Bali 3R)",
                    notasUbicacion = "Tramo posterior Calle 3"
                )
            )
        }

        // Calle 4: Lado Norte (68 a 77 Bali 2r)
        for (i in 68..77) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_2",
                    nombreCondominio = "Los Prados 2",
                    calle = "Calle 4",
                    prototipo = PrototipoCasa.BALI_2R,
                    ladoManzana = "Manzana Central Calle 4 (Bali 2R)",
                    notasUbicacion = "Frente a Calle 4"
                )
            )
        }

        // Calle 4: Lado Sur (78 a 91 Bali 3r)
        for (i in 78..91) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_2",
                    nombreCondominio = "Los Prados 2",
                    calle = "Calle 4",
                    prototipo = PrototipoCasa.BALI_3R,
                    ladoManzana = "Manzana Sur Calle 4 (Bali 3R)",
                    notasUbicacion = "Colindancia con Condominio 3"
                )
            )
        }

        list
    }

    val LOTES_PRADOS_3: List<LoteCroquis> by lazy {
        val list = mutableListOf<LoteCroquis>()

        // Calle 5: Lado Izquierdo (1 a 22 - Topacio 3r)
        for (i in 1..22) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_3",
                    nombreCondominio = "Los Prados 3",
                    calle = "Calle 5",
                    prototipo = PrototipoCasa.TOPACIO_3R,
                    ladoManzana = "Lado Poniente Calle 5 (Topacio 3R)",
                    notasUbicacion = "Frente a Calle 5 Condominio 3"
                )
            )
        }

        // Calle 5: Lado Derecho (23 a 45 - Topacio 3r)
        for (i in 23..45) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_3",
                    nombreCondominio = "Los Prados 3",
                    calle = "Calle 5",
                    prototipo = PrototipoCasa.TOPACIO_3R,
                    ladoManzana = "Manzana Central Calle 5 (Topacio 3R)",
                    notasUbicacion = "Colindancia dorsal con Calle 6"
                )
            )
        }

        // Calle 6: Lado Izquierdo (46 a 68 - Topacio 3r)
        for (i in 46..68) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_3",
                    nombreCondominio = "Los Prados 3",
                    calle = "Calle 6",
                    prototipo = PrototipoCasa.TOPACIO_3R,
                    ladoManzana = "Manzana Central Calle 6 (Topacio 3R)",
                    notasUbicacion = "Frente a Calle 6"
                )
            )
        }

        // Calle 6: Lado Derecho (69 a 76 - Topacio 3r)
        for (i in 69..76) {
            list.add(
                LoteCroquis(
                    numero = i,
                    condominioId = "PRADOS_3",
                    nombreCondominio = "Los Prados 3",
                    calle = "Calle 6",
                    prototipo = PrototipoCasa.TOPACIO_3R,
                    ladoManzana = "Límite Perimetral Oriente (Área Verde)",
                    notasUbicacion = "Fondo Calle 6"
                )
            )
        }

        list
    }

    val TODOS_LOS_LOTES: List<LoteCroquis> by lazy {
        LOTES_PRADOS_1 + LOTES_PRADOS_2 + LOTES_PRADOS_3
    }

    fun obtenerLotesPorCondominio(condominioId: String): List<LoteCroquis> {
        return when (condominioId) {
            "PRADOS_1" -> LOTES_PRADOS_1
            "PRADOS_2" -> LOTES_PRADOS_2
            "PRADOS_3" -> LOTES_PRADOS_3
            else -> TODOS_LOS_LOTES
        }
    }

    fun buscarLotes(query: String, condominioId: String? = null): List<LoteCroquis> {
        val base = if (condominioId != null) obtenerLotesPorCondominio(condominioId) else TODOS_LOS_LOTES
        if (query.isBlank()) return base
        val q = query.trim().lowercase()
        return base.filter { lote ->
            lote.numero.toString().contains(q) ||
                    lote.calle.lowercase().contains(q) ||
                    lote.prototipo.nombre.lowercase().contains(q) ||
                    lote.nombreCondominio.lowercase().contains(q) ||
                    lote.ladoManzana.lowercase().contains(q)
        }
    }
}
