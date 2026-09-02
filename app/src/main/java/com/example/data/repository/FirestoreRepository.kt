package com.example.data.repository

import android.util.Log
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerTransactionEntity
import com.example.data.db.InvoiceEntity
import com.example.data.db.ProductEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "FirestoreRepository"
    }

    fun getCurrentUserId(providedUserId: String? = null): String? {
        if (!providedUserId.isNullOrBlank()) return providedUserId
        return auth.currentUser?.uid
    }

    /**
     * Purges cached offline Firestore documents from previous user session.
     */
    suspend fun clearFirestoreCache() = withContext(Dispatchers.IO) {
        try {
            firestore.clearPersistence().await()
            Log.d(TAG, "Firestore persistence cleared successfully")
        } catch (e: Exception) {
            Log.d(TAG, "Firestore clearPersistence skipped: ${e.localizedMessage}")
        }
    }

    /**
     * Stream invoices strictly scoped under users/{userId}/invoices
     */
    fun getInvoicesStream(userId: String? = null): Flow<List<InvoiceEntity>> = callbackFlow {
        val activeUid = getCurrentUserId(userId)
        if (activeUid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val collectionRef = firestore.collection("users").document(activeUid).collection("invoices")
        val listenerRegistration = collectionRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to invoices for $activeUid: ${error.localizedMessage}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        InvoiceEntity(
                            id = doc.id.hashCode(),
                            firestoreId = doc.id,
                            customerName = doc.getString("customerName") ?: "",
                            customerMobile = doc.getString("customerMobile") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            itemsCount = doc.getLong("itemsCount")?.toInt() ?: 0,
                            subtotal = doc.getDouble("subtotal") ?: 0.0,
                            discountAmount = doc.getDouble("discountAmount") ?: 0.0,
                            taxAmount = doc.getDouble("taxAmount") ?: 0.0,
                            paymentMode = doc.getString("paymentMode") ?: "Cash",
                            itemsSummary = doc.getString("itemsSummary") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            status = doc.getString("status") ?: "Paid",
                            doctorName = doc.getString("doctorName") ?: "",
                            patientInfo = doc.getString("patientInfo") ?: "",
                            dlNumber = doc.getString("dlNumber") ?: "",
                            gstin = doc.getString("gstin") ?: "",
                            tableNumber = doc.getString("tableNumber") ?: "",
                            orderType = doc.getString("orderType") ?: "",
                            isEdited = doc.getBoolean("isEdited") ?: false,
                            lastEditedTimestamp = doc.getLong("lastEditedTimestamp") ?: 0L,
                            itemsJson = doc.getString("itemsJson") ?: ""
                        )
                    }
                    trySend(list)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Stream inventory/products strictly scoped under users/{userId}/products & users/{userId}/inventory
     */
    fun getProductsStream(userId: String? = null): Flow<List<ProductEntity>> = callbackFlow {
        val activeUid = getCurrentUserId(userId)
        if (activeUid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val collectionRef = firestore.collection("users").document(activeUid).collection("products")
        val listenerRegistration = collectionRef
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to products for $activeUid: ${error.localizedMessage}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        ProductEntity(
                            id = doc.id.hashCode(),
                            firestoreId = doc.id,
                            name = doc.getString("name") ?: "",
                            salePrice = doc.getDouble("salePrice") ?: 0.0,
                            purchasePrice = doc.getDouble("purchasePrice") ?: 0.0,
                            stockQuantity = doc.getDouble("stockQuantity") ?: 0.0,
                            unit = doc.getString("unit") ?: "Pcs",
                            category = doc.getString("category") ?: "General",
                            barcode = doc.getString("barcode") ?: "",
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                            batchNumber = doc.getString("batchNumber") ?: "",
                            expiryDate = doc.getString("expiryDate") ?: "",
                            manufacturer = doc.getString("manufacturer") ?: "",
                            saltComposition = doc.getString("saltComposition") ?: "",
                            packUnitConfig = doc.getString("packUnitConfig") ?: "",
                            isRxRequired = doc.getBoolean("isRxRequired") ?: false,
                            minStockThreshold = doc.getDouble("minStockThreshold") ?: 5.0
                        )
                    }
                    trySend(list)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Stream Udhar customers strictly scoped under users/{userId}/customers
     */
    fun getCustomersStream(userId: String? = null): Flow<List<CustomerEntity>> = callbackFlow {
        val activeUid = getCurrentUserId(userId)
        if (activeUid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val collectionRef = firestore.collection("users").document(activeUid).collection("customers")
        val listenerRegistration = collectionRef
            .orderBy("lastTransactionTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to customers for $activeUid: ${error.localizedMessage}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        CustomerEntity(
                            id = doc.id.hashCode(),
                            firestoreId = doc.id,
                            name = doc.getString("name") ?: "",
                            mobileNumber = doc.getString("mobileNumber") ?: "",
                            totalPendingBalance = doc.getDouble("totalPendingBalance") ?: 0.0,
                            lastTransactionTimestamp = doc.getLong("lastTransactionTimestamp") ?: System.currentTimeMillis(),
                            reminderScheduledDate = doc.getLong("reminderScheduledDate") ?: 0L,
                            reminderStatus = doc.getString("reminderStatus") ?: "NONE"
                        )
                    }
                    trySend(list)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Stream Udhar transactions strictly scoped under users/{userId}/customers/{customerId}/transactions
     */
    fun getCustomerTransactionsStream(userId: String? = null, customerMobile: String): Flow<List<CustomerTransactionEntity>> = callbackFlow {
        val activeUid = getCurrentUserId(userId)
        if (activeUid.isNullOrBlank() || customerMobile.isBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val docId = customerMobile.replace("+", "").replace(" ", "")
        val collectionRef = firestore.collection("users").document(activeUid)
            .collection("customers").document(docId)
            .collection("transactions")

        val listenerRegistration = collectionRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to customer txs for $activeUid: ${error.localizedMessage}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        CustomerTransactionEntity(
                            id = doc.id.hashCode(),
                            firestoreId = doc.id,
                            customerMobile = doc.getString("customerMobile") ?: customerMobile,
                            customerName = doc.getString("customerName") ?: "",
                            type = doc.getString("type") ?: "DEBIT",
                            amount = doc.getDouble("amount") ?: 0.0,
                            paymentMode = doc.getString("paymentMode") ?: "Cash",
                            note = doc.getString("note") ?: "",
                            invoiceId = doc.getString("invoiceId") ?: "",
                            itemsJson = doc.getString("itemsJson") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    trySend(list)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Immediate direct update of Invoice to users/{userId}/invoices
     */
    suspend fun saveInvoice(
        userId: String? = null,
        invoice: InvoiceEntity
    ): String = withContext(Dispatchers.IO) {
        val activeUid = getCurrentUserId(userId) ?: return@withContext ""
        val userInvoicesRef = firestore.collection("users").document(activeUid).collection("invoices")
        val docRef = if (invoice.firestoreId.isNotBlank()) userInvoicesRef.document(invoice.firestoreId) else userInvoicesRef.document()

        val invoiceData = hashMapOf(
            "customerName" to invoice.customerName,
            "customerMobile" to invoice.customerMobile,
            "amount" to invoice.amount,
            "itemsCount" to invoice.itemsCount,
            "subtotal" to invoice.subtotal,
            "discountAmount" to invoice.discountAmount,
            "taxAmount" to invoice.taxAmount,
            "paymentMode" to invoice.paymentMode,
            "itemsSummary" to invoice.itemsSummary,
            "timestamp" to invoice.timestamp,
            "status" to invoice.status,
            "doctorName" to invoice.doctorName,
            "patientInfo" to invoice.patientInfo,
            "dlNumber" to invoice.dlNumber,
            "gstin" to invoice.gstin,
            "tableNumber" to invoice.tableNumber,
            "orderType" to invoice.orderType,
            "isEdited" to invoice.isEdited,
            "lastEditedTimestamp" to invoice.lastEditedTimestamp,
            "itemsJson" to invoice.itemsJson
        )

        docRef.set(invoiceData, SetOptions.merge()).await()
        docRef.id
    }

    /**
     * Immediate direct update of Product/Stock to users/{userId}/products & users/{userId}/inventory
     */
    suspend fun saveProduct(
        userId: String? = null,
        product: ProductEntity
    ): String = withContext(Dispatchers.IO) {
        val activeUid = getCurrentUserId(userId) ?: return@withContext ""
        val userProdsRef = firestore.collection("users").document(activeUid).collection("products")
        val userInvRef = firestore.collection("users").document(activeUid).collection("inventory")

        val docRef = if (product.firestoreId.isNotBlank()) userProdsRef.document(product.firestoreId) else userProdsRef.document()
        val docId = docRef.id

        val data = hashMapOf(
            "name" to product.name,
            "salePrice" to product.salePrice,
            "purchasePrice" to product.purchasePrice,
            "stockQuantity" to product.stockQuantity,
            "unit" to product.unit,
            "category" to product.category,
            "barcode" to product.barcode,
            "updatedAt" to System.currentTimeMillis(),
            "batchNumber" to product.batchNumber,
            "expiryDate" to product.expiryDate,
            "manufacturer" to product.manufacturer,
            "saltComposition" to product.saltComposition,
            "packUnitConfig" to product.packUnitConfig,
            "isRxRequired" to product.isRxRequired,
            "minStockThreshold" to product.minStockThreshold
        )

        docRef.set(data, SetOptions.merge()).await()
        userInvRef.document(docId).set(data, SetOptions.merge()).await()
        docId
    }

    suspend fun deleteProduct(userId: String? = null, firestoreId: String) = withContext(Dispatchers.IO) {
        val activeUid = getCurrentUserId(userId) ?: return@withContext
        if (firestoreId.isBlank()) return@withContext
        val userRef = firestore.collection("users").document(activeUid)
        userRef.collection("products").document(firestoreId).delete().await()
        userRef.collection("inventory").document(firestoreId).delete().await()
    }
}
