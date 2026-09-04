package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

/**
 * Modelo de datos genérico para opciones de condominio en selectores.
 */
data class CondominiumOption(
    val id: String,
    val name: String,
    val shortTag: String,
    val description: String,
    val totalUnits: Int
)

val DEFAULT_CONDOMINIUM_OPTIONS = listOf(
    CondominiumOption(
        id = "PARAISO",
        name = "Condominio Paraíso",
        shortTag = "PARAÍSO",
        description = "32 Casas · Fracción F4-133",
        totalUnits = 32
    ),
    CondominiumOption(
        id = "PRADOS_1",
        name = "Los Prados 1",
        shortTag = "PRADOS 1",
        description = "Calle 1 y Calle 2 · 94 Casas",
        totalUnits = 94
    ),
    CondominiumOption(
        id = "PRADOS_2",
        name = "Los Prados 2",
        shortTag = "PRADOS 2",
        description = "Calle 3 y Calle 4 · 91 Casas",
        totalUnits = 91
    ),
    CondominiumOption(
        id = "PRADOS_3",
        name = "Los Prados 3",
        shortTag = "PRADOS 3",
        description = "Calle 5 y Calle 6 · 76 Casas",
        totalUnits = 76
    )
)

/**
 * COMPONENTE CONDO SELECTOR (DropdownMenu M3)
 *
 * Permite al guardia o supervisor seleccionar el condominio objetivo (e.g., Paraíso, Los Prados)
 * antes de inicializar cualquier reporte (reporte de turno IA, informe ejecutivo o bitácora),
 * garantizando el aislamiento estricto de los datos.
 */
@Composable
fun CondoSelector(
    selectedCondo: CondoTarget,
    onCondoSelected: (CondoTarget) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Condominio Objetivo del Reporte",
    availableCondos: List<CondoTarget> = CondoTarget.values().toList(),
    onInitializeReport: ((CondoTarget) -> Unit)? = null,
    initializeButtonText: String = "Inicializar Reporte para este Condominio"
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("condo_selector_card"),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila superior informativa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label.uppercase(),
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    color = NavyDark,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "AISLAMIENTO",
                            color = CyanNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Contenedor ancla para el DropdownMenu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("condo_selector_box")
            ) {
                // Gatillo interactivo (Touch target >= 48dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { expanded = !expanded }
                        .testTag("condo_selector_trigger"),
                    shape = RoundedCornerShape(10.dp),
                    color = NavyDark,
                    border = BorderStroke(
                        1.dp,
                        if (expanded) CyanNeon else Color.White.copy(alpha = 0.18f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Apartment,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = selectedCondo.displayName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("selected_condo_text")
                                )
                                Text(
                                    text = "${selectedCondo.locationInfo} • ${selectedCondo.totalCasas} casas",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = GoldPrimary,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = selectedCondo.shortTag,
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = if (expanded) "Cerrar menú" else "Abrir menú",
                                tint = if (expanded) CyanNeon else GoldPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationState)
                            )
                        }
                    }
                }

                // DropdownMenu desplegable
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(NavyCard)
                        .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .widthIn(min = 280.dp)
                        .testTag("condo_selector_dropdown")
                ) {
                    // Encabezado del menú
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "SELECCIONA EL CONDOMINIO",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "El reporte solo incluirá eventos y datos de la propiedad seleccionada.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Opciones de condominios
                    availableCondos.forEach { condo ->
                        val isSelected = condo == selectedCondo
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = condo.displayName,
                                        color = if (isSelected) GoldPrimary else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                    )
                                    Text(
                                        text = "${condo.locationInfo} (${condo.totalCasas} casas)",
                                        color = if (isSelected) CyanNeon else TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            onClick = {
                                onCondoSelected(condo)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (condo == CondoTarget.PARAISO) Icons.Default.Apartment else Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = if (isSelected) GoldPrimary else CyanNeon,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(if (isSelected) NavySurface else Color.Transparent)
                                .testTag("condo_option_${condo.name.lowercase()}")
                        )
                    }
                }
            }

            // Botón opcional para inicializar el reporte directamente desde el componente
            if (onInitializeReport != null) {
                Button(
                    onClick = { onInitializeReport(selectedCondo) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("init_report_condo_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = initializeButtonText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Sobrecarga de CondoSelector usando el modelo genérico [CondominiumOption] e identificador de texto.
 */
@Composable
fun CondoSelectorGeneric(
    selectedCondoId: String,
    onCondoSelected: (CondominiumOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Condominio Objetivo",
    condos: List<CondominiumOption> = DEFAULT_CONDOMINIUM_OPTIONS,
    onInitializeReport: ((CondominiumOption) -> Unit)? = null,
    initializeButtonText: String = "Inicializar Reporte"
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSelected = condos.find { it.id.equals(selectedCondoId, ignoreCase = true) }
        ?: condos.firstOrNull()
        ?: DEFAULT_CONDOMINIUM_OPTIONS.first()

    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_generic_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("generic_condo_selector_card"),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    color = GoldPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${currentSelected.totalUnits} Casas Registradas",
                    color = CyanNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clickable { expanded = !expanded }
                        .testTag("generic_condo_selector_trigger"),
                    shape = RoundedCornerShape(10.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, if (expanded) CyanNeon else Color.White.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apartment,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = currentSelected.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentSelected.description,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotationState)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(NavyCard)
                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .widthIn(min = 260.dp)
                ) {
                    condos.forEach { condo ->
                        val isSel = condo.id.equals(currentSelected.id, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = condo.name,
                                        color = if (isSel) GoldPrimary else Color.White,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = condo.description,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            onClick = {
                                onCondoSelected(condo)
                                expanded = false
                            },
                            trailingIcon = {
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        )
                    }
                }
            }

            if (onInitializeReport != null) {
                Button(
                    onClick = { onInitializeReport(currentSelected) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text(
                        text = initializeButtonText,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
