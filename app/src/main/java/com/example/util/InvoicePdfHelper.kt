package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.db.InvoiceEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfHelper {

    private const val TAG = "InvoicePdfHelper"

    fun generateInvoicePdf(
        context: Context,
        invoice: InvoiceEntity,
        businessName: String? = "Billing Store",
        merchantMobile: String? = ""
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 page size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val titlePaint = Paint()
        val headerPaint = Paint()

        val primaryColor = Color.parseColor("#10B981") // Emerald Green
        val darkTextColor = Color.parseColor("#1E293B")
        val lightGrayColor = Color.parseColor("#F1F5F9")
        val borderColor = Color.parseColor("#E2E8F0")

        // 1. Header Banner
        paint.color = primaryColor
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Business Name
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 22f
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText((businessName ?: "Billing Store").uppercase(Locale.ROOT), 30f, 45f, titlePaint)

        // Sub-title / Merchant contact / DL No & GSTIN
        paint.color = Color.parseColor("#D1FAE5")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dlStr = invoice.dlNumber.ifBlank { "DL-20B/10492/2024" }
        val gstinStr = invoice.gstin.ifBlank { "27ABCDE1234F1Z5" }
        val isGst = invoice.taxAmount > 0 || invoice.gstin.isNotBlank()
        val contactText = if (isGst) "DL No: $dlStr | GSTIN: $gstinStr" else "DL No: $dlStr | Simple Bill"
        val phoneText = if (!merchantMobile.isNullOrEmpty()) "Contact: $merchantMobile" else "Smart POS Invoice"
        canvas.drawText(phoneText, 30f, 62f, paint)
        canvas.drawText(contactText, 30f, 75f, paint)

        // INVOICE text on right
        titlePaint.textSize = 18f
        titlePaint.textAlign = Paint.Align.RIGHT
        val invTitle = if (isGst) "TAX INVOICE" else "ESTIMATE / CASH MEMO"
        canvas.drawText(invTitle, 565f, 50f, titlePaint)

        if (invoice.isEdited) {
            paint.color = Color.parseColor("#FBBF24")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("(EDITED INVOICE)", 565f, 70f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        // Reset text align
        titlePaint.textAlign = Paint.Align.LEFT

        // 2. Invoice Meta & Customer Details
        var currentY = 110f
        val detailsBoxHeight = if (invoice.doctorName.isNotBlank() || invoice.patientInfo.isNotBlank()) 90f else 70f

        // Box background for details
        paint.color = lightGrayColor
        canvas.drawRect(30f, currentY, 565f, currentY + detailsBoxHeight, paint)

        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(30f, currentY, 565f, currentY + detailsBoxHeight, paint)
        paint.style = Paint.Style.FILL

        // Metadata Left Side: Customer Name & Mobile
        headerPaint.color = darkTextColor
        headerPaint.textSize = 10.5f
        headerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CUSTOMER / PATIENT DETAILS:", 45f, currentY + 20f, headerPaint)

        paint.color = darkTextColor
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Name: ${invoice.customerName}", 45f, currentY + 36f, paint)
        val mobStr = if (invoice.customerMobile.isNotBlank()) invoice.customerMobile else "N/A"
        canvas.drawText("Mobile: $mobStr", 45f, currentY + 52f, paint)

        if (invoice.patientInfo.isNotBlank()) {
            canvas.drawText("Patient: ${invoice.patientInfo}", 45f, currentY + 68f, paint)
        }

        // Metadata Right Side: Invoice #, Date, Doctor Name
        headerPaint.textAlign = Paint.Align.RIGHT
        paint.textAlign = Paint.Align.RIGHT

        val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.timestamp))
        val invNo = invoice.firestoreId.take(8).ifBlank { "${invoice.id}" }.uppercase(Locale.ROOT)

        canvas.drawText("INVOICE NO: #$invNo", 550f, currentY + 20f, headerPaint)
        canvas.drawText("Date: $formattedDate", 550f, currentY + 36f, paint)
        canvas.drawText("Payment Mode: ${invoice.paymentMode}", 550f, currentY + 52f, paint)
        if (invoice.doctorName.isNotBlank()) {
            canvas.drawText("Doctor: ${invoice.doctorName}", 550f, currentY + 68f, paint)
        }

        headerPaint.textAlign = Paint.Align.LEFT
        paint.textAlign = Paint.Align.LEFT

        // 3. Itemized Table Header
        currentY += detailsBoxHeight + 20f

        // Table Header Bar
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRect(30f, currentY, 565f, currentY + 25f, paint)

        headerPaint.color = Color.WHITE
        headerPaint.textSize = 10f
        headerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("#", 40f, currentY + 16f, headerPaint)
        canvas.drawText("ITEM DESCRIPTION", 70f, currentY + 16f, headerPaint)
        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("QTY", 380f, currentY + 16f, headerPaint)
        canvas.drawText("PRICE", 470f, currentY + 16f, headerPaint)
        canvas.drawText("AMOUNT", 555f, currentY + 16f, headerPaint)
        headerPaint.textAlign = Paint.Align.LEFT

        currentY += 25f

        // 4. Parse Items and Draw Table Rows
        val itemsList = parseItemsSummary(invoice.itemsSummary)

        paint.color = darkTextColor
        paint.textSize = 10f

        itemsList.forEachIndexed { index, item ->
            val rowBg = if (index % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            val rowPaint = Paint().apply { color = rowBg }
            canvas.drawRect(30f, currentY, 565f, currentY + 22f, rowPaint)

            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${index + 1}", 40f, currentY + 15f, paint)

            val itemTitle = if (item.name.length > 35) item.name.take(32) + "..." else item.name
            canvas.drawText(itemTitle, 70f, currentY + 15f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(item.qtyStr, 380f, currentY + 15f, paint)
            val priceDisplay = if (item.priceStr.isNotBlank() && item.priceStr != "-") item.priceStr else if (item.price > 0) "₹${String.format(Locale.US, "%.2f", item.price)}" else "-"
            canvas.drawText(priceDisplay, 470f, currentY + 15f, paint)
            val totalDisplay = if (item.total > 0) "₹${String.format(Locale.US, "%.2f", item.total)}" else "-"
            canvas.drawText(totalDisplay, 555f, currentY + 15f, paint)

            currentY += 22f
        }

        // Draw bottom line of table
        paint.color = borderColor
        canvas.drawLine(30f, currentY, 565f, currentY, paint)

        // 5. Summary Breakdown Box
        currentY += 20f

        val summaryStartX = 330f
        paint.color = lightGrayColor
        canvas.drawRect(summaryStartX, currentY, 565f, currentY + 105f, paint)

        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        canvas.drawRect(summaryStartX, currentY, 565f, currentY + 105f, paint)
        paint.style = Paint.Style.FILL

        var sumY = currentY + 20f
        paint.color = darkTextColor
        paint.textSize = 10f

        // Subtotal
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Subtotal:", summaryStartX + 15f, sumY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", invoice.subtotal)}", 550f, sumY, paint)

        // Discount
        if (invoice.discountAmount > 0) {
            sumY += 18f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Discount:", summaryStartX + 15f, sumY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", invoice.discountAmount)}", 550f, sumY, paint)
        }

        // Tax Breakdown
        if (invoice.taxAmount > 0) {
            val halfTax = invoice.taxAmount / 2.0
            sumY += 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("CGST:", summaryStartX + 15f, sumY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("+₹${String.format(Locale.US, "%.2f", halfTax)}", 550f, sumY, paint)

            sumY += 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("SGST:", summaryStartX + 15f, sumY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("+₹${String.format(Locale.US, "%.2f", halfTax)}", 550f, sumY, paint)
        }

        // Grand Total Line
        sumY += 22f
        paint.color = borderColor
        canvas.drawLine(summaryStartX + 10f, sumY - 12f, 555f, sumY - 12f, paint)

        headerPaint.color = primaryColor
        headerPaint.textSize = 12f
        headerPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        headerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("GRAND TOTAL:", summaryStartX + 15f, sumY, headerPaint)

        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", invoice.amount)}", 550f, sumY, headerPaint)

        // 6. Footer
        currentY = 770f
        paint.color = borderColor
        canvas.drawLine(30f, currentY, 565f, currentY, paint)

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DISCLAIMER: Medicines sold without prescription at patient's risk. Goods once sold will not be taken back.", 297f, currentY + 16f, paint)
        canvas.drawText("Thank you for your business! Computer-generated Pharmacy Tax Invoice.", 297f, currentY + 30f, paint)

        pdfDocument.finishPage(page)

        // Write PDF file to cache directory
        val fileName = "Invoice_${invoice.firestoreId.take(8).ifBlank { invoice.id }}.pdf"
        val pdfFile = File(context.cacheDir, fileName)

        try {
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PDF file: ${e.localizedMessage}")
        } finally {
            pdfDocument.close()
        }

        return pdfFile
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

    data class PdfItemRow(val name: String, val qtyStr: String, val priceStr: String, val price: Double, val total: Double)

    fun parseItemsSummary(summary: String): List<PdfItemRow> {
        if (summary.isBlank()) {
            return listOf(PdfItemRow("Billed Products", "1", "-", 0.0, 0.0))
        }

        val rows = mutableListOf<PdfItemRow>()
        val parts = summary.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isNotBlank()) {
                if (trimmed.contains(" — ")) {
                    // e.g. "Dolo 650mg — 3 Tablets @ ₹10.00/Tab = ₹30.00 (Batch: B123)"
                    val subParts = trimmed.split(" — ")
                    val name = subParts[0].trim()
                    val rest = subParts.getOrElse(1) { "" }.trim()

                    if (rest.contains(" = ")) {
                        val rateAndTotal = rest.split(" = ")
                        val qtyAndRate = rateAndTotal[0].trim() // "3 Tablets @ ₹10.00/Tab"
                        val totalStr = rateAndTotal.getOrElse(1) { "" }.trim()
                        val totalVal = totalStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0

                        if (qtyAndRate.contains(" @ ")) {
                            val qrParts = qtyAndRate.split(" @ ")
                            val qtyStr = qrParts[0].trim() // "3 Tablets"
                            val priceStr = qrParts.getOrElse(1) { "" }.trim() // "₹10.00/Tab"
                            rows.add(PdfItemRow(name, qtyStr, priceStr, 0.0, totalVal))
                        } else {
                            rows.add(PdfItemRow(name, qtyAndRate, "-", 0.0, totalVal))
                        }
                    } else {
                        rows.add(PdfItemRow(name, rest, "-", 0.0, 0.0))
                    }
                } else {
                    val matchResult = Regex("""^(.+?)\s*x\s*(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
                    if (matchResult != null) {
                        val qStr = matchResult.groupValues[1].trim()
                        val name = matchResult.groupValues[2].trim()
                        rows.add(PdfItemRow(name, qStr, "-", 0.0, 0.0))
                    } else {
                        rows.add(PdfItemRow(trimmed, "1", "-", 0.0, 0.0))
                    }
                }
            }
        }
        return if (rows.isEmpty()) listOf(PdfItemRow("Billed Products", "1", "-", 0.0, 0.0)) else rows
    }

    // --- Android Printing Support ---

    fun printInvoicePdf(context: Context, pdfFile: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Printing service not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "Print_${pdfFile.name}"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder(pdfFile.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()

                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) return
                    var input: FileInputStream? = null
                    var output: FileOutputStream? = null

                    try {
                        input = FileInputStream(pdfFile)
                        output = FileOutputStream(destination.fileDescriptor)

                        val buf = ByteArray(1024)
                        var bytesRead: Int
                        while (input.read(buf).also { bytesRead = it } > 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                                return
                            }
                            output.write(buf, 0, bytesRead)
                        }

                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        Log.e(TAG, "Print write error: ${e.localizedMessage}")
                        callback?.onWriteFailed(e.message)
                    } finally {
                        try {
                            input?.close()
                            output?.close()
                        } catch (e: IOException) {
                            Log.e(TAG, "Error closing stream: ${e.localizedMessage}")
                        }
                    }
                }
            },
            null
        )
    }

    // --- WhatsApp & Social Sharing Engine ---

    fun shareInvoicePdfWhatsApp(
        context: Context,
        pdfFile: File,
        invoice: InvoiceEntity,
        businessName: String? = "Billing Store"
    ) {
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider error: ${e.localizedMessage}")
            Toast.makeText(context, "Could not access PDF file for sharing", Toast.LENGTH_SHORT).show()
            return
        }

        val message = WhatsAppInvoiceHelper.formatInvoiceText(invoice, businessName ?: "SmartPOS Store")

        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(whatsappIntent)
        } catch (e: ActivityNotFoundException) {
            // If WhatsApp package isn't directly available, open general share chooser
            val generalShareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(generalShareIntent, "Share Invoice PDF"))
        }
    }

    fun shareInvoicePdfGeneral(
        context: Context,
        pdfFile: File,
        invoice: InvoiceEntity,
        businessName: String? = "Billing Store"
    ) {
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider error: ${e.localizedMessage}")
            Toast.makeText(context, "Could not access PDF file for sharing", Toast.LENGTH_SHORT).show()
            return
        }

        val message = "Tax Invoice for ${invoice.customerName} - Total: $${String.format(Locale.US, "%.2f", invoice.amount)} (${businessName ?: "Billing Store"})"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Tax Invoice PDF"))
    }
}
