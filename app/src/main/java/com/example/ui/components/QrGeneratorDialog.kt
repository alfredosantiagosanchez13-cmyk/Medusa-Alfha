package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.scanner.PassType
import com.example.scanner.QrPassEntity
import com.example.scanner.SamplePassRepository
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.TextMuted
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@Composable
fun QrGeneratorDialog(
    onSimulateScan: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var guestName by remember { mutableStateOf("Roberto D'Amico") }
    var destinationHouse by remember { mutableStateOf("Casa #204") }
    var passCode by remember { mutableStateOf("MEDUSA-PASS-${(200..999).random()}") }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(generateQrBitmap(passCode)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("qr_generator_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generador de Pase QR",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // QR Display Box
                Card(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(3.dp, CyanNeon)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated QR Pass",
                                modifier = Modifier.size(180.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Código: $passCode",
                    color = CyanNeon,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = guestName,
                    onValueChange = {
                        guestName = it
                    },
                    label = { Text("Nombre del Invitado", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_guest_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = destinationHouse,
                    onValueChange = {
                        destinationHouse = it
                    },
                    label = { Text("Casa de Destino", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_destination_house_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val newCode = "MEDUSA-PASS-${(200..999).random()}"
                            passCode = newCode
                            val customPass = QrPassEntity(
                                passCode = newCode,
                                guestName = guestName.ifBlank { "Invitado de Prueba" },
                                guestDocument = "20.${(100..999).random()}.${(100..999).random()}-K",
                                destinationHouse = destinationHouse.ifBlank { "Casa #101" },
                                hostResidentName = "Residente Propietario",
                                passType = PassType.VISITOR_SINGLE,
                                validUntilMillis = System.currentTimeMillis() + (6 * 3600 * 1000)
                            )
                            SamplePassRepository.addCustomPass(customPass)
                            qrBitmap = generateQrBitmap(newCode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyCard, contentColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generate_new_qr_pass_button")
                    ) {
                        Text("Nuevo Pase", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            // Register in repository & trigger immediate scan test
                            val customPass = QrPassEntity(
                                passCode = passCode,
                                guestName = guestName.ifBlank { "Invitado de Prueba" },
                                guestDocument = "20.123.456-7",
                                destinationHouse = destinationHouse.ifBlank { "Casa #101" },
                                hostResidentName = "Residente Propietario",
                                passType = PassType.VISITOR_SINGLE,
                                validUntilMillis = System.currentTimeMillis() + (6 * 3600 * 1000)
                            )
                            SamplePassRepository.addCustomPass(customPass)
                            onSimulateScan(passCode)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("simulate_scan_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simular Escaneo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun generateQrBitmap(contents: String, sizePx: Int = 512): Bitmap? {
    return try {
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
