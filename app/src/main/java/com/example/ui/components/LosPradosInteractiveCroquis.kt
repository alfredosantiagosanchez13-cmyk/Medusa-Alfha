package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.vecinos.LosPradosCroquisData
import com.example.data.vecinos.LoteCroquis
import com.example.data.vecinos.PrototipoCasa
import com.example.ui.theme.*

private val DarkBlueprintBg = Color(0xFF0F172A)
private val StreetAsphaltColor = Color(0xFF1E293B)
private val BorderAccent = Color(0xFF334155)

enum class CroquisTab(val label: String, val condoId: String?) {
    TODOS("Master Plan (Todo)", null),
    PRADOS_1("Condominio 1 (Calles 1-2)", "PRADOS_1"),
    PRADOS_2("Condominio 2 (Calles 3-4)", "PRADOS_2"),
    PRADOS_3("Condominio 3 (Calles 5-6)", "PRADOS_3")
}

@Composable
fun LosPradosInteractiveCroquis(
    selectedLote: LoteCroquis? = null,
    onLoteSelected: (LoteCroquis) -> Unit,
    onLoteConfirmedForLogin: ((LoteCroquis) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(CroquisTab.TODOS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPrototipoFilter by remember { mutableStateOf<PrototipoCasa?>(null) }
    var currentLoteSelected by remember { mutableStateOf(selectedLote) }
    var viewModeGrid by remember { mutableStateOf(false) } // false = Plano Callejero, true = Rejilla rápida

    val allFilteredLotes = remember(activeTab, searchQuery, selectedPrototipoFilter) {
        val base = LosPradosCroquisData.buscarLotes(searchQuery, activeTab.condoId)
        if (selectedPrototipoFilter != null) {
            base.filter { it.prototipo == selectedPrototipoFilter }
        } else {
            base
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBlueprintBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- CABECERA ARQUITECTÓNICA ---
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📐", fontSize = 20.sp)
                        Column {
                            Text(
                                "RESIDENCIAL LOS PRADOS",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                LosPradosCroquisData.DIRECCION_DESARROLLO,
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Botón alternar vista
                    IconButton(
                        onClick = { viewModeGrid = !viewModeGrid },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (viewModeGrid) Icons.Default.Map else Icons.Default.GridView,
                            contentDescription = "Cambiar vista",
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Lema Operativo Oficial
                Surface(
                    color = NavyDark,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⏳", fontSize = 11.sp)
                        Text(
                            LosPradosCroquisData.LEMA_OPERATIVO,
                            color = CyanNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- LEYENDA OFICIAL DE PROTOTIPOS DEL PLANO ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PrototipoLegendChip(
                prototipo = PrototipoCasa.BALI_2R,
                isSelected = selectedPrototipoFilter == PrototipoCasa.BALI_2R,
                onClick = {
                    selectedPrototipoFilter = if (selectedPrototipoFilter == PrototipoCasa.BALI_2R) null else PrototipoCasa.BALI_2R
                },
                modifier = Modifier.weight(1f)
            )
            PrototipoLegendChip(
                prototipo = PrototipoCasa.BALI_3R,
                isSelected = selectedPrototipoFilter == PrototipoCasa.BALI_3R,
                onClick = {
                    selectedPrototipoFilter = if (selectedPrototipoFilter == PrototipoCasa.BALI_3R) null else PrototipoCasa.BALI_3R
                },
                modifier = Modifier.weight(1f)
            )
            PrototipoLegendChip(
                prototipo = PrototipoCasa.TOPACIO_3R,
                isSelected = selectedPrototipoFilter == PrototipoCasa.TOPACIO_3R,
                onClick = {
                    selectedPrototipoFilter = if (selectedPrototipoFilter == PrototipoCasa.TOPACIO_3R) null else PrototipoCasa.TOPACIO_3R
                },
                modifier = Modifier.weight(1f)
            )
        }

        // --- BUSCADOR Y SELECTOR DE CONDOMINIO ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar casa (ej. 13, 45, 94) o calle...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NavySurface,
                unfocusedContainerColor = NavySurface,
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = BorderAccent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("croquis_search_input")
        )

        // Tabs de Condominios
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CroquisTab.values()) { tab ->
                val isSelected = activeTab == tab
                Surface(
                    onClick = { activeTab = tab },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) GoldPrimary else NavySurface,
                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else BorderAccent)
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) NavyDark else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // --- CONTENIDO: VISTA CALLEJERA PLANO O REJILLA ---
        Box(modifier = Modifier.weight(1f)) {
            if (viewModeGrid) {
                // Vista Rejilla Rápida
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 65.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allFilteredLotes, key = { it.idUnico }) { lote ->
                        val isSelected = currentLoteSelected?.idUnico == lote.idUnico
                        LoteMiniTile(
                            lote = lote,
                            isSelected = isSelected,
                            onClick = {
                                currentLoteSelected = lote
                                onLoteSelected(lote)
                            }
                        )
                    }
                }
            } else {
                // Vista Plano Callejero Arquitectónico (Estructura idéntica al croquis)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECCIÓN CONDOMINIO 1
                    if (activeTab == CroquisTab.TODOS || activeTab == CroquisTab.PRADOS_1) {
                        item {
                            CondominioBlueprintBlock(
                                title = "CONDOMINIO 1",
                                subtitle = "Calles 1 y 2 · 94 Casas · Modelos Bali 2R y Bali 3R",
                                accentColor = Color(0xFF4CAF50)
                            ) {
                                // Calle 1
                                StreetSectionCard(
                                    streetName = "CALLE 1 CONDOMINIO 1",
                                    streetSubtitle = "49 Bali 2r · Verde",
                                    leftLabel = "Lado Izquierdo (Borde Área Verde: Casas 1 a 26)",
                                    leftLotes = LosPradosCroquisData.LOTES_PRADOS_1.filter { it.calle == "Calle 1" && it.numero in 1..26 },
                                    rightLabel = "Lado Derecho (Manzana Central: Casas 27 a 49)",
                                    rightLotes = LosPradosCroquisData.LOTES_PRADOS_1.filter { it.calle == "Calle 1" && it.numero in 27..49 },
                                    selectedLote = currentLoteSelected,
                                    onLoteClick = {
                                        currentLoteSelected = it
                                        onLoteSelected(it)
                                    }
                                )

                                Spacer(Modifier.height(10.dp))

                                // Calle 2
                                StreetSectionCard(
                                    streetName = "CALLE 2 CONDOMINIO 1",
                                    streetSubtitle = "28 Bali 2r y 17 Bali 3r",
                                    leftLabel = "Manzana Central (50-65 Bali 2R / 66-72 Bali 3R)",
                                    leftLotes = LosPradosCroquisData.LOTES_PRADOS_1.filter { it.calle == "Calle 2" && (it.numero in 50..72) },
                                    rightLabel = "Manzana Sur (73-74 Bali 3R / 75-86 Bali 2R / 87-94 Bali 3R / 1-22 Bali 2R)",
                                    rightLotes = LosPradosCroquisData.LOTES_PRADOS_1.filter { it.calle == "Calle 2" && (it.numero in 73..94 || it.numero in 1..22) },
                                    selectedLote = currentLoteSelected,
                                    onLoteClick = {
                                        currentLoteSelected = it
                                        onLoteSelected(it)
                                    }
                                )
                            }
                        }
                    }

                    // SECCIÓN CONDOMINIO 2
                    if (activeTab == CroquisTab.TODOS || activeTab == CroquisTab.PRADOS_2) {
                        item {
                            CondominioBlueprintBlock(
                                title = "CONDOMINIO 2",
                                subtitle = "Calles 3 y 4 · 91 Casas · Modelos Bali 2R y Bali 3R",
                                accentColor = Color(0xFFFF9800)
                            ) {
                                // Calle 3
                                StreetSectionCard(
                                    streetName = "CALLE 3 CONDOMINIO 2",
                                    streetSubtitle = "45 Bali 2r / Bali 3r",
                                    leftLabel = "Lado Izquierdo (Casas 23 a 45 Bali 2R)",
                                    leftLotes = LosPradosCroquisData.LOTES_PRADOS_2.filter { it.calle == "Calle 3" && it.numero in 23..45 },
                                    rightLabel = "Lado Derecho (46-55 Bali 2R / 56-67 Bali 3R)",
                                    rightLotes = LosPradosCroquisData.LOTES_PRADOS_2.filter { it.calle == "Calle 3" && (it.numero in 46..67) },
                                    selectedLote = currentLoteSelected,
                                    onLoteClick = {
                                        currentLoteSelected = it
                                        onLoteSelected(it)
                                    }
                                )

                                Spacer(Modifier.height(10.dp))

                                // Calle 4
                                StreetSectionCard(
                                    streetName = "CALLE 4 CONDOMINIO 2",
                                    streetSubtitle = "18 Bali 2r y 28 Bali 3r",
                                    leftLabel = "Manzana Norte (Casas 68 a 77 Bali 2R)",
                                    leftLotes = LosPradosCroquisData.LOTES_PRADOS_2.filter { it.calle == "Calle 4" && it.numero in 68..77 },
                                    rightLabel = "Manzana Sur (Casas 78 a 91 Bali 3R)",
                                    rightLotes = LosPradosCroquisData.LOTES_PRADOS_2.filter { it.calle == "Calle 4" && it.numero in 78..91 },
                                    selectedLote = currentLoteSelected,
                                    onLoteClick = {
                                        currentLoteSelected = it
                                        onLoteSelected(it)
                                    }
                                )
                            }
                        }
                    }

                    // SECCIÓN CONDOMINIO 3
                    if (activeTab == CroquisTab.TODOS || activeTab == CroquisTab.PRADOS_3) {
                        item {
                            CondominioBlueprintBlock(
                                title = "CONDOMINIO 3",
                                subtitle = "Calles 5 y 6 · 76 Casas · Modelo Topacio 3R (Azul)",
                                accentColor = Color(0xFF2196F3)
                            ) {
                                // Calle 5
                                StreetSectionCard(
                                    streetName = "CALLE 5 CONDOMINIO 3",
                                    streetSubtitle = "45 Topacio 3r · Azul",
                                    leftLabel = "Lado Izquierdo (Casas 1 a 22 Topacio 3R)",
                                    leftLotes = LosPradosCroquisData.LOTES_PRADOS_3.filter { it.calle == "Calle 5" && it.numero in 1..22 },
                                    rightLabel = "Lado Derecho (Casas 23 a 45 Topacio 3R)",
                                    rightLotes = LosPradosCroquisData.LOTES_PRADOS_3.filter { it.calle == "Calle 5" && it.numero in 23..45 },
                                    selectedLote = currentLoteSelected,
                                    onLoteClick = {
                                        currentLoteSelected = it
                                        onLoteSelected(it)
                                    }
                                )

                                Spacer(Modifier.height(10.dp))

                                // Calle 6
                                StreetSectionCard(
                                    streetName = "CALLE 6 CONDOMINIO 3",
                                    streetSubtitle = "31 Topacio 3r · Azul",
                                    leftLabel = "Lado Izquierdo (Casas 46 a 68 Topacio 3R)",
                                    leftLotes = LosPradosCroquisData.LOTES_PRADOS_3.filter { it.calle == "Calle 6" && it.numero in 46..68 },
                                    rightLabel = "Lado Derecho (Área Verde: Casas 69 a 76 Topacio 3R)",
                                    rightLotes = LosPradosCroquisData.LOTES_PRADOS_3.filter { it.calle == "Calle 6" && it.numero in 69..76 },
                                    selectedLote = currentLoteSelected,
                                    onLoteClick = {
                                        currentLoteSelected = it
                                        onLoteSelected(it)
                                    }
                                )
                            }
                        }
                    }

                    // SECCIÓN ÁREAS VERDES Y VIALIDADES
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F291E)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🌳", fontSize = 16.sp)
                                    Text(
                                        "ÁREAS VERDES & VIALIDADES PRINCIPALES",
                                        color = Color(0xFF81C784),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    "Calle Principal Área Común · Calle Principal Condominio 3 · Caseta de Seguridad Medusa",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- TARJETA INFERIOR: DETALLE DEL LOTE SELECCIONADO ---
        AnimatedVisibility(visible = currentLoteSelected != null) {
            currentLoteSelected?.let { lote ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, lote.prototipo.color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(lote.prototipo.color.copy(alpha = 0.2f))
                                        .border(1.5.dp, lote.prototipo.color, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${lote.numero}",
                                        fontWeight = FontWeight.Black,
                                        color = lote.prototipo.color,
                                        fontSize = 13.sp
                                    )
                                }
                                Column {
                                    Text(
                                        "Casa ${lote.numero} · ${lote.calle}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "${lote.nombreCondominio} · ${lote.ladoManzana}",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                color = lote.prototipo.color.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, lote.prototipo.color)
                            ) {
                                Text(
                                    text = lote.prototipo.codigo,
                                    color = lote.prototipo.color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (onLoteConfirmedForLogin != null) {
                            Button(
                                onClick = { onLoteConfirmedForLogin(lote) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("btn_confirm_lote_login")
                            ) {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Entrar con Casa ${lote.numero} (${lote.calle})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrototipoLegendChip(
    prototipo: PrototipoCasa,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) prototipo.color.copy(alpha = 0.3f) else NavySurface,
        border = BorderStroke(1.5.dp, if (isSelected) prototipo.color else prototipo.color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(prototipo.color)
            )
            Column {
                Text(
                    text = prototipo.codigo,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${prototipo.recamaras} Rec.",
                    color = TextMuted,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun CondominioBlueprintBlock(
    title: String,
    subtitle: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Column {
                    Text(title, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(subtitle, color = TextMuted, fontSize = 10.sp)
                }
            }
            HorizontalDivider(color = BorderAccent)
            content()
        }
    }
}

@Composable
private fun StreetSectionCard(
    streetName: String,
    streetSubtitle: String,
    leftLabel: String,
    leftLotes: List<LoteCroquis>,
    rightLabel: String,
    rightLotes: List<LoteCroquis>,
    selectedLote: LoteCroquis?,
    onLoteClick: (LoteCroquis) -> Unit
) {
    Surface(
        color = StreetAsphaltColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nombre de la calle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    streetName,
                    color = GoldPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    streetSubtitle,
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }

            // Lado Izquierdo de la Manzana
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(leftLabel, color = TextMuted, fontSize = 9.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    leftLotes.forEach { lote ->
                        LoteHouseBox(
                            lote = lote,
                            isSelected = selectedLote?.idUnico == lote.idUnico,
                            onClick = { onLoteClick(lote) }
                        )
                    }
                }
            }

            // Separador de calle tipo asfalto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Color(0xFF0B132B), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "——————— $streetName ———————",
                    color = Color.Gray.copy(alpha = 0.4f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Lado Derecho de la Manzana
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rightLabel, color = TextMuted, fontSize = 9.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rightLotes.forEach { lote ->
                        LoteHouseBox(
                            lote = lote,
                            isSelected = selectedLote?.idUnico == lote.idUnico,
                            onClick = { onLoteClick(lote) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoteHouseBox(
    lote: LoteCroquis,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color.White else lote.prototipo.color
    val bgColor = if (isSelected) lote.prototipo.color else lote.prototipo.color.copy(alpha = 0.25f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .size(width = 38.dp, height = 48.dp)
            .testTag("lote_${lote.numero}_${lote.calle.replace(" ", "")}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${lote.numero}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(lote.prototipo.color, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun LoteMiniTile(
    lote: LoteCroquis,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) lote.prototipo.color else NavySurface,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color.White else lote.prototipo.color.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${lote.numero}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
            Text(
                text = lote.calle,
                color = if (isSelected) NavyDark else TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = lote.prototipo.codigo,
                color = if (isSelected) Color.White else lote.prototipo.color,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}
