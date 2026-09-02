package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.Locale

object QRCodeGenerator {

    /**
     * Builds standard Indian Unified Payments Interface (UPI) payment URL schema:
     * `upi://pay?pa={upiId}&pn={merchantName}&am={grandTotal}&cu=INR`
     */
    fun buildUpiPayUrl(upiId: String, merchantName: String, amount: Double): String {
        val cleanUpi = upiId.trim().ifBlank { "kirana.store@upi" }
        val cleanName = Uri.encode(merchantName.trim().ifBlank { "Kirana Store" })
        val formattedAmt = String.format(Locale.US, "%.2f", amount)
        return "upi://pay?pa=$cleanUpi&pn=$cleanName&am=$formattedAmt&cu=INR"
    }

    /**
     * Generates a square Android Bitmap encoding the given text using ZXing QRCodeWriter.
     */
    fun generateQRCodeBitmap(content: String, width: Int = 512, height: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height
            )
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
