package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.data.db.InvoiceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ReceiptPrintHelper {

    private const val TAG = "ReceiptPrintHelper"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val INIT_PRINTER = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11) // Double height & width
    private val NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val FEED_LINE = byteArrayOf(0x0A)
    private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

    data class PairedPrinter(
        val name: String,
        val address: String
    )

    /**
     * Get list of paired Bluetooth devices that could be thermal printers.
     */
    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<PairedPrinter> {
        val paired = PrinterManager.loadPairedDevices(context)
        return paired.map {
            PairedPrinter(
                name = it.name,
                address = it.address
            )
        }
    }

    /**
     * Connect to Bluetooth Thermal Printer and print formatted receipt.
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceipt(
        context: Context,
        deviceAddress: String,
        invoice: InvoiceEntity,
        businessName: String,
        upiId: String = "merchant@upi",
        paperWidthMm: Int = 58, // 58 or 80
        isGstMode: Boolean = true,
        onStatusUpdate: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val adapter = BluetoothPermissionHandler.getBluetoothAdapter(context)
        if (adapter == null || !adapter.isEnabled) {
            withContext(Dispatchers.Main) { onStatusUpdate("Bluetooth is turned off") }
            return@withContext false
        }

        val socket = PrinterManager.openSocket(context, deviceAddress)
        if (socket == null) {
            withContext(Dispatchers.Main) { onStatusUpdate("Connection failed to $deviceAddress") }
            return@withContext false
        }

        var outputStream: OutputStream? = null

        try {
            outputStream = socket.outputStream

            withContext(Dispatchers.Main) { onStatusUpdate("Formatting ESC/POS receipt data...") }
            val receiptBytes = generateReceiptBytes(
                invoice = invoice,
                businessName = businessName,
                upiId = upiId,
                paperWidthMm = paperWidthMm,
                isGstMode = isGstMode
            )

            withContext(Dispatchers.Main) { onStatusUpdate("Sending data to thermal printer...") }
            outputStream.write(receiptBytes)
            outputStream.flush()

            withContext(Dispatchers.Main) { onStatusUpdate("Receipt printed successfully!") }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to print thermal receipt: ${e.message}", e)
            withContext(Dispatchers.Main) { onStatusUpdate("Printer Error: ${e.localizedMessage ?: "Connection failed"}") }
            false
        } finally {
            try {
                outputStream?.close()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing printer socket: ${e.message}")
            }
        }
    }

    /**
     * Send test page to thermal printer
     */
    @SuppressLint("MissingPermission")
    suspend fun printTestReceipt(
        context: Context,
        deviceAddress: String,
        businessName: String = "Kirana Store",
        paperWidthMm: Int = 58,
        onStatusUpdate: (String) -> Unit
    ): Boolean {
        return PrinterManager.printTestPage(
            context = context,
            deviceAddress = deviceAddress,
            businessName = businessName,
            paperWidthMm = paperWidthMm,
            onStatus = onStatusUpdate
        )
    }

    /**
     * Generate ESC/POS byte sequence for thermal printer receipt
     */
    fun printReceiptBytes(
        invoice: InvoiceEntity,
        businessName: String,
        upiId: String,
        paperWidthMm: Int,
        isGstMode: Boolean
    ): ByteArray {
        return generateReceiptBytes(invoice, businessName, upiId, paperWidthMm, isGstMode)
    }

    /**
     * Generate ESC/POS byte sequence for thermal printer receipt
     */
    fun generateReceiptBytes(
        invoice: InvoiceEntity,
        businessName: String,
        upiId: String,
        paperWidthMm: Int,
        isGstMode: Boolean
    ): ByteArray {
        val lineLen = if (paperWidthMm == 80) 48 else 32
        val divider = "-".repeat(lineLen) + "\n"
        val doubleDivider = "=".repeat(lineLen) + "\n"

        val baos = java.io.ByteArrayOutputStream()

        baos.write(INIT_PRINTER)

        // 1. Store Header
        baos.write(ALIGN_CENTER)
        baos.write(DOUBLE_SIZE)
        baos.write(BOLD_ON)
        baos.write("${businessName.uppercase(Locale.ROOT)}\n".toByteArray(Charsets.UTF_8))
        baos.write(NORMAL_SIZE)
        baos.write(BOLD_OFF)

        val headerType = if (isGstMode) "TAX INVOICE" else "ESTIMATE / CASH MEMO"
        baos.write(BOLD_ON)
        baos.write("--- $headerType ---\n".toByteArray(Charsets.UTF_8))
        baos.write(BOLD_OFF)

        if (isGstMode) {
            val gstin = invoice.gstin.ifBlank { "27ABCDE1234F1Z5" }
            baos.write("GSTIN: $gstin\n".toByteArray(Charsets.UTF_8))
        }
        if (invoice.dlNumber.isNotBlank()) {
            baos.write("DL No: ${invoice.dlNumber}\n".toByteArray(Charsets.UTF_8))
        }

        baos.write(divider.toByteArray(Charsets.UTF_8))

        // 2. Invoice Metadata
        baos.write(ALIGN_LEFT)
        val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(invoice.timestamp))
        val invNo = invoice.firestoreId.take(8).ifBlank { "${invoice.id}" }.uppercase(Locale.ROOT)

        baos.write(formatTwoColumns("Inv #: #$invNo", "Date: $dateStr", lineLen).toByteArray(Charsets.UTF_8))
        val custName = if (invoice.customerName.isBlank()) "Walk-in Customer" else invoice.customerName
        baos.write("Cust: $custName".take(lineLen).plus("\n").toByteArray(Charsets.UTF_8))
        if (invoice.customerMobile.isNotBlank()) {
            baos.write("Mob: ${invoice.customerMobile}\n".toByteArray(Charsets.UTF_8))
        }
        if (invoice.tableNumber.isNotBlank()) {
            baos.write(formatTwoColumns("Table: ${invoice.tableNumber}", "Type: ${invoice.orderType.ifBlank { "Dine-in" }}", lineLen).toByteArray(Charsets.UTF_8))
        }
        if (invoice.doctorName.isNotBlank()) {
            baos.write("Doctor: ${invoice.doctorName}\n".toByteArray(Charsets.UTF_8))
        }
        baos.write("Payment: ${invoice.paymentMode}\n".toByteArray(Charsets.UTF_8))

        baos.write(divider.toByteArray(Charsets.UTF_8))

        // 3. Items Table Header
        baos.write(BOLD_ON)
        if (lineLen == 32) {
            // 58mm: Item Name (16) Qty (6) Amt (10)
            baos.write(formatThreeColumns("Item Name", "Qty", "Amt", 16, 6, 10).toByteArray(Charsets.UTF_8))
        } else {
            // 80mm: Item Name (24) Qty (8) Price (8) Amt (8)
            baos.write(formatFourColumns("Item Description", "Qty", "Price", "Amount", 22, 7, 9, 10).toByteArray(Charsets.UTF_8))
        }
        baos.write(BOLD_OFF)
        baos.write(divider.toByteArray(Charsets.UTF_8))

        // 4. Item Rows
        val parsedItems = InvoicePdfHelper.parseItemsSummary(invoice.itemsSummary)

        parsedItems.forEach { item ->
            val cleanName = item.name.trim()
            if (lineLen == 32) {
                // Wrap item name if > 16 chars
                val wrappedLines = wrapText(cleanName, 16)
                baos.write(formatThreeColumns(
                    col1 = wrappedLines.firstOrNull() ?: "",
                    col2 = item.qtyStr,
                    col3 = if (item.total > 0) String.format(Locale.US, "%.2f", item.total) else "-",
                    w1 = 16, w2 = 6, w3 = 10
                ).toByteArray(Charsets.UTF_8))

                for (i in 1 until wrappedLines.size) {
                    baos.write("${wrappedLines[i]}\n".toByteArray(Charsets.UTF_8))
                }
            } else {
                val wrappedLines = wrapText(cleanName, 22)
                baos.write(formatFourColumns(
                    col1 = wrappedLines.firstOrNull() ?: "",
                    col2 = item.qtyStr,
                    col3 = if (item.price > 0) String.format(Locale.US, "%.2f", item.price) else "-",
                    col4 = if (item.total > 0) String.format(Locale.US, "%.2f", item.total) else "-",
                    w1 = 22, w2 = 7, w3 = 9, w4 = 10
                ).toByteArray(Charsets.UTF_8))

                for (i in 1 until wrappedLines.size) {
                    baos.write("${wrappedLines[i]}\n".toByteArray(Charsets.UTF_8))
                }
            }
        }

        baos.write(divider.toByteArray(Charsets.UTF_8))

        // 5. Totals & GST Breakdown Section
        baos.write(formatTwoColumns("Subtotal:", "INR ${String.format(Locale.US, "%.2f", invoice.subtotal)}", lineLen).toByteArray(Charsets.UTF_8))

        if (invoice.discountAmount > 0) {
            baos.write(formatTwoColumns("Discount:", "-INR ${String.format(Locale.US, "%.2f", invoice.discountAmount)}", lineLen).toByteArray(Charsets.UTF_8))
        }

        if (isGstMode) {
            val taxableAmount = (invoice.subtotal - invoice.discountAmount).coerceAtLeast(0.0)
            val halfTax = invoice.taxAmount / 2.0
            baos.write(formatTwoColumns("Taxable Amount:", "INR ${String.format(Locale.US, "%.2f", taxableAmount)}", lineLen).toByteArray(Charsets.UTF_8))
            if (invoice.taxAmount > 0) {
                baos.write(formatTwoColumns("CGST (Intra-state):", "INR ${String.format(Locale.US, "%.2f", halfTax)}", lineLen).toByteArray(Charsets.UTF_8))
                baos.write(formatTwoColumns("SGST (Intra-state):", "INR ${String.format(Locale.US, "%.2f", halfTax)}", lineLen).toByteArray(Charsets.UTF_8))
            } else {
                baos.write(formatTwoColumns("GST (0% Exempted):", "INR 0.00", lineLen).toByteArray(Charsets.UTF_8))
            }
        } else {
            baos.write("Note: Non-GST Estimate / Cash Memo\n".toByteArray(Charsets.UTF_8))
        }

        baos.write(doubleDivider.toByteArray(Charsets.UTF_8))

        // Grand Total (Bold & Enlarged)
        baos.write(BOLD_ON)
        baos.write(formatTwoColumns("GRAND TOTAL:", "INR ${String.format(Locale.US, "%.2f", invoice.amount)}", lineLen).toByteArray(Charsets.UTF_8))
        baos.write(BOLD_OFF)

        baos.write(doubleDivider.toByteArray(Charsets.UTF_8))

        // 6. UPI QR Code & Instructions
        baos.write(ALIGN_CENTER)
        val cleanUpi = upiId.ifBlank { "merchant@upi" }
        baos.write("Pay via UPI: $cleanUpi\n".toByteArray(Charsets.UTF_8))
        val upiPayload = "upi://pay?pa=$cleanUpi&pn=${UriEncode(businessName)}&am=${String.format(Locale.US, "%.2f", invoice.amount)}&cu=INR"
        
        // Print native ESC/POS QR Code if supported
        baos.write(generateEscPosQrBytes(upiPayload))

        baos.write(FEED_LINE)
        baos.write(BOLD_ON)
        baos.write("Thank you! Visit Again.\n".toByteArray(Charsets.UTF_8))
        baos.write(BOLD_OFF)
        baos.write("Generated via Kirana POS\n".toByteArray(Charsets.UTF_8))

        // Extra spacing and paper cut command
        baos.write(byteArrayOf(0x1B, 0x64, 0x04)) // Feed 4 lines
        baos.write(CUT_PAPER)

        return baos.toByteArray()
    }

    /**
     * Native ESC/POS QR Code byte command (Model 2 QR Code)
     */
    private fun generateEscPosQrBytes(qrData: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val dataBytes = qrData.toByteArray(Charsets.UTF_8)
        val length = dataBytes.size + 3

        try {
            // Function 167: Set QR Code model (Model 2)
            baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
            // Function 169: Set QR Code size (Module size 6)
            baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06))
            // Function 173: Set Error Correction Level (Level M)
            baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
            // Function 180: Store QR Code data in symbol storage area
            val pL = (length % 256).toByte()
            val pH = (length / 256).toByte()
            baos.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
            baos.write(dataBytes)
            // Function 181: Print symbol data in symbol storage area
            baos.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        } catch (e: Exception) {
            Log.e(TAG, "Error generating ESC/POS QR bytes: ${e.message}")
        }
        return baos.toByteArray()
    }

    private fun UriEncode(str: String): String {
        return java.net.URLEncoder.encode(str, "UTF-8").replace("+", "%20")
    }

    private fun wrapText(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxLen).coerceAtMost(text.length)
            result.add(text.substring(start, end))
            start = end
        }
        return result
    }

    private fun formatTwoColumns(left: String, right: String, totalLen: Int): String {
        val spaces = totalLen - left.length - right.length
        return if (spaces > 0) {
            left + " ".repeat(spaces) + right + "\n"
        } else {
            left.take(totalLen - right.length - 1) + " " + right + "\n"
        }
    }

    private fun formatThreeColumns(col1: String, col2: String, col3: String, w1: Int, w2: Int, w3: Int): String {
        val c1 = col1.padEnd(w1).take(w1)
        val c2 = col2.padStart(w2).take(w2)
        val c3 = col3.padStart(w3).take(w3)
        return c1 + c2 + c3 + "\n"
    }

    private fun formatFourColumns(col1: String, col2: String, col3: String, col4: String, w1: Int, w2: Int, w3: Int, w4: Int): String {
        val c1 = col1.padEnd(w1).take(w1)
        val c2 = col2.padStart(w2).take(w2)
        val c3 = col3.padStart(w3).take(w3)
        val c4 = col4.padStart(w4).take(w4)
        return c1 + c2 + c3 + c4 + "\n"
    }
}
