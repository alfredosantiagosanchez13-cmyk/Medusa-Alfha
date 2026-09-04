package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CondoSelector
import com.example.ui.components.CondoTarget

@Composable
fun ReporteIaTacticoScreen(
    reporteTextoIa: String, // Aquí pasas el texto que generó Gemini
    initialCondo: CondoTarget = CondoTarget.PARAISO,
    onCondoSelected: (CondoTarget) -> Unit = {},
    onInitializeReport: ((CondoTarget) -> Unit)? = null,
    onCompartirClick: () -> Unit
) {
    // Definición de paleta neón táctica Medusa
    val darkBackground = Color(0xFF111625) // Azul muy oscuro/negro
    val neonYellow = Color(0xFFFFD700)      // Amarillo Oro Medusa
    val neonBlue = Color(0xFF00E5FF)        // Azul Neón Guardias

    var currentCondo by remember { mutableStateOf(initialCondo) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(16.dp)
            .testTag("reporte_ia_tactico_screen")
    ) {
        // Encabezado de la Sección de Inteligencia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✨ INFORME DE TURNO IA",
                color = neonYellow,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Red.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color.Red)
            ) {
                Text(
                    text = "MEDUSA AUDIT",
                    color = Color.Red,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selector de Condominio con DropdownMenu (Paraíso, Los Prados 1, 2, 3)
        CondoSelector(
            selectedCondo = currentCondo,
            onCondoSelected = { condo ->
                currentCondo = condo
                onCondoSelected(condo)
            },
            onInitializeReport = onInitializeReport?.let { action ->
                { condo -> action(condo) }
            },
            initializeButtonText = "Generar Informe IA para ${currentCondo.shortTag}"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Contenedor del Reporte con scroll dinámico
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("reporte_ia_content_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2333)),
            border = BorderStroke(1.dp, neonBlue)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = reporteTextoIa,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.testTag("reporte_ia_text")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón de Acción Principal: Exportar a la Administración
        Button(
            onClick = onCompartirClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("enviar_reporte_admin_button"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = neonYellow)
        ) {
            Text(
                text = "ENVIAR REPORTE A ADMINISTRACIÓN",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
