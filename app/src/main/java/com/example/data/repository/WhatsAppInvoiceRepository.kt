package com.example.data.repository

import android.util.Log
import com.example.data.api.ApiResponse
import com.example.data.api.InvoiceRequestPayload
import com.example.data.api.ItemPayload
import com.example.data.api.WhatsAppApiService
import com.example.data.db.InvoiceEntity
import com.example.util.WhatsAppInvoiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository handling central WhatsApp invoice dispatch requests via Retrofit API.
 */
class WhatsAppInvoiceRepository(
    private val apiService: WhatsAppApiService = WhatsAppApiService.getInstance()
) {
    companion object {
        private const val TAG = "WhatsAppInvoiceRepo"
    }

    /**
     * Dispatches an itemized digital invoice directly via central SmartPOS WhatsApp endpoint.
     */
    suspend fun sendCentralInvoice(
        customerPhone: String,
        storeName: String,
        invoiceNumber: String,
        totalAmount: Double,
        date: String,
        items: List<ItemPayload>,
        paymentMode: String = "Cash",
        customerName: String = "",
        subtotal: Double = totalAmount,
        discountAmount: Double = 0.0,
        taxAmount: Double = 0.0,
        storePhone: String = "",
        previousUdhar: Double = 0.0,
        totalOutstanding: Double = 0.0
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = customerPhone.replace("[^0-9]".toRegex(), "").takeLast(10)
            if (cleanPhone.length != 10) {
                val err = "Customer mobile must be 10 digits: $customerPhone"
                Log.w(TAG, err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val payload = InvoiceRequestPayload(
                customerPhone = cleanPhone,
                storeName = storeName.ifBlank { "SmartPOS Retail Store" },
                storePhone = storePhone,
                invoiceNumber = invoiceNumber,
                totalAmount = totalAmount,
                paymentMode = paymentMode,
                previousUdhar = previousUdhar,
                totalOutstanding = totalOutstanding,
                date = date,
                items = items,
                customerName = customerName,
                subtotal = subtotal,
                discountAmount = discountAmount,
                taxAmount = taxAmount
            )

            Log.d(TAG, "Sending central WhatsApp invoice to $cleanPhone for bill $invoiceNumber")
            val response = apiService.sendInvoice(payload)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "Central WhatsApp invoice success response: ${body.message}")
                Result.success(body)
            } else {
                val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()} ${response.message()}"
                Log.w(TAG, "Central WhatsApp invoice API error: $errorBody")
                Result.failure(Exception("API Error (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during central WhatsApp invoice dispatch: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    /**
     * Converts an InvoiceEntity into payload parameters and dispatches via central WhatsApp API.
     */
    suspend fun sendInvoiceFromEntity(
        invoice: InvoiceEntity,
        storeName: String
    ): Result<ApiResponse> {
        val extractedItems = WhatsAppInvoiceHelper.extractItemsFromInvoice(invoice).map { item ->
            ItemPayload(
                name = item.name,
                quantity = item.quantity,
                unit = item.unit.ifBlank { "Pcs" },
                unitPrice = item.price,
                totalPrice = item.totalAmount
            )
        }
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateString = dateFormat.format(Date(if (invoice.timestamp > 0) invoice.timestamp else System.currentTimeMillis()))
        val invoiceNum = if (invoice.firestoreId.isNotBlank()) {
            "#${invoice.firestoreId.take(8).uppercase()}"
        } else if (invoice.id > 0) {
            "#BILL-${invoice.id}"
        } else {
            "#BILL-${(1000..9999).random()}"
        }

        return sendCentralInvoice(
            customerPhone = invoice.customerMobile,
            storeName = storeName,
            invoiceNumber = invoiceNum,
            totalAmount = invoice.amount,
            date = dateString,
            items = extractedItems,
            paymentMode = invoice.paymentMode,
            customerName = invoice.customerName,
            subtotal = if (invoice.subtotal > 0) invoice.subtotal else invoice.amount,
            discountAmount = invoice.discountAmount,
            taxAmount = invoice.taxAmount
        )
    }
}
