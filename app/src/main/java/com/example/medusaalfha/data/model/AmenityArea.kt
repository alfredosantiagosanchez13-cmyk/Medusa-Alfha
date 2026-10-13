package com.example.medusaalfha.data.model

data class AmenityArea(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val capacity: Int,
    val startHour: Int = 8,
    val endHour: Int = 22
)

object AmenityCatalog {
    val areas = listOf(
        AmenityArea(
            id = "palapa_alberca",
            name = "Palapa & Alberca",
            description = "Área social para convivios familiares, asador y zona de camastros.",
            iconName = "Pool",
            capacity = 40,
            startHour = 9,
            endHour = 22
        ),
        AmenityArea(
            id = "casa_club",
            name = "Casa Club Principal",
            description = "Salón climatizado para eventos privados y reuniones vecinales.",
            iconName = "House",
            capacity = 60,
            startHour = 10,
            endHour = 23
        ),
        AmenityArea(
            id = "cancha_padel",
            name = "Cancha de Pádel",
            description = "Cancha con iluminación LED y pasto sintético profesional.",
            iconName = "SportsTennis",
            capacity = 4,
            startHour = 6,
            endHour = 22
        ),
        AmenityArea(
            id = "gimnasio",
            name = "Gimnasio Residencial",
            description = "Equipamiento cardiovascular y área de pesas de uso exclusivo.",
            iconName = "FitnessCenter",
            capacity = 15,
            startHour = 6,
            endHour = 22
        )
    )

    fun getAreaById(id: String): AmenityArea? = areas.find { it.id == id }
}
