package com.example.platform

import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ThermalPrintInvoice(
    val storeName: String = "SMART KIRANA & GENERAL STORE",
    val storeAddress: String = "Main Market, Sector 12, Delhi - 110001",
    val storeContact: String = "+91 9876543210",
    val invoiceNumber: String,
    val date: Date = Date(),
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val items: List<ThermalItem>,
    val grandTotal: Double,
    val paymentMode: String = "Cash",
    val footerMessage: String = "Thank You! Please Visit Again\nPowered by SmartPOS Vyapar"
)

data class ThermalItem(
    val name: String,
    val quantity: Double,
    val unit: String = "Pcs",
    val unitPrice: Double,
    val subtotal: Double = quantity * unitPrice
)

object DesktopPrinterManager {

    // Standard ESC/POS Command Constants
    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    // Commands
    private val CMD_INIT = byteArrayOf(ESC, '@'.code.toByte()) // Initialize printer
    private val CMD_ALIGN_LEFT = byteArrayOf(ESC, 'a'.code.toByte(), 0)
    private val CMD_ALIGN_CENTER = byteArrayOf(ESC, 'a'.code.toByte(), 1)
    private val CMD_ALIGN_RIGHT = byteArrayOf(ESC, 'a'.code.toByte(), 2)
    private val CMD_BOLD_ON = byteArrayOf(ESC, 'E'.code.toByte(), 1)
    private val CMD_BOLD_OFF = byteArrayOf(ESC, 'E'.code.toByte(), 0)
    private val CMD_DOUBLE_SIZE = byteArrayOf(ESC, '!'.code.toByte(), 0x30)
    private val CMD_DOUBLE_HEIGHT = byteArrayOf(ESC, '!'.code.toByte(), 0x10)
    private val CMD_NORMAL_FONT = byteArrayOf(ESC, '!'.code.toByte(), 0x00)
    
    // Hardware paper-cut command: GS V 65 16 (0x1D, 0x56, 0x41, 0x10)
    private val CMD_PAPER_CUT = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x41.toByte(), 0x10.toByte())

    /**
     * Builds ESC/POS formatted raw byte stream for 58mm (32 chars) / 80mm (48 chars) thermal printers.
     */
    fun buildEscPosBytes(invoice: ThermalPrintInvoice, lineWidth: Int = 32): ByteArray {
        val bytes = mutableListOf<Byte>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
        val separatorLine = "-".repeat(lineWidth) + "\n"

        fun addString(str: String) {
            bytes.addAll(str.toByteArray(Charsets.UTF_8).toList())
        }

        // 1. Initialize Printer
        bytes.addAll(CMD_INIT.toList())

        // 2. Centered Header: Store Name, Address & Contact
        bytes.addAll(CMD_ALIGN_CENTER.toList())
        bytes.addAll(CMD_BOLD_ON.toList())
        bytes.addAll(CMD_DOUBLE_HEIGHT.toList())
        addString("${invoice.storeName}\n")
        
        bytes.addAll(CMD_NORMAL_FONT.toList())
        bytes.addAll(CMD_BOLD_OFF.toList())
        addString("${invoice.storeAddress}\n")
        addString("Contact: ${invoice.storeContact}\n")
        addString(separatorLine)

        // 3. Invoice Metadata & Date
        bytes.addAll(CMD_ALIGN_LEFT.toList())
        addString("Bill No: ${invoice.invoiceNumber}\n")
        addString("Date:    ${dateFormat.format(invoice.date)}\n")
        if (invoice.customerPhone.isNotBlank()) {
            addString("Cust:    ${invoice.customerName} (${invoice.customerPhone})\n")
        } else {
            addString("Cust:    ${invoice.customerName}\n")
        }
        addString(separatorLine)

        // 4. Itemized Product Table Header
        bytes.addAll(CMD_BOLD_ON.toList())
        val tableHeader = if (lineWidth >= 40) {
            String.format("%-20s %5s %8s %10s\n", "Item", "Qty", "Price", "Total")
        } else {
            String.format("%-14s %4s %6s %6s\n", "Item", "Qty", "Rate", "Total")
        }
        addString(tableHeader)
        bytes.addAll(CMD_BOLD_OFF.toList())
        addString(separatorLine)

        // 5. Item Rows
        invoice.items.forEach { item ->
            val itemName = if (item.name.length > (if (lineWidth >= 40) 20 else 14)) {
                item.name.take(if (lineWidth >= 40) 19 else 13) + "."
            } else {
                item.name
            }

            val itemLine = if (lineWidth >= 40) {
                String.format("%-20s %5.1f %8.2f %10.2f\n", itemName, item.quantity, item.unitPrice, item.subtotal)
            } else {
                val qtyStr = if (item.quantity % 1.0 == 0.0) "${item.quantity.toInt()}" else String.format("%.1f", item.quantity)
                String.format("%-14s %4s %6.1f %6.1f\n", itemName, qtyStr, item.unitPrice, item.subtotal)
            }
            addString(itemLine)
        }

        addString(separatorLine)

        // 6. Grand Total & Payment Mode (Emphasized)
        bytes.addAll(CMD_ALIGN_RIGHT.toList())
        bytes.addAll(CMD_BOLD_ON.toList())
        val grandTotalStr = String.format("GRAND TOTAL: ₹%.2f\n", invoice.grandTotal)
        addString(grandTotalStr)
        bytes.addAll(CMD_BOLD_OFF.toList())

        addString("Payment Mode: ${invoice.paymentMode}\n")
        addString(separatorLine)

        // 7. Footer Message & Branding
        bytes.addAll(CMD_ALIGN_CENTER.toList())
        addString("${invoice.footerMessage}\n")
        addString("\n\n\n") // Feed lines before cut

        // 8. Hardware Paper-cut command (0x1D, 0x56, 0x41, 0x10)
        bytes.addAll(CMD_PAPER_CUT.toList())

        return bytes.toByteArray()
    }

    /**
     * Prints invoice directly to the default Windows OS thermal printer using Java Print Service (javax.print).
     */
    suspend fun printToDefaultPrinter(invoice: ThermalPrintInvoice): Result<String> = withContext(Dispatchers.IO) {
        try {
            val defaultPrintService: PrintService? = PrintServiceLookup.lookupDefaultPrintService()
            if (defaultPrintService == null) {
                return@withContext Result.failure(IllegalStateException("No default printer configured in Windows OS."))
            }

            val escPosData = buildEscPosBytes(invoice)
            val printJob = defaultPrintService.createPrintJob()
            val docFlavor = DocFlavor.BYTE_ARRAY.AUTOSENSE
            val doc = SimpleDoc(ByteArrayInputStream(escPosData), docFlavor, null)
            val attributes = HashPrintRequestAttributeSet()

            printJob.print(doc, attributes)
            Result.success("Printed successfully to ${defaultPrintService.name}")
        } catch (e: Throwable) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Network printing option for LAN / Ethernet thermal printers (Socket port 9100).
     */
    suspend fun printOverNetwork(ipAddress: String, port: Int = 9100, invoice: ThermalPrintInvoice): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val escPosData = buildEscPosBytes(invoice)
                Socket(ipAddress, port).use { socket ->
                    val out: OutputStream = socket.getOutputStream()
                    out.write(escPosData)
                    out.flush()
                }
                Result.success("Printed successfully over network to $ipAddress:$port")
            } catch (e: Throwable) {
                e.printStackTrace()
                Result.failure(e)
            }
        }

    /**
     * Lists available system printers for printer selection dialog.
     */
    fun getAvailablePrinters(): List<String> {
        val flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE
        val printServices = PrintServiceLookup.lookupPrintServices(flavor, null)
        return printServices.map { it.name }
    }
}
