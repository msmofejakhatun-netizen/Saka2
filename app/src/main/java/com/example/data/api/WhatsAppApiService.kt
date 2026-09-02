package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Item data model for Central WhatsApp invoice dispatch payload.
 */
@JsonClass(generateAdapter = true)
data class ItemPayload(
    @Json(name = "name") val name: String,
    @Json(name = "quantity") val quantity: Double,
    @Json(name = "unit") val unit: String = "Pcs",
    @Json(name = "unitPrice") val unitPrice: Double = 0.0,
    @Json(name = "totalPrice") val totalPrice: Double = quantity * unitPrice
)

/**
 * Request payload for central server-side WhatsApp invoice generation and dispatch.
 */
@JsonClass(generateAdapter = true)
data class InvoiceRequestPayload(
    @Json(name = "customerPhone") val customerPhone: String,
    @Json(name = "storeName") val storeName: String,
    @Json(name = "storePhone") val storePhone: String = "",
    @Json(name = "invoiceNumber") val invoiceNumber: String,
    @Json(name = "totalAmount") val totalAmount: Double,
    @Json(name = "paymentMode") val paymentMode: String = "Cash",
    @Json(name = "previousUdhar") val previousUdhar: Double = 0.0,
    @Json(name = "totalOutstanding") val totalOutstanding: Double = 0.0,
    @Json(name = "date") val date: String,
    @Json(name = "items") val items: List<ItemPayload> = emptyList(),
    @Json(name = "customerName") val customerName: String = "",
    @Json(name = "subtotal") val subtotal: Double = totalAmount,
    @Json(name = "discountAmount") val discountAmount: Double = 0.0,
    @Json(name = "taxAmount") val taxAmount: Double = 0.0
)

/**
 * Request payload for central server-side automated Udhar Payment Reminders.
 */
@JsonClass(generateAdapter = true)
data class UdharReminderRequestPayload(
    @Json(name = "customerPhone") val customerPhone: String,
    @Json(name = "customerName") val customerName: String,
    @Json(name = "storeName") val storeName: String,
    @Json(name = "storePhone") val storePhone: String = "",
    @Json(name = "merchantUpiId") val merchantUpiId: String = "",
    @Json(name = "pendingBalance") val pendingBalance: Double,
    @Json(name = "lastTxnDate") val lastTxnDate: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "upiLink") val upiLink: String = ""
)

/**
 * Server response model for WhatsApp dispatch endpoint.
 */
@JsonClass(generateAdapter = true)
data class ApiResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "messageId") val messageId: String? = null,
    @Json(name = "status") val status: String? = null
)

/**
 * Retrofit API interface for automated central WhatsApp invoice and reminder messaging.
 */
interface WhatsAppApiService {

    @POST("api/send-central-invoice")
    suspend fun sendInvoice(
        @Body invoiceData: InvoiceRequestPayload
    ): Response<ApiResponse>

    @POST("api/send-central-invoice")
    suspend fun sendCentralInvoice(
        @Body request: InvoiceRequestPayload
    ): Response<ApiResponse> = sendInvoice(request)

    @POST("api/send-udhar-reminder")
    suspend fun sendUdharReminder(
        @Body request: UdharReminderRequestPayload
    ): Response<ApiResponse>

    companion object {
        fun getInstance(): WhatsAppApiService = RetrofitClient.whatsAppApiService
    }
}
