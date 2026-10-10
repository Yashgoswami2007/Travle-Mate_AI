package com.example.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

private fun decodeQrCodeFromBitmap(originalBitmap: android.graphics.Bitmap): String? {
    return try {
        // Scale down if too large to help ZXing binarizer and prevent memory issues
        var bitmap = originalBitmap
        val maxDim = 1024
        if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val bMap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        reader.decode(bMap, hints).text
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerDialog(
    currentUserId: String,
    onParticipantConfirmed: (QrParticipantPayload) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var manualInput by remember { mutableStateOf("") }
    var scannedPayload by remember { mutableStateOf<QrParticipantPayload?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Camera Image Picker
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val decodedText = decodeQrCodeFromBitmap(bitmap)
                if (decodedText != null) {
                    val parsed = QrCodeUtils.parseParticipantPayload(decodedText)
                    if (parsed != null) {
                        if (parsed.id == currentUserId) {
                            errorMessage = "Cannot add yourself as a travel companion!"
                        } else {
                            scannedPayload = parsed
                            errorMessage = null
                        }
                    } else {
                        errorMessage = "Invalid QR code payload."
                    }
                } else {
                    errorMessage = "Could not decode QR code from camera."
                }
            } catch (e: Exception) {
                errorMessage = "Error processing camera image."
            }
        }
    }

    // Image Picker Launcher for scanning QR from photo gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val decodedText = decodeQrCodeFromBitmap(bitmap)
                    if (decodedText != null) {
                        val parsed = QrCodeUtils.parseParticipantPayload(decodedText)
                        if (parsed != null) {
                            if (parsed.id == currentUserId) {
                                errorMessage = "Cannot add yourself as a travel companion!"
                            } else {
                                scannedPayload = parsed
                                errorMessage = null
                            }
                        } else {
                            errorMessage = "Invalid QR code payload."
                        }
                    } else {
                        errorMessage = "Could not decode QR code from image."
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Could not load image."
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("qr_scanner_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
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
                                .background(TravelNavyContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = TravelNavyPrimary)
                        }
                        Column {
                            Text("Scan Companion QR", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TravelNavyDark)
                            Text("Add companion & share expenses", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TravelNavySecondary)
                    }
                }

                Divider(color = TravelBorderSubtle)

                // Error alert
                errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TravelWarningRed.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TravelWarningRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = TravelWarningRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(err, color = TravelWarningRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // If a payload was decoded, display confirmation step
                if (scannedPayload != null) {
                    val p = scannedPayload!!
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelNavyPrimary.copy(alpha = 0.05f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, TravelNavyPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(TravelTerracotta),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(p.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }

                                Column {
                                    Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TravelNavyDark)
                                    if (p.username.isNotBlank()) {
                                        Text("@${p.username}", fontSize = 13.sp, color = TravelTerracotta)
                                    }
                                    Text("ID: ${p.id.take(12)}...", fontSize = 11.sp, color = TextSecondary)
                                }
                            }

                            Text(
                                "Add this companion to your trip group to split expenses and settle balances?",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onParticipantConfirmed(p) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TravelForestGreen),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("btn_confirm_add_qr_companion")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Confirm & Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = { scannedPayload = null },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Scan Again", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Option 1: Camera Image Selector
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_camera_qr_image")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OPEN CAMERA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Option 2: Gallery Image Selector
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_pick_qr_image")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select QR Image from Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Text("OR enter Participant ID / QR Payload manually:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)

                    // Option 3: Manual Text / ID Input
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        placeholder = { Text("e.g. tripmate://participant?id=p_123 or p_9821...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(12.dp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_qr_payload")
                    )

                    Button(
                        onClick = {
                            val parsed = QrCodeUtils.parseParticipantPayload(manualInput)
                            if (parsed != null) {
                                if (parsed.id == currentUserId) {
                                    errorMessage = "Cannot add yourself as a companion!"
                                } else {
                                    scannedPayload = parsed
                                    errorMessage = null
                                }
                            } else {
                                errorMessage = "Invalid participant ID format. Try pasting full payload."
                            }
                        },
                        enabled = manualInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelTerracotta),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_process_manual_qr")
                    ) {
                        Text("Verify & Add Companion", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

