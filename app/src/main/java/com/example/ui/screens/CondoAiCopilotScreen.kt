package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.RbacEvaluationResult
import com.example.auth.RbacManager
import com.example.auth.SystemPermission
import com.example.auth.UserRole
import androidx.compose.material.icons.filled.History
import com.example.data.booking.AppDatabase
import com.example.data.chat.AiGuardChatLog
import com.example.ui.components.PersistentChatHistoryView
import com.example.ui.components.VoiceIncidentLoggerComponent
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val content: String,
    val activeRole: UserRole,
    val isAccessDenied: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Composable
fun CondoAiCopilotScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeRole by remember { mutableStateOf(UserRole.GUARD) }
    var inputText by remember { mutableStateOf("") }
    var isGeneratingResponse by remember { mutableStateOf(false) }
    var activeSubMode by remember { mutableStateOf("CHAT") } // "CHAT" or "VOICE_LOGGER"

    // Memory buffer storing chat messages in active session memory
    val chatHistory = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "AI",
                content = "¡Hola! Soy el Copiloto AI de Garita MEDUSA ALFHA.\n\nSujeto a la política de **Control de Acceso Basado en Roles (RBAC)**:\n• **Rol Actual:** Guardia de Caseta\n• **Comandos Permitidos:** Actualización de estado de visitantes y gestión de alertas de emergencia.",
                activeRole = UserRole.GUARD
            )
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("condo_ai_copilot_screen")
    ) {
        // AI Header & RBAC Policy Status Indicator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (activeRole == UserRole.GUARD) GoldPrimary.copy(alpha = 0.5f) else CyanNeon.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (activeRole == UserRole.GUARD) GoldPrimary.copy(alpha = 0.2f) else CyanNeon.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activeRole == UserRole.GUARD) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CONTROL DE ACCESO RBAC - COPILOTO AI",
                                color = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = activeRole.displayName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = (if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeRole == UserRole.GUARD) GoldPrimary.copy(alpha = 0.5f) else CyanNeon.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (activeRole == UserRole.GUARD) "Restringido (Guardia)" else "Acceso Total (Admin)",
                                color = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = activeRole.description,
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Role Selector Switcher (Guardia vs. Administrador)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = {
                            if (activeRole != UserRole.GUARD) {
                                activeRole = UserRole.GUARD
                                chatHistory.add(
                                    ChatMessage(
                                        sender = "AI",
                                        content = "🛡️ **Perfil Cambiado a Guardia de Caseta**\n\nConjunto de comandos restringido a:\n• Actualizar estado de visitantes (CHECKED-IN / DEPARTED)\n• Gestión y respuesta de Alertas de Pánico\n\n*Las configuraciones de sistema quedan denegadas.*",
                                        activeRole = UserRole.GUARD
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (activeRole == UserRole.GUARD) GoldPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_guard_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (activeRole == UserRole.GUARD) NavyDark else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Rol Guardia",
                                color = if (activeRole == UserRole.GUARD) NavyDark else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (activeRole != UserRole.ADMIN) {
                                activeRole = UserRole.ADMIN
                                chatHistory.add(
                                    ChatMessage(
                                        sender = "AI",
                                        content = "🔑 **Perfil Cambiado a Administrador de Sistema**\n\nAcceso desbloqueado para:\n• Configuración de parámetros de cámara y escáner\n• Modificación de políticas de seguridad y listas negras\n• Auditorías completas y reset del sistema",
                                        activeRole = UserRole.ADMIN
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (activeRole == UserRole.ADMIN) CyanNeon else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("role_admin_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (activeRole == UserRole.ADMIN) NavyDark else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Rol Administrador",
                                color = if (activeRole == UserRole.ADMIN) NavyDark else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                // Mode Selector Switcher: Chat vs. Dictado por Voz
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { activeSubMode = "CHAT" },
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeSubMode == "CHAT") NavyCard else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeSubMode == "CHAT") (if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_chat_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (activeSubMode == "CHAT") (if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon) else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "💬 Chat RBAC",
                                color = if (activeSubMode == "CHAT") Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = { activeSubMode = "VOICE_LOGGER" },
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeSubMode == "VOICE_LOGGER") NavyCard else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeSubMode == "VOICE_LOGGER") GoldPrimary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_voice_logger_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (activeSubMode == "VOICE_LOGGER") GoldPrimary else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🎙️ Dictado Voz (Hands-Free)",
                                color = if (activeSubMode == "VOICE_LOGGER") Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeSubMode == "VOICE_LOGGER") {
            // Voice Incident Logger View
            VoiceIncidentLoggerComponent(
                modifier = Modifier.weight(1f),
                onIncidentLogged = { incident ->
                    chatHistory.add(
                        ChatMessage(
                            sender = "USER",
                            content = "🎙️ [DICTADO POR VOZ]: \"${incident.rawTranscript}\"",
                            activeRole = activeRole
                        )
                    )
                    chatHistory.add(
                        ChatMessage(
                            sender = "AI",
                            content = "✅ **Incidencia de Voz Categorizada por IA**\n\n" +
                                    "• **Categoría:** ${incident.category.iconName} ${incident.category.displayName}\n" +
                                    "• **Prioridad:** ${incident.priority.displayName}\n" +
                                    "• **Ubicación:** ${incident.location}\n" +
                                    "• **Acción:** ${incident.recommendedAction}",
                            activeRole = activeRole
                        )
                    )
                }
            )
        } else {
            // Quick Suggestion Chips according to role permissions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val commandSuggestions = if (activeRole == UserRole.GUARD) {
                listOf(
                    "Actualizar estado a Checked-In",
                    "Gestionar alerta de pánico",
                    "Configurar parámetros del sistema" // Will trigger RBAC Access Denied for Guard
                )
            } else {
                listOf(
                    "Configurar parámetros de cámara",
                    "Modificar política de listas negras",
                    "Auditoría de base de datos"
                )
            }

            commandSuggestions.forEach { command ->
                Surface(
                    onClick = {
                        inputText = command
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = NavyCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = command,
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chat Conversation Memory Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatHistory, key = { it.id }) { msg ->
                ChatMessageBubble(msg)
            }

            if (isGeneratingResponse) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Evaluando comando con política RBAC de ${activeRole.displayName}...",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Field & Send Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = if (activeRole == UserRole.GUARD) "Comandos de visitas o alertas..." else "Comandos de configuración o sistema...",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                    unfocusedBorderColor = Color(0xFF374151),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank() && !isGeneratingResponse) {
                        val userQuery = inputText.trim()
                        inputText = ""

                        chatHistory.add(
                            ChatMessage(
                                sender = "USER",
                                content = userQuery,
                                activeRole = activeRole
                            )
                        )

                        isGeneratingResponse = true

                        scope.launch {
                            delay(800) // Simulate streaming AI response with RBAC policy evaluation
                            val (aiAnswer, isDenied) = processCommandWithRbacPolicy(userQuery, activeRole)
                            chatHistory.add(
                                ChatMessage(
                                    sender = "AI",
                                    content = aiAnswer,
                                    activeRole = activeRole,
                                    isAccessDenied = isDenied
                                )
                            )
                            isGeneratingResponse = false
                        }
                    }
                },
                enabled = inputText.isNotBlank() && !isGeneratingResponse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeRole == UserRole.GUARD) GoldPrimary else CyanNeon,
                    contentColor = NavyDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("send_ai_chat_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Enviar",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    chatHistory.clear()
                    chatHistory.add(
                        ChatMessage(
                            sender = "AI",
                            content = "🧹 Memoria del chat reiniciada para ${activeRole.displayName}.",
                            activeRole = activeRole
                        )
                    )
                    Toast.makeText(context, "Historial limpiado", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = "Limpiar Memoria",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun ChatMessageBubble(msg: ChatMessage) {
    val isUser = msg.sender == "USER"
    val formattedTime = remember(msg.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestampMillis))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (msg.isAccessDenied) {
                ErrorRed.copy(alpha = 0.2f)
            } else if (isUser) {
                if (msg.activeRole == UserRole.GUARD) GoldPrimary.copy(alpha = 0.2f) else CyanNeon.copy(alpha = 0.2f)
            } else {
                NavySurface
            },
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (msg.isAccessDenied) ErrorRed
                else if (isUser) (if (msg.activeRole == UserRole.GUARD) GoldPrimary else CyanNeon)
                else Color(0xFF374151)
            ),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (msg.isAccessDenied) Icons.Default.Warning else if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = if (msg.isAccessDenied) ErrorRed else if (isUser) Color.White else (if (msg.activeRole == UserRole.GUARD) GoldPrimary else CyanNeon),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (msg.isAccessDenied) "DENEGADO (Capa RBAC)" else if (isUser) "Usuario" else "Copiloto (${msg.activeRole.displayName})",
                            color = if (msg.isAccessDenied) ErrorRed else if (isUser) Color.White else (if (msg.activeRole == UserRole.GUARD) GoldPrimary else CyanNeon),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(text = formattedTime, color = TextMuted, fontSize = 9.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = msg.content,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/**
 * Processes commands strictly applying the RBAC policy layer.
 */
private fun processCommandWithRbacPolicy(query: String, role: UserRole): Pair<String, Boolean> {
    val evaluation = RbacManager.evaluateAiCommandAccess(role, query)

    if (evaluation is RbacEvaluationResult.AccessDenied) {
        val deniedMessage = "🚫 **[DENEGADO POR CAPA DE SEGURIDAD RBAC]**\n\n" +
                "• **Permiso Requerido:** ${evaluation.requiredPermission.label} (${evaluation.requiredPermission.category})\n" +
                "• **Rol Actual:** ${role.displayName}\n\n" +
                "**Motivo de Restricción:**\n${evaluation.reason}\n\n" +
                "💡 *Si requiere realizar cambios en la configuración del sistema o parámetros de seguridad, debe autenticarse con perfil de **Administrador**.*"
        return Pair(deniedMessage, true)
    }

    val q = query.lowercase(Locale.getDefault())

    val responseText = when (role) {
        UserRole.GUARD -> {
            if (q.contains("estado") || q.contains("checked-in") || q.contains("departed") || q.contains("visitante") || q.contains("actualizar")) {
                "✅ **Comando de Guardia Aprobado (Permiso: UPDATE_VISITOR_STATUS)**\n\n" +
                        "Se ha procesado la solicitud para el registro de visitante:\n" +
                        "1. **Acción:** Actualización de estado en base de datos Room.\n" +
                        "2. **Auditoría:** Guardado con estampa de tiempo e ID de operador de garita.\n" +
                        "3. **Notificación:** Notificación enviada al residente de la unidad de destino."
            } else if (q.contains("panico") || q.contains("pánico") || q.contains("alerta") || q.contains("emergencia")) {
                "🚨 **Comando de Guardia Aprobado (Permiso: MANAGE_PANIC_ALERTS)**\n\n" +
                        "Protocolo de Atención de Alerta de Emergencia:\n" +
                        "1. **Verificación:** Alerta registrada en la consola de monitoreo.\n" +
                        "2. **Despacho:** Guardias de patrulla notificados vía radio e interfaz de mapa de piso.\n" +
                        "3. **Seguimiento:** Estado marcado en 'Atención en Curso'."
            } else {
                "🛡️ **Asistencia de Guardia de Caseta (Alcance Permitido)**\n\n" +
                        "Entendido respecto a '$query'. Sus permisos vigentes le permiten:\n" +
                        "• Actualizar estado de ingreso/salida de visitantes.\n" +
                        "• Atender y desactivar alertas de pánico e incidencias físicas en la garita."
            }
        }

        UserRole.ADMIN -> {
            if (q.contains("configurar") || q.contains("camara") || q.contains("cámara") || q.contains("escaner") || q.contains("escáner") || q.contains("parametro") || q.contains("parámetro")) {
                "⚙️ **Comando de Administrador Aprobado (Permiso: CONFIGURE_SYSTEM_SETTINGS)**\n\n" +
                        "Ajustes de Parámetros del Sistema de Seguridad:\n" +
                        "1. **Umbral de Escaneo CameraX:** 0.85 FPS / Enfoque continuo activado.\n" +
                        "2. **Sensor Biométrico:** Habilitado para autenticación de guardias.\n" +
                        "3. **Sensibilidad de Pánico:** 300 ms de pulsación sostenida.\n" +
                        "4. **Persistencia:** Configuración guardada en preferencias del sistema."
            } else if (q.contains("politica") || q.contains("política") || q.contains("lista negra") || q.contains("bloqueo")) {
                "🛡️ **Comando de Administrador Aprobado (Permiso: MODIFY_SECURITY_POLICIES)**\n\n" +
                        "Gestión de Políticas de Seguridad y Listas de Control:\n" +
                        "1. **Reglas de Acceso:** Restricción de ingreso nocturno a visitas no enroladas.\n" +
                        "2. **Listas de Bloqueo:** Verificación activa de documentos RUT restringidos por administración."
            } else if (q.contains("auditoria") || q.contains("auditoría") || q.contains("base de datos") || q.contains("exportar")) {
                "📊 **Comando de Administrador Aprobado (Permiso: AUDIT_FULL_DATABASE)**\n\n" +
                        "Módulo de Auditoría y Exportación de Sistema:\n" +
                        "1. **Exportación JSON:** Generación de esquema estandarizado para auditoría externa.\n" +
                        "2. **Integridad Room DB:** Base de datos verificada sin corrupción de registros."
            } else {
                "🔑 **Acceso Total de Administrador:**\n\n" +
                        "Procesando comando '$query' con privilegios elevados del sistema.\n" +
                        "Tiene acceso ilimitado a configuraciones, parámetros de escáner, políticas y auditorías de copropiedad."
            }
        }
    }

    return Pair(responseText, false)
}
