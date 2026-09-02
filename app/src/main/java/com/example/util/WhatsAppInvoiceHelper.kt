package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.data.db.InvoiceEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Item detail model for WhatsApp receipt formatting.
 */
data class WhatsAppInvoiceItem(
    val name: String,
    val quantity: Double,
    val unit: String = "",
    val price: Double,
    val totalAmount: Double = quantity * price
)

/**
 * Utility helper for formatting itemized invoice receipts and auto-dispatching WhatsApp messages to customers.
 */
object WhatsAppInvoiceHelper {

    private const val TAG = "WhatsAppInvoiceHelper"
    private const val PREFS_NAME = "smartpos_settings_prefs"
    private const val KEY_AUTO_SEND_WHATSAPP = "key_auto_send_whatsapp_invoice"

    /**
     * Checks if auto-send WhatsApp bill is enabled by merchant.
     * Defaults to true for Kirana & retail stores.
     */
    fun isAutoSendEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SEND_WHATSAPP, true)
    }

    /**
     * Updates the merchant's preference for auto-sending WhatsApp bills on checkout.
     */
    fun setAutoSendEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SEND_WHATSAPP, enabled).apply()
    }

    /**
     * Parses items from an InvoiceEntity using itemsJson (primary) or itemsSummary (fallback).
     */
    fun extractItemsFromInvoice(invoice: InvoiceEntity): List<WhatsAppInvoiceItem> {
        val items = mutableListOf<WhatsAppInvoiceItem>()

        // 1. Try parsing structured JSON
        if (invoice.itemsJson.isNotBlank()) {
            try {
                val jsonArray = JSONArray(invoice.itemsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Product")
                    val qty = obj.optDouble("quantity", 1.0)
                    val unit = obj.optString("unit", "")
                    val unitPrice = obj.optDouble("unitPrice", 0.0)
                    val lineTotal = if (obj.has("lineTotal")) obj.optDouble("lineTotal", qty * unitPrice) else qty * unitPrice
                    items.add(WhatsAppInvoiceItem(name, qty, unit, unitPrice, lineTotal))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing invoice itemsJson: ${e.localizedMessage}")
            }
        }

        // 2. Fallback: Parse itemsSummary comma-separated text
        if (items.isEmpty() && invoice.itemsSummary.isNotBlank()) {
            val parts = invoice.itemsSummary.split(",")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.isNotBlank()) {
                    // Check for patterns like "Paracetamol — 2 Pcs @ ₹15.00/Strip = ₹30.00 (Batch: B1)"
                    if (trimmed.contains("—") && trimmed.contains("=")) {
                        try {
                            val namePart = trimmed.substringBefore("—").trim()
                            val afterDash = trimmed.substringAfter("—").trim()
                            val qtyPart = afterDash.substringBefore("@").trim()
                            val afterAt = afterDash.substringAfter("@").trim()
                            val pricePart = afterAt.substringBefore("=").substringBefore("/").replace("[^0-9.]".toRegex(), "")
                            val totalPart = afterAt.substringAfter("=").substringBefore("(").replace("[^0-9.]".toRegex(), "")

                            val qtyVal = qtyPart.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 1.0
                            val priceVal = pricePart.toDoubleOrNull() ?: 0.0
                            val totalVal = totalPart.toDoubleOrNull() ?: (qtyVal * priceVal)

                            items.add(WhatsAppInvoiceItem(namePart, qtyVal, "", priceVal, totalVal))
                        } catch (e: Exception) {
                            items.add(WhatsAppInvoiceItem(trimmed, 1.0, "", 0.0, 0.0))
                        }
                    } else if (trimmed.contains(" x ")) {
                        // Pattern: "2 Pcs x Paracetamol" or "2 x Crocin"
                        val qtyPrefix = trimmed.substringBefore(" x ").replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 1.0
                        val name = trimmed.substringAfter(" x ").trim()
                        val unitPrice = if (qtyPrefix > 0 && invoice.amount > 0) invoice.amount / qtyPrefix else 0.0
                        items.add(WhatsAppInvoiceItem(name, qtyPrefix, "", unitPrice, qtyPrefix * unitPrice))
                    } else {
                        items.add(WhatsAppInvoiceItem(trimmed, 1.0, "", 0.0, 0.0))
                    }
                }
            }
        }

        // 3. Fallback if still empty
        if (items.isEmpty() && invoice.itemsCount > 0) {
            val count = invoice.itemsCount.toDouble()
            val unitPrice = if (count > 0) invoice.amount / count else invoice.amount
            items.add(WhatsAppInvoiceItem("Billed Products", count, "pcs", unitPrice, invoice.amount))
        }

        return items
    }

    /**
     * Formats itemized invoice text for WhatsApp sharing.
     */
    fun formatInvoiceText(invoice: InvoiceEntity, businessName: String): String {
        val invoiceNumber = if (invoice.firestoreId.isNotBlank()) {
            invoice.firestoreId.take(8).uppercase()
        } else {
            "INV-${invoice.id}"
        }

        val dateStr = if (invoice.timestamp > 0) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.timestamp))
        } else {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        }

        val storeName = if (businessName.isNotBlank()) businessName.trim() else "SmartPOS Retail Store"
        val items = extractItemsFromInvoice(invoice)

        val itemsText = if (items.isNotEmpty()) {
            items.joinToString("\n") { item ->
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)
                val unitSuffix = if (item.unit.isNotBlank() && !item.unit.equals("Pcs", ignoreCase = true)) " ${item.unit}" else ""
                val priceFormatted = String.format(Locale.US, "%.2f", item.price)
                val lineTotalFormatted = String.format(Locale.US, "%.2f", item.totalAmount)
                "• ${item.name} ($qtyStr$unitSuffix x ₹$priceFormatted) = *₹$lineTotalFormatted*"
            }
        } else {
            "• Billed Items (${invoice.itemsCount} pcs) = *₹${String.format(Locale.US, "%.2f", invoice.amount)}*"
        }

        // Subtotal, Discount & Tax breakdown
        val breakdownList = mutableListOf<String>()
        if (invoice.subtotal > 0 && (invoice.discountAmount > 0 || invoice.taxAmount > 0)) {
            breakdownList.add("Subtotal: ₹${String.format(Locale.US, "%.2f", invoice.subtotal)}")
        }
        if (invoice.discountAmount > 0) {
            breakdownList.add("Discount: -₹${String.format(Locale.US, "%.2f", invoice.discountAmount)}")
        }
        if (invoice.taxAmount > 0) {
            breakdownList.add("GST / Tax: +₹${String.format(Locale.US, "%.2f", invoice.taxAmount)}")
        }

        val breakdownSection = if (breakdownList.isNotEmpty()) {
            "\n" + breakdownList.joinToString("\n") + "\n--------------------------------"
        } else ""

        val formattedAmount = String.format(Locale.US, "%.2f", invoice.amount)
        val mode = if (invoice.paymentMode.isNotBlank()) invoice.paymentMode else "Cash"

        return """
        🧾 *INVOICE: #$invoiceNumber*
        🏬 *Store:* $storeName
        📅 *Date:* $dateStr
        --------------------------------
        *ITEMS PURCHASED:*
        $itemsText
        --------------------------------$breakdownSection
        💰 *Total Amount:* *₹$formattedAmount*
        💳 *Payment Mode:* $mode

        🙏 _Thank you for shopping with us!_
        """.trimIndent()
    }

    /**
     * Alias matching standard template signature: generateWhatsAppInvoiceText(invoice, storeName)
     */
    fun generateWhatsAppInvoiceText(invoice: InvoiceEntity, storeName: String): String {
        return formatInvoiceText(invoice, storeName)
    }

    /**
     * Formats WhatsApp invoice text directly from cart item details.
     */
    fun generateWhatsAppInvoiceTextFromItems(
        items: List<WhatsAppInvoiceItem>,
        invoiceNumber: String,
        storeName: String,
        subtotal: Double,
        discountAmount: Double,
        taxAmount: Double,
        totalAmount: Double,
        paymentMode: String,
        dateStr: String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
    ): String {
        val itemsText = if (items.isNotEmpty()) {
            items.joinToString("\n") { item ->
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)
                val unitSuffix = if (item.unit.isNotBlank() && !item.unit.equals("Pcs", ignoreCase = true)) " ${item.unit}" else ""
                val priceFormatted = String.format(Locale.US, "%.2f", item.price)
                val lineTotalFormatted = String.format(Locale.US, "%.2f", item.totalAmount)
                "• ${item.name} ($qtyStr$unitSuffix x ₹$priceFormatted) = *₹$lineTotalFormatted*"
            }
        } else {
            "• Billed Items = *₹${String.format(Locale.US, "%.2f", totalAmount)}*"
        }

        val breakdownList = mutableListOf<String>()
        if (subtotal > 0 && (discountAmount > 0 || taxAmount > 0)) {
            breakdownList.add("Subtotal: ₹${String.format(Locale.US, "%.2f", subtotal)}")
        }
        if (discountAmount > 0) {
            breakdownList.add("Discount: -₹${String.format(Locale.US, "%.2f", discountAmount)}")
        }
        if (taxAmount > 0) {
            breakdownList.add("GST / Tax: +₹${String.format(Locale.US, "%.2f", taxAmount)}")
        }

        val breakdownSection = if (breakdownList.isNotEmpty()) {
            "\n" + breakdownList.joinToString("\n") + "\n--------------------------------"
        } else ""

        val effectiveStore = if (storeName.isNotBlank()) storeName.trim() else "SmartPOS Retail Store"
        val formattedAmount = String.format(Locale.US, "%.2f", totalAmount)
        val mode = if (paymentMode.isNotBlank()) paymentMode else "Cash"

        return """
        🧾 *INVOICE: #$invoiceNumber*
        🏬 *Store:* $effectiveStore
        📅 *Date:* $dateStr
        --------------------------------
        *ITEMS PURCHASED:*
        $itemsText
        --------------------------------$breakdownSection
        💰 *Total Amount:* *₹$formattedAmount*
        💳 *Payment Mode:* $mode

        🙏 _Thank you for shopping with us!_
        """.trimIndent()
    }

    /**
     * Launches WhatsApp with pre-filled phone number and formatted invoice text.
     * Returns true if intent was launched successfully.
     */
    fun sendWhatsAppInvoice(
        context: Context,
        customerPhone: String,
        invoice: InvoiceEntity,
        businessName: String
    ): Boolean {
        val invoiceText = formatInvoiceText(invoice, businessName)
        return sendWhatsAppText(context, customerPhone, invoiceText)
    }

    /**
     * Dispatches pre-formatted text to customer's WhatsApp or general share sheet fallback.
     */
    fun sendWhatsAppText(
        context: Context,
        customerPhone: String,
        invoiceText: String
    ): Boolean {
        val cleanPhone = customerPhone.replace("\\D".toRegex(), "").takeLast(10)
        val hasValidPhone = cleanPhone.length >= 10

        return try {
            val encodedText = URLEncoder.encode(invoiceText, "UTF-8")
            val whatsappUri = if (hasValidPhone) {
                Uri.parse("https://api.whatsapp.com/send?phone=91$cleanPhone&text=$encodedText")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
            }

            // 1. Primary Attempt: Standard WhatsApp
            val primaryIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage("com.whatsapp")
            }

            try {
                context.startActivity(primaryIntent)
                Log.d(TAG, "Opened WhatsApp directly for customer: $cleanPhone")
                true
            } catch (e1: Exception) {
                // 2. Secondary Attempt: WhatsApp Business
                try {
                    val waBusinessIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        setPackage("com.whatsapp.w4b")
                    }
                    context.startActivity(waBusinessIntent)
                    Log.d(TAG, "Opened WhatsApp Business for customer: $cleanPhone")
                    true
                } catch (e2: Exception) {
                    // 3. Fallback: Generic Browser / Deep Link
                    try {
                        val genericIntent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(genericIntent)
                        true
                    } catch (e3: Exception) {
                        // 4. Fallback: Standard Share Chooser
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, invoiceText)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val chooser = Intent.createChooser(shareIntent, "Share Invoice Bill").apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(chooser)
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WhatsApp invoice: ${e.localizedMessage}")
            Toast.makeText(context, "Unable to launch WhatsApp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

