package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.net.URLEncoder

data class QrParticipantPayload(
    val id: String,
    val name: String,
    val username: String
)

object QrCodeUtils {

    fun encodeParticipantPayload(id: String, name: String, username: String): String {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        val encodedUser = URLEncoder.encode(username, "UTF-8")
        return "tripmate://participant?id=$id&name=$encodedName&username=$encodedUser"
    }

    fun parseParticipantPayload(qrData: String): QrParticipantPayload? {
        if (qrData.isBlank()) return null
        return try {
            val trimmed = qrData.trim()
            if (trimmed.startsWith("tripmate://participant")) {
                val queryParams = trimmed.substringAfter("?").split("&").associate {
                    val parts = it.split("=")
                    parts[0] to if (parts.size > 1) parts[1] else ""
                }
                val id = queryParams["id"]
                val name = queryParams["name"]
                val username = queryParams["username"] ?: ""
                if (!id.isNullOrBlank() && !name.isNullOrBlank()) {
                    QrParticipantPayload(
                        id = id,
                        name = URLDecoder.decode(name, "UTF-8"),
                        username = URLDecoder.decode(username, "UTF-8")
                    )
                } else null
            } else if (trimmed.startsWith("p_") || trimmed.matches("[a-f0-9\\-]{36}".toRegex())) {
                QrParticipantPayload(
                    id = trimmed,
                    name = "Traveler " + trimmed.take(4).uppercase(),
                    username = "user_" + trimmed.take(6)
                )
            } else null
        } catch (e: Exception) {
            Log.e("QrCodeUtils", "Failed parsing QR payload: $qrData", e)
            null
        }
    }

    fun generateQrCodeBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e("QrCodeUtils", "Failed generating QR bitmap", e)
            null
        }
    }
}
