package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

object WhatsAppReminderHelper {

    /**
     * Builds a standard deep-linked UPI Payment URL.
     * Example: upi://pay?pa={merchantUpiId}&pn={merchantStoreName}&am={pendingBalance}&cu=INR&tn=Udhar%20Payment%20({customerName})
     */
    fun buildUpiPaymentUrl(
        upiId: String,
        merchantName: String,
        amount: Double,
        customerName: String = "",
        note: String = ""
    ): String {
        val cleanUpi = if (upiId.isNotBlank()) upiId.trim() else "merchant@upi"
        val cleanName = Uri.encode(if (merchantName.isNotBlank()) merchantName.trim() else "SmartPOS Store")
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val txnNote = when {
            note.isNotBlank() -> note.trim()
            customerName.isNotBlank() -> "Udhar Payment ($customerName)"
            else -> "Udhar Payment"
        }
        val cleanNote = Uri.encode(txnNote)

        return "upi://pay?pa=$cleanUpi&pn=$cleanName&am=$formattedAmount&cu=INR&tn=$cleanNote"
    }

    /**
     * Appends an interactive UPI deep link into WhatsApp reminder text.
     */
    fun appendInteractiveUpiPaymentLink(
        originalMessage: String,
        upiId: String,
        merchantName: String,
        amount: Double,
        customerName: String = "",
        note: String = ""
    ): String {
        val upiUrl = buildUpiPaymentUrl(upiId, merchantName, amount, customerName, note)
        val cleanUpi = if (upiId.isNotBlank()) upiId.trim() else "merchant@upi"

        val paymentBlock = "\n\n" +
            "📲 *Click to Pay Instantly:*\n" +
            "$upiUrl\n\n" +
            "• Merchant UPI ID: $cleanUpi\n\n" +
            "⚡ _Powered by SmartPOS_\n" +
            "🔗 https://smartpos-ashen.vercel.app/"

        return originalMessage.trim() + paymentBlock
    }

    /**
     * Directly launches the native device UPI app picker for a given amount.
     */
    fun launchUpiPaymentIntent(
        context: Context,
        upiId: String,
        merchantName: String,
        amount: Double,
        note: String = "Udhar Clearance"
    ) {
        val upiUrl = buildUpiPaymentUrl(upiId, merchantName, amount, note)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUrl))
            val chooser = Intent.createChooser(intent, "Pay via UPI App")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No UPI Payment App found on device", Toast.LENGTH_SHORT).show()
        }
    }
}
