package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.vecinos.*
import com.example.data.booking.AppDatabase
import com.example.scanner.PassType
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class PortalLoginMode(val label: String, val icon: String) {
    CROQUIS("Plano / Croquis Interactivo", "📐"),
    MANUAL("Acceso Rápido Manual", "⌨️")
}

@Composable
fun MedusaVecinosPortal(
    onSimulateScanInCaseta: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sesion by remember { mutableStateOf<VecinoSesion?>(null) }
    val condominios = remember { MedusaVecinosService.listarCondominios() }

    // Modo de ingreso: Plano Croquis vs Manual
    var loginMode by remember { mutableStateOf(PortalLoginMode.CROQUIS) }

    // Formulario de Login
    var selectedCondoId by remember { mutableStateOf("PRADOS_1") }
    var inputCasa by remember { mutableStateOf("") }
    var inputCalle by remember { mutableStateOf("") }
    var inputCodigo by remember { mutableStateOf("1234") }
    var selectedLoteCroquis by remember { mutableStateOf<LoteCroquis?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Diálogo de Ver Croquis desde sesión activa
    var showCroquisDialog by remember { mutableStateOf(false) }

    // Formulario de Crear Visita Temporal
    var visNombre by remember { mutableStateOf("") }
    var visFecha by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var visDuracionHoras by remember { mutableStateOf(24) }
    var visTipoPase by remember { mutableStateOf(PassType.VISITOR_SINGLE) }
    var visPlacas by remember { mutableStateOf("") }
    var visDocumento by remember { mutableStateOf("") }
    var visMaxEntries by remember { mutableStateOf(1) }
    var visNotas by remember { mutableStateOf("") }
    var isCreatingVisita by remember { mutableStateOf(false) }

    // QR Generado
    var ultimoQrCode by remember { mutableStateOf<String?>(null) }
    var ultimoQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ultimoQrVigenciaMillis by remember { mutableStateOf(0L) }
    var ultimaVisitaInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Lista de Visitas
    var visitasList by remember { mutableStateOf<List<VecinoVisita>>(emptyList()) }
    var isLoadingVisitas by remember { mutableStateOf(false) }
    var residentActiveTab by remember { mutableStateOf(0) } // 0: Visitas, 1: Calendario Amenidades

    // Pre-cargar e inicializar directorio de los 261 lotes del croquis en Room SQLite local
    LaunchedEffect(Unit) {
        MedusaVecinosService.inicializarDirectorioLosPradosSiVacio(context)
    }

    fun cargarVisitasActuales(s: VecinoSesion) {
        scope.launch {
            isLoadingVisitas = true
            val res = MedusaVecinosService.listarVisitasLocales(context, s.idCondominio, s.casa)
            res.onSuccess { visitasList = it }
            isLoadingVisitas = false
        }
    }

    fun ejecutarLoginConLote(lote: LoteCroquis) {
        selectedCondoId = lote.condominioId
        inputCasa = "${lote.numero}"
        inputCalle = lote.calle
        selectedLoteCroquis = lote

        loginError = null
        isLoggingIn = true
        scope.launch {
            val res = MedusaVecinosService.loginCasaLocal(
                context = context,
                idCondominio = lote.condominioId,
                casa = "${lote.numero}",
                codigo = "1234",
                calle = lote.calle
            )
            isLoggingIn = false
            res.onSuccess { newSesion ->
                sesion = newSesion
                cargarVisitasActuales(newSesion)
                Toast.makeText(
                    context,
                    "Bienvenido a Casa ${newSesion.casa} (${newSesion.calle} · ${newSesion.prototipo})",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                loginError = it.message ?: "No se pudo iniciar sesión."
            }
        }
    }

    fun ejecutarLogin() {
        if (selectedCondoId.isBlank() || inputCasa.isBlank() || inputCodigo.isBlank()) {
            loginError = "Ingresa condominio, casa y tu código de acceso."
            return
        }
        loginError = null
        isLoggingIn = true
        scope.launch {
            val res = MedusaVecinosService.loginCasaLocal(
                context = context,
                idCondominio = selectedCondoId,
                casa = inputCasa.trim(),
                codigo = inputCodigo.trim(),
                calle = inputCalle.trim()
            )
            isLoggingIn = false
            res.onSuccess { newSesion ->
                sesion = newSesion
                cargarVisitasActuales(newSesion)
                Toast.makeText(context, "Bienvenido a Casa ${newSesion.casa} (${newSesion.nombreCondominio})", Toast.LENGTH_SHORT).show()
            }.onFailure {
                loginError = it.message ?: "No se pudo iniciar sesión."
            }
        }
    }

    fun ejecutarCrearVisita() {
        val s = sesion ?: return
        if (visNombre.isBlank() || visFecha.isBlank()) {
            Toast.makeText(context, "Ingresa el nombre del visitante.", Toast.LENGTH_SHORT).show()
            return
        }

        isCreatingVisita = true
        scope.launch {
            val res = MedusaVecinosService.crearVisitaLocal(
                context = context,
                idCondominio = s.idCondominio,
                casa = s.casa,
                calle = s.calle,
                nombreVisitante = visNombre.trim(),
                fechaVisita = visFecha.trim(),
                notas = visNotas.trim(),
                duracionHoras = visDuracionHoras,
                tipoPase = visTipoPase,
                placasVehiculo = visPlacas.trim().ifBlank { null },
                documentoVisitante = visDocumento.trim().ifBlank { "Verificar en Caseta" },
                maxEntries = visMaxEntries,
                anfitrion = "Casa ${s.casa} · ${s.calle} (${s.nombreCondominio})"
            )
            isCreatingVisita = false
            res.onSuccess { (idVisita, payload) ->
                val bmp = generateQrBitmapNative(payload)
                ultimoQrCode = payload
                ultimoQrBitmap = bmp
                ultimoQrVigenciaMillis = System.currentTimeMillis() + (visDuracionHoras * 3600 * 1000L)
                ultimaVisitaInfo = Pair(visNombre.trim(), visFecha.trim())

                // Limpiar campos
                visNombre = ""
                visPlacas = ""
                visNotas = ""
                cargarVisitasActuales(s)
                Toast.makeText(context, "✅ Pase temporal guardado en Room SQLite y sincronizado a Firestore", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Error guardando pase: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun compartirPorWhatsApp(visita: VecinoVisita? = null) {
        val s = sesion ?: return
        val targetCode = visita?.passCode ?: ultimoQrCode ?: return
        val targetName = visita?.nombreVisitante ?: ultimaVisitaInfo?.first ?: "Visitante"
        val targetPlacas = visita?.vehiclePlate ?: visPlacas.ifBlank { null }
        val targetVigencia = if (visita != null && visita.validUntilMillis > 0) visita.validUntilMillis else (if (ultimoQrVigenciaMillis > 0) ultimoQrVigenciaMillis else System.currentTimeMillis() + 86400000L)
        val targetEntries = visita?.maxEntries ?: visMaxEntries
        val targetNotas = visita?.notas ?: visNotas

        val textoInvitacion = MedusaVecinosService.generarTextoInvitacion(
            condoNombre = s.nombreCondominio,
            casa = s.casa,
            calle = s.calle,
            nombreVisitante = targetName,
            passCode = targetCode,
            validUntilMillis = targetVigencia,
            placas = targetPlacas,
            maxEntries = targetEntries,
            notas = targetNotas
        )

        val bmp = if (visita != null) generateQrBitmapNative(targetCode) else ultimoQrBitmap
        val imageUri = if (bmp != null) MedusaVecinosService.guardarQrEnCache(context, bmp, targetCode) else null

        try {
            val shareIntent = MedusaVecinosService.crearIntentCompartirPase(context, textoInvitacion, imageUri)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error abriendo selector de compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("medusa_vecinos_portal"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- CABECERA PRINCIPAL ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🪼", fontSize = 22.sp)
                        Text(
                            "MEDUSA VECINOS",
                            color = GoldPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        "Portal de Residentes · Residencial Los Prados",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "2750 Avenida de la Cantera, Santiago de Querétaro, Qro.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = NavyDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("💾", fontSize = 11.sp)
                            Text(
                                "Almacenamiento 100% Local Autónomo (Room SQLite) · Cero cobros externos",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // VISTA 1: LOGIN SI NO HAY SESIÓN ACTIVA
        // ==========================================
        if (sesion == null) {
            item {
                // Selector de Modo de Acceso (Croquis vs Manual)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PortalLoginMode.values().forEach { mode ->
                        val isSelected = loginMode == mode
                        Surface(
                            onClick = { loginMode = mode },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GoldPrimary else NavyCard,
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mode.icon, fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) NavyDark else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (loginMode == PortalLoginMode.CROQUIS) {
                // --- VISTA CROQUIS ARQUITECTÓNICO INTERACTIVO ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(540.dp)
                    ) {
                        LosPradosInteractiveCroquis(
                            selectedLote = selectedLoteCroquis,
                            onLoteSelected = { lote ->
                                selectedLoteCroquis = lote
                                selectedCondoId = lote.condominioId
                                inputCasa = "${lote.numero}"
                                inputCalle = lote.calle
                            },
                            onLoteConfirmedForLogin = { lote ->
                                ejecutarLoginConLote(lote)
                            }
                        )
                    }
                }
            } else {
                // --- VISTA MANUAL TRADICIONAL ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "🔐 Ingreso a tu Casa",
                                color = GoldPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Selector de Condominio
                            Text("Condominio", color = TextMuted, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                condominios.forEach { condo ->
                                    val isSelected = selectedCondoId == condo.id
                                    Surface(
                                        onClick = { selectedCondoId = condo.id },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) GoldPrimary else NavySurface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = condo.nombre.replace("Condominio ", "").replace("Los ", ""),
                                            color = if (isSelected) NavyDark else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Input Casa
                            Text("Número de casa", color = TextMuted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = inputCasa,
                                onValueChange = { inputCasa = it },
                                placeholder = { Text("Ej: 13, 26, 45, 94", color = Color.Gray, fontSize = 13.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = GoldPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vecino_login_casa")
                            )

                            // Input Calle (Opcional)
                            Text("Calle (Opcional)", color = TextMuted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = inputCalle,
                                onValueChange = { inputCalle = it },
                                placeholder = { Text("Ej: Calle 1, Calle 2, Calle 3...", color = Color.Gray, fontSize = 13.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = GoldPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Input Código
                            Text("Código de acceso", color = TextMuted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = inputCodigo,
                                onValueChange = { inputCodigo = it },
                                placeholder = { Text("Código de residente o 1234", color = Color.Gray, fontSize = 13.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = GoldPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vecino_login_codigo")
                            )

                            if (loginError != null) {
                                Surface(
                                    color = ErrorRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ErrorRed)
                                ) {
                                    Text(
                                        text = loginError ?: "",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { ejecutarLogin() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isLoggingIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("vecino_login_button")
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark)
                                } else {
                                    Text("Entrar a Mi Casa", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // VISTA 2: PANEL DE RESIDENTE ACTIVO
            // ==========================================
            val s = sesion!!

            // Barra superior de sesión con datos arquitectónicos del croquis
            item {
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.2f))
                                        .border(1.dp, GoldPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🏡", fontSize = 18.sp)
                                }
                                Column {
                                    Text(
                                        "Casa ${s.casa}${if (s.calle.isNotBlank()) " · ${s.calle}" else ""}",
                                        color = GoldPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        s.nombreCondominio,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    sesion = null
                                    ultimoQrCode = null
                                    ultimoQrBitmap = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.2f), contentColor = ErrorRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Cerrar sesión", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Detalle del modelo arquitectónico
                        if (s.prototipo.isNotBlank() && s.prototipo != "Estándar") {
                            Surface(
                                color = NavyDark,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Prototipo: ${s.prototipo}",
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(
                                        onClick = { showCroquisDialog = true },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("📐 Ver en Plano Croquis", color = GoldPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selector de Pestañas del Residente: Visitas vs Calendario de Amenidades
            item {
                Surface(
                    color = NavyDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { residentActiveTab = 0 },
                            shape = RoundedCornerShape(8.dp),
                            color = if (residentActiveTab == 0) GoldPrimary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = if (residentActiveTab == 0) NavyDark else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Visitas & Pases QR",
                                    fontSize = 11.sp,
                                    fontWeight = if (residentActiveTab == 0) FontWeight.Black else FontWeight.Medium,
                                    color = if (residentActiveTab == 0) NavyDark else Color.White
                                )
                            }
                        }

                        Surface(
                            onClick = { residentActiveTab = 1 },
                            shape = RoundedCornerShape(8.dp),
                            color = if (residentActiveTab == 1) CyanNeon else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = if (residentActiveTab == 1) NavyDark else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Calendario Amenidades",
                                    fontSize = 11.sp,
                                    fontWeight = if (residentActiveTab == 1) FontWeight.Black else FontWeight.Medium,
                                    color = if (residentActiveTab == 1) NavyDark else Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (residentActiveTab == 1) {
                // Calendario interactivo con aislamiento estricto por s.idCondominio
                item {
                    val appDb = remember { AppDatabase.getDatabase(context) }
                    AmenityCalendarView(
                        db = appDb,
                        initialCondominiumId = s.idCondominio,
                        filterUnitId = "Casa ${s.casa}"
                    )
                }
            } else {
                // Formulario de Autorizar Visita Temporal
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                Text(
                                    "Generar Pase Temporal de Visitante",
                                    color = GoldPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = CyanNeon.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "☁️ Firestore Sync",
                                    color = CyanNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text("1. Nombre del Visitante *", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = visNombre,
                            onValueChange = { visNombre = it },
                            placeholder = { Text("Ej: Lic. Carlos Mendoza", color = Color.Gray, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyanNeon
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vecino_vis_nombre")
                        )

                        Text("2. Tipo de Pase:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val tiposPase = remember {
                            listOf(
                                Pair(PassType.VISITOR_SINGLE, "Visita Personal"),
                                Pair(PassType.DELIVERY_SERVICE, "Delivery / Paquete"),
                                Pair(PassType.RESIDENT_PERMANENT, "Frecuente / Familiar"),
                                Pair(PassType.EVENT_GUEST, "Evento")
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(tiposPase) { item ->
                                val tipo = item.first
                                val label = item.second
                                val isSel = visTipoPase == tipo
                                FilterChip(
                                    selected = isSel,
                                    onClick = { visTipoPase = tipo },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = NavyDark,
                                        containerColor = NavyDark,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Text("3. Vigencia Temporal del Pase:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val duracionesPase = remember {
                            listOf(
                                Pair(2, "2 Horas"),
                                Pair(6, "6 Horas"),
                                Pair(12, "12 Horas"),
                                Pair(24, "24 Horas (1 Día)"),
                                Pair(48, "48 Horas (2 Días)"),
                                Pair(72, "72 Horas (3 Días)")
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(duracionesPase) { item ->
                                val horas = item.first
                                val label = item.second
                                val isSel = visDuracionHoras == horas
                                FilterChip(
                                    selected = isSel,
                                    onClick = { visDuracionHoras = horas },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanNeon,
                                        selectedLabelColor = NavyDark,
                                        containerColor = NavyDark,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Placas (opcional)", color = TextMuted, fontSize = 11.sp)
                                OutlinedTextField(
                                    value = visPlacas,
                                    onValueChange = { visPlacas = it },
                                    placeholder = { Text("Ej: ABC-1234", color = Color.Gray, fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = CyanNeon
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Identificación / INE", color = TextMuted, fontSize = 11.sp)
                                OutlinedTextField(
                                    value = visDocumento,
                                    onValueChange = { visDocumento = it },
                                    placeholder = { Text("Doc / RUT", color = Color.Gray, fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = CyanNeon
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Text("Notas o Instrucciones para Caseta (opcional)", color = TextMuted, fontSize = 11.sp)
                        OutlinedTextField(
                            value = visNotas,
                            onValueChange = { visNotas = it },
                            placeholder = { Text("Ej: Viene a reparar calentador solar...", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyanNeon
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { ejecutarCrearVisita() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isCreatingVisita,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("vecino_generar_qr_btn")
                        ) {
                            if (isCreatingVisita) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark)
                            } else {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generar Pase QR y Guardar en Firestore", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Tarjeta de QR Generado
            if (ultimoQrCode != null && ultimoQrBitmap != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, SuccessGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✅ PASE TEMPORAL GENERADO",
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "Room + Firestore OK",
                                        color = SuccessGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Image(
                                    bitmap = ultimoQrBitmap!!.asImageBitmap(),
                                    contentDescription = "QR de acceso",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .padding(10.dp)
                                )
                            }

                            Text(
                                ultimoQrCode ?: "",
                                color = CyanNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "El pase ya está disponible para validación inmediata en caseta de seguridad.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = { compartirPorWhatsApp() },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📲 Compartir Pase (Intent / WhatsApp)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            OutlinedButton(
                                onClick = { onSimulateScanInCaseta(ultimoQrCode ?: "") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                                border = BorderStroke(1.dp, CyanNeon),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🔍 Probar Validación en Caseta", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Lista de Visitas Autorizadas
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📋 Mis Visitas Autorizadas (Base Local + Cloud)",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { cargarVisitasActuales(s) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = GoldPrimary)
                            }
                        }

                        if (isLoadingVisitas) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(24.dp))
                            }
                        } else if (visitasList.isEmpty()) {
                            Text(
                                "No hay visitas autorizadas para esta casa en la base local.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            visitasList.forEach { visita ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                visita.nombreVisitante,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (!visita.vehiclePlate.isNullOrBlank()) {
                                                Surface(
                                                    color = NavyDark,
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        "🚗 ${visita.vehiclePlate}",
                                                        color = GoldPrimary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "Fecha: ${visita.fechaVisita}${if (visita.notas.isNotBlank()) " · ${visita.notas}" else ""}",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "Código: ${visita.passCode}",
                                            color = CyanNeon.copy(alpha = 0.8f),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = if (visita.estado.contains("Usado", ignoreCase = true)) SuccessGreen.copy(alpha = 0.2f) else if (visita.estado.contains("Expirado", ignoreCase = true)) ErrorRed.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = visita.estado,
                                                color = if (visita.estado.contains("Usado", ignoreCase = true)) SuccessGreen else if (visita.estado.contains("Expirado", ignoreCase = true)) ErrorRed else GoldPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { compartirPorWhatsApp(visita) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // --- DIÁLOGO DE PLANO CROQUIS DESDE SESIÓN ACTIVA ---
    if (showCroquisDialog) {
        AlertDialog(
            onDismissRequest = { showCroquisDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📐", fontSize = 20.sp)
                    Text("Croquis Oficial Los Prados", color = GoldPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    LosPradosInteractiveCroquis(
                        selectedLote = selectedLoteCroquis,
                        onLoteSelected = { selectedLoteCroquis = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCroquisDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NavyDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

private fun generateQrBitmapNative(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = MultiFormatWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.parseColor("#1a1a2e") else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
