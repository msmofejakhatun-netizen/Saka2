package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val businessName: String,
    val mobileNumber: String,
    val passwordHash: String,
    val category: String,
    val upiId: String = "merchant@upi",
    val merchantName: String = "",
    val dlNumber: String = "DL-20B/10492/2024",
    val gstin: String = "27ABCDE1234F1Z5"
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val iconName: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firestoreId: String = "",
    val customerName: String,
    val customerMobile: String = "",
    val amount: Double,
    val itemsCount: Int,
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val paymentMode: String = "Cash", // Cash, UPI / QR, Online, Credit (Udhar)
    val itemsSummary: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Paid", // Paid, Unpaid, Pending
    val doctorName: String = "",
    val patientInfo: String = "",
    val dlNumber: String = "",
    val gstin: String = "",
    val tableNumber: String = "",
    val orderType: String = "",
    val isEdited: Boolean = false,
    val lastEditedTimestamp: Long = 0L,
    val itemsJson: String = ""
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firestoreId: String = "",
    val name: String,
    val salePrice: Double,
    val purchasePrice: Double = 0.0,
    val stockQuantity: Double,
    val unit: String = "Pcs", // Pcs, Kg, Ltr, Box, Meter, Strip, Bottle
    val category: String = "General",
    val barcode: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val batchNumber: String = "",
    val expiryDate: String = "", // e.g. "11/2026" or "10/2025"
    val manufacturer: String = "",
    val saltComposition: String = "",
    val packUnitConfig: String = "", // e.g. "1 Strip = 10 Tablets"
    val isRxRequired: Boolean = false,
    val size: String = "", // Garments e.g. "S", "M", "L", "XL", "32"
    val color: String = "", // Garments e.g. "Red", "Blue", "Black"
    val minStockThreshold: Double = 5.0
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firestoreId: String = "",
    val name: String,
    val mobileNumber: String,
    val totalPendingBalance: Double = 0.0,
    val lastTransactionTimestamp: Long = System.currentTimeMillis(),
    val reminderScheduledDate: Long = 0L,
    val reminderStatus: String = "NONE" // NONE, SCHEDULED, SENT
)

@Entity(tableName = "customer_transactions")
data class CustomerTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firestoreId: String = "",
    val customerMobile: String,
    val customerName: String = "",
    val type: String, // DEBIT (Udhar) or CREDIT (Jama)
    val amount: Double,
    val paymentMode: String = "Cash", // Cash, UPI, Online
    val note: String = "",
    val invoiceId: String = "",
    val itemsJson: String = "",
    val isEdited: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
