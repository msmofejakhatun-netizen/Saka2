package com.example.data.repository

import android.util.Log
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.db.InvoiceDao
import com.example.data.db.InvoiceEntity
import com.example.data.db.ProductDao
import com.example.data.db.ProductEntity
import com.example.data.db.UserDao
import com.example.data.db.UserEntity
import com.example.data.firebase.FirebaseManager
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BillingRepository(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val invoiceDao: InvoiceDao,
    private val productDao: ProductDao,
    private val customerDao: com.example.data.db.CustomerDao,
    private val customerTransactionDao: com.example.data.db.CustomerTransactionDao
) {
    private val TAG = "BillingRepository"

    // --- Users (Auth & Profile) ---
    
    suspend fun getUserByMobile(mobile: String): UserEntity? = withContext(Dispatchers.IO) {
        if (FirebaseManager.isFirebaseAvailable) {
            try {
                val snapshot = FirebaseManager.firestore
                    ?.collection("users")
                    ?.whereEqualTo("mobileNumber", mobile)
                    ?.limit(1)
                    ?.get()
                    ?.await()
                
                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null) {
                    return@withContext UserEntity(
                        id = doc.hashCode(),
                        fullName = doc.getString("fullName") ?: "",
                        businessName = doc.getString("businessName") ?: "",
                        mobileNumber = doc.getString("mobileNumber") ?: "",
                        passwordHash = "", // Auth credentials handled securely via Firebase Auth
                        category = doc.getString("category") ?: "Retail"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore getUserByMobile error: ${e.localizedMessage}")
            }
        }
        // Fallback to Room local database
        userDao.getUserByMobile(mobile)
    }

    suspend fun registerUserInFirebase(user: UserEntity, passwordRaw: String): String = withContext(Dispatchers.IO) {
        if (!FirebaseManager.isFirebaseAvailable) {
            throw IllegalStateException("Firebase service is unavailable.")
        }
        
        val auth = FirebaseManager.auth ?: throw IllegalStateException("Firebase Auth is null.")
        val firestore = FirebaseManager.firestore ?: throw IllegalStateException("Firestore is null.")
        
        // 1. Authenticate with normalized email matching user's phone or email
        val normalizedEmail = FirebaseManager.normalizeToEmail(user.mobileNumber)
        val authResult = auth.createUserWithEmailAndPassword(normalizedEmail, passwordRaw).await()
        val uid = authResult.user?.uid ?: throw IllegalStateException("Firebase UID generation failed.")

        // 2. Save professional business profile metadata to Firestore
        val userMap = hashMapOf(
            "uid" to uid,
            "fullName" to user.fullName,
            "businessName" to user.businessName,
            "mobileNumber" to user.mobileNumber,
            "category" to user.category,
            "createdAt" to System.currentTimeMillis()
        )
        
        firestore.collection("users").document(uid).set(userMap).await()
        uid
    }

    suspend fun loginUserInFirebase(mobileOrEmail: String, passwordRaw: String): UserEntity = withContext(Dispatchers.IO) {
        if (!FirebaseManager.isFirebaseAvailable) {
            throw IllegalStateException("Firebase service is unavailable.")
        }

        val auth = FirebaseManager.auth ?: throw IllegalStateException("Firebase Auth is null.")
        val firestore = FirebaseManager.firestore ?: throw IllegalStateException("Firestore is null.")
        
        val normalizedEmail = FirebaseManager.normalizeToEmail(mobileOrEmail)
        val authResult = auth.signInWithEmailAndPassword(normalizedEmail, passwordRaw).await()
        val uid = authResult.user?.uid ?: throw IllegalStateException("Verification failed.")

        val doc = firestore.collection("users").document(uid).get().await()
        if (doc.exists()) {
            UserEntity(
                id = doc.hashCode(),
                fullName = doc.getString("fullName") ?: "",
                businessName = doc.getString("businessName") ?: "",
                mobileNumber = doc.getString("mobileNumber") ?: "",
                passwordHash = "",
                category = doc.getString("category") ?: "Retail"
            )
        } else {
            throw IllegalStateException("Profile document not found in Firestore.")
        }
    }

    suspend fun insertUser(user: UserEntity): Long = withContext(Dispatchers.IO) {
        val existingByMobile = userDao.getUserByMobile(user.mobileNumber)
        val existingById = if (user.id != 0) userDao.getUserById(user.id) else null
        val resolvedId = existingById?.id ?: existingByMobile?.id ?: user.id
        val resolvedUser = user.copy(id = resolvedId)
        userDao.insertUser(resolvedUser)
    }

    suspend fun getUserByUid(uid: String): UserEntity? = withContext(Dispatchers.IO) {
        if (FirebaseManager.isFirebaseAvailable) {
            try {
                val doc = FirebaseManager.firestore
                    ?.collection("users")
                    ?.document(uid)
                    ?.get()
                    ?.await()
                if (doc != null && doc.exists()) {
                    val fullName = doc.getString("fullName") ?: doc.getString("displayName") ?: ""
                    val businessName = doc.getString("businessName") ?: doc.getString("shopName") ?: ""
                    val mobileNumber = doc.getString("mobileNumber") ?: doc.getString("phoneNumber") ?: doc.getString("mobile") ?: ""
                    val category = doc.getString("businessCategory") ?: doc.getString("category") ?: doc.getString("selectedCategory") ?: "Retail"
                    val upiId = doc.getString("upiId") ?: doc.getString("merchantUpi") ?: doc.getString("vpa") ?: "merchant@upi"
                    val merchantName = doc.getString("merchantName") ?: businessName

                    return@withContext UserEntity(
                        id = uid.hashCode(),
                        fullName = fullName,
                        businessName = businessName,
                        mobileNumber = mobileNumber,
                        passwordHash = "",
                        category = category,
                        upiId = upiId.ifBlank { "merchant@upi" },
                        merchantName = merchantName
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore getUserByUid error: ${e.localizedMessage}")
            }
        }
        null
    }

    suspend fun saveUserProfile(
        uid: String,
        fullName: String,
        businessName: String,
        mobileOrEmail: String,
        category: String,
        authProvider: String,
        upiId: String = "merchant@upi",
        merchantName: String = businessName
    ) = withContext(Dispatchers.IO) {
        val authUser = FirebaseManager.auth?.currentUser
        val targetUid = authUser?.uid ?: uid
        val defaultHashId = targetUid.hashCode()

        val existingByMobile = userDao.getUserByMobile(mobileOrEmail)
        val existingById = userDao.getUserById(defaultHashId)
        val resolvedId = existingById?.id ?: existingByMobile?.id ?: defaultHashId

        val localUser = UserEntity(
            id = resolvedId,
            fullName = fullName,
            businessName = businessName,
            mobileNumber = mobileOrEmail,
            passwordHash = "",
            category = category,
            upiId = upiId.ifBlank { "merchant@upi" },
            merchantName = merchantName.ifBlank { businessName }
        )

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val data = hashMapOf(
                    "uid" to targetUid,
                    "fullName" to fullName,
                    "displayName" to fullName,
                    "businessName" to businessName,
                    "businessCategory" to category,
                    "mobileNumber" to (if (mobileOrEmail.contains("@")) "" else mobileOrEmail),
                    "email" to (if (mobileOrEmail.contains("@")) mobileOrEmail else ""),
                    "category" to category,
                    "upiId" to upiId.ifBlank { "merchant@upi" },
                    "merchantName" to merchantName.ifBlank { businessName },
                    "role" to "user",
                    "authProvider" to authProvider,
                    "updatedAt" to System.currentTimeMillis()
                )
                try {
                    firestore.collection("users").document(targetUid).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore saveUserProfile exception: ${e.localizedMessage}")
                    // Save to local Room database before rethrowing so local copy is safe
                    userDao.insertUser(localUser)
                    throw e
                }
            }
        }
        
        // Save locally to SQLite Room Database for offline support
        userDao.insertUser(localUser)
    }

    // --- Dynamic Categories (Real-time Stream & Updates) ---

    val allCategories: Flow<List<CategoryEntity>> = callbackFlow {
        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                // Listen to Firestore 'categories' in real-time
                val listenerRegistration = firestore.collection("categories")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Firestore categories listener error: ${error.localizedMessage}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val list = snapshot.documents.map { doc ->
                                CategoryEntity(
                                    id = doc.id.hashCode(),
                                    name = doc.getString("name") ?: "",
                                    description = doc.getString("description") ?: "",
                                    iconName = doc.getString("iconName") ?: "shopping_basket",
                                    isEnabled = doc.getBoolean("isEnabled") ?: true
                                )
                            }
                            trySend(list)
                        }
                    }
                awaitClose { listenerRegistration.remove() }
            } else {
                close()
            }
        } else {
            // Local Database fallback
            categoryDao.getAllCategories().collect { list ->
                trySend(list)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun insertCategory(category: CategoryEntity): Long = withContext(Dispatchers.IO) {
        if (FirebaseManager.isFirebaseAvailable) {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val docRef = firestore.collection("categories").document()
                    val data = hashMapOf(
                        "name" to category.name,
                        "description" to category.description,
                        "iconName" to category.iconName,
                        "isEnabled" to category.isEnabled
                    )
                    docRef.set(data).await()
                    return@withContext docRef.id.hashCode().toLong()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore insertCategory error: ${e.localizedMessage}")
            }
        }
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        if (FirebaseManager.isFirebaseAvailable) {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    // Since Room id hashes doc.id, let's find the category document by name to update it safely
                    val snapshot = firestore.collection("categories")
                        .whereEqualTo("name", category.name)
                        .limit(1)
                        .get()
                        .await()
                    
                    val doc = snapshot.documents.firstOrNull()
                    if (doc != null) {
                        val data = hashMapOf(
                            "name" to category.name,
                            "description" to category.description,
                            "iconName" to category.iconName,
                            "isEnabled" to category.isEnabled
                        )
                        doc.reference.set(data).await()
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore updateCategory error: ${e.localizedMessage}")
            }
        }
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        if (FirebaseManager.isFirebaseAvailable) {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val snapshot = firestore.collection("categories")
                        .whereEqualTo("name", category.name)
                        .limit(1)
                        .get()
                        .await()
                    
                    val doc = snapshot.documents.firstOrNull()
                    if (doc != null) {
                        doc.reference.delete().await()
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore deleteCategory error: ${e.localizedMessage}")
            }
        }
        categoryDao.deleteCategory(category)
    }

    suspend fun clearLocalCache() = withContext(Dispatchers.IO) {
        try {
            productDao.clearAllProducts()
            customerDao.clearAllCustomers()
            customerTransactionDao.clearAllTransactions()
            invoiceDao.clearAllInvoices()
            userDao.clearAllUsers()
        } catch (e: Exception) {
            Log.e(TAG, "clearLocalCache error: ${e.localizedMessage}")
        }
    }

    suspend fun prepopulateCategoriesIfEmpty() = withContext(Dispatchers.IO) {
        // Prepopulate system default categories if empty
        if (categoryDao.getCategoryCount() == 0) {
            val defaults = listOf(
                CategoryEntity(name = "Pharmacy / Medical", description = "Medicines, Rx drugs, salt compositions, batch & expiry management", iconName = "local_pharmacy", isEnabled = true),
                CategoryEntity(name = "Kirana / Grocery", description = "Daily staples, loose items, pulses, rice, edible oils, spices", iconName = "shopping_basket", isEnabled = true),
                CategoryEntity(name = "Garments", description = "Clothing, activewear, and fashion accessories", iconName = "checkroom", isEnabled = true),
                CategoryEntity(name = "Electronics", description = "Smartphones, home appliances, laptops, and gadgets", iconName = "devices", isEnabled = true)
            )
            for (category in defaults) {
                categoryDao.insertCategory(category)
            }
        }

        // Prepopulate Firestore categories if available
        if (FirebaseManager.isFirebaseAvailable) {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val count = firestore.collection("categories").get().await().size()
                    if (count == 0) {
                        val defaults = listOf(
                            hashMapOf("name" to "Kirana / Grocery", "description" to "Daily staples, loose items, pulses, rice, edible oils, spices", "iconName" to "shopping_basket", "isEnabled" to true),
                            hashMapOf("name" to "Garments", "description" to "Clothing, activewear, and fashion accessories", "iconName" to "checkroom", "isEnabled" to true),
                            hashMapOf("name" to "Electronics", "description" to "Smartphones, home appliances, laptops, and gadgets", "iconName" to "devices", "isEnabled" to true),
                            hashMapOf("name" to "Pharmacy", "description" to "Medicines, healthcare devices, and wellness products", "iconName" to "local_pharmacy", "isEnabled" to true)
                        )
                        for (data in defaults) {
                            firestore.collection("categories").add(data).await()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Firestore prepopulate skipped or not permitted: ${e.localizedMessage}")
            }
        }
    }

    // --- Invoices ---

    fun getInvoicesStream(userUid: String): Flow<List<InvoiceEntity>> = callbackFlow {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (activeUid.isBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val collectionRef = firestore.collection("users").document(activeUid).collection("invoices")

                val listenerRegistration = collectionRef
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Firestore invoices listener error for $activeUid: ${error.localizedMessage}")
                            CoroutineScope(Dispatchers.IO).launch {
                                invoiceDao.getAllInvoices().collect { list ->
                                    trySend(list)
                                }
                            }
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
                                    itemsCount = doc.getLong("itemsCount")?.toInt() ?: 1,
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
            } else {
                close()
            }
        } else {
            invoiceDao.getAllInvoices().collect { list ->
                trySend(list)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    fun getTotalSalesStream(userUid: String): Flow<Double?> = callbackFlow {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (activeUid.isBlank()) {
            trySend(0.0)
            awaitClose()
            return@callbackFlow
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val collectionRef = firestore.collection("users").document(activeUid).collection("invoices")

                val listenerRegistration = collectionRef
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
                            val total = snapshot.documents.sumOf { doc ->
                                if (doc.getString("status") == "Paid") {
                                    doc.getDouble("amount") ?: 0.0
                                } else 0.0
                            }
                            trySend(total)
                        }
                    }
                awaitClose { listenerRegistration.remove() }
            } else {
                close()
            }
        } else {
            invoiceDao.getTotalSales().collect { sales ->
                trySend(sales)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    fun getInvoicesCountStream(userUid: String): Flow<Int> = callbackFlow {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (activeUid.isBlank()) {
            trySend(0)
            awaitClose()
            return@callbackFlow
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val collectionRef = firestore.collection("users").document(activeUid).collection("invoices")

                val listenerRegistration = collectionRef
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
                            trySend(snapshot.size())
                        }
                    }
                awaitClose { listenerRegistration.remove() }
            } else {
                close()
            }
        } else {
            invoiceDao.getInvoicesCount().collect { count ->
                trySend(count)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun insertInvoice(invoice: InvoiceEntity): Long = withContext(Dispatchers.IO) {
        val activeUid = FirebaseManager.auth?.currentUser?.uid ?: ""
        if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val docRef = firestore.collection("users").document(activeUid).collection("invoices").document()
                    val data = hashMapOf(
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
                        "status" to invoice.status
                    )
                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                    return@withContext docRef.id.hashCode().toLong()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore insertInvoice error: ${e.localizedMessage}")
            }
        }
        invoiceDao.insertInvoice(invoice)
    }

    suspend fun saveInvoiceAndDeductStock(
        userUid: String,
        invoice: InvoiceEntity,
        purchasedProducts: List<Pair<ProductEntity, Double>>
    ): InvoiceEntity = withContext(Dispatchers.IO) {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        var generatedFirestoreId = invoice.firestoreId

        if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                try {
                    val userInvoicesRef = firestore.collection("users").document(activeUid).collection("invoices")
                    val docRef = if (generatedFirestoreId.isNotBlank()) userInvoicesRef.document(generatedFirestoreId) else userInvoicesRef.document()
                    generatedFirestoreId = docRef.id

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

                    docRef.set(invoiceData, com.google.firebase.firestore.SetOptions.merge()).await()

                    // Auto-Deduct Stock for each purchased item under user subcollections
                    for ((prod, purchasedQty) in purchasedProducts) {
                        val newStock = (prod.stockQuantity - purchasedQty).coerceAtLeast(0.0)
                        val prodData = hashMapOf<String, Any>(
                            "stockQuantity" to newStock,
                            "updatedAt" to System.currentTimeMillis()
                        )

                        if (prod.firestoreId.isNotBlank()) {
                            firestore.collection("users").document(activeUid)
                                .collection("products").document(prod.firestoreId)
                                .set(prodData, com.google.firebase.firestore.SetOptions.merge())

                            firestore.collection("users").document(activeUid)
                                .collection("inventory").document(prod.firestoreId)
                                .set(prodData, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "saveInvoiceAndDeductStock firestore error: ${e.localizedMessage}")
                }
            }
        }

        // Deduct stock in local Room database
        for ((prod, purchasedQty) in purchasedProducts) {
            val newStock = (prod.stockQuantity - purchasedQty).coerceAtLeast(0.0)
            val updatedProd = prod.copy(stockQuantity = newStock, updatedAt = System.currentTimeMillis())
            productDao.insertProduct(updatedProd)
        }

        // Insert invoice into local Room database
        val localInvoice = invoice.copy(firestoreId = generatedFirestoreId)
        val newLocalId = invoiceDao.insertInvoice(localInvoice)
        val finalSavedInvoice = localInvoice.copy(id = if (localInvoice.id == 0) newLocalId.toInt() else localInvoice.id)
        return@withContext finalSavedInvoice
    }

    suspend fun updateInvoiceAndAdjustStock(
        userUid: String,
        updatedInvoice: InvoiceEntity,
        oldPurchasedList: List<Pair<ProductEntity, Double>>,
        newPurchasedList: List<Pair<ProductEntity, Double>>
    ) = withContext(Dispatchers.IO) {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")

        // 1. Revert old stock
        for ((prod, oldQty) in oldPurchasedList) {
            val currentProd = productDao.getProductById(prod.id) ?: prod
            val revertedStock = currentProd.stockQuantity + oldQty
            val updatedProd = currentProd.copy(stockQuantity = revertedStock, updatedAt = System.currentTimeMillis())
            productDao.insertProduct(updatedProd)

            if (FirebaseManager.isFirebaseAvailable && prod.firestoreId.isNotBlank() && activeUid.isNotBlank()) {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    try {
                        val prodData = hashMapOf<String, Any>("stockQuantity" to revertedStock, "updatedAt" to System.currentTimeMillis())
                        firestore.collection("users").document(activeUid).collection("products").document(prod.firestoreId).set(prodData, com.google.firebase.firestore.SetOptions.merge())
                        firestore.collection("users").document(activeUid).collection("inventory").document(prod.firestoreId).set(prodData, com.google.firebase.firestore.SetOptions.merge())
                    } catch (e: Exception) {
                        Log.e(TAG, "Revert stock firestore error: ${e.localizedMessage}")
                    }
                }
            }
        }

        // 2. Deduct new stock
        for ((prod, newQty) in newPurchasedList) {
            val currentProd = productDao.getProductById(prod.id) ?: prod
            val finalStock = (currentProd.stockQuantity - newQty).coerceAtLeast(0.0)
            val updatedProd = currentProd.copy(stockQuantity = finalStock, updatedAt = System.currentTimeMillis())
            productDao.insertProduct(updatedProd)

            if (FirebaseManager.isFirebaseAvailable && prod.firestoreId.isNotBlank() && activeUid.isNotBlank()) {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    try {
                        val prodData = hashMapOf<String, Any>("stockQuantity" to finalStock, "updatedAt" to System.currentTimeMillis())
                        firestore.collection("users").document(activeUid).collection("products").document(prod.firestoreId).set(prodData, com.google.firebase.firestore.SetOptions.merge())
                        firestore.collection("users").document(activeUid).collection("inventory").document(prod.firestoreId).set(prodData, com.google.firebase.firestore.SetOptions.merge())
                    } catch (e: Exception) {
                        Log.e(TAG, "Deduct new stock firestore error: ${e.localizedMessage}")
                    }
                }
            }
        }

        // 3. Save updated invoice in local Room DB
        invoiceDao.insertInvoice(updatedInvoice)

        // 4. Update Firestore invoice under user subcollection
        if (FirebaseManager.isFirebaseAvailable && updatedInvoice.firestoreId.isNotBlank() && activeUid.isNotBlank()) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                try {
                    val invoiceData = hashMapOf(
                        "customerName" to updatedInvoice.customerName,
                        "customerMobile" to updatedInvoice.customerMobile,
                        "amount" to updatedInvoice.amount,
                        "itemsCount" to updatedInvoice.itemsCount,
                        "subtotal" to updatedInvoice.subtotal,
                        "discountAmount" to updatedInvoice.discountAmount,
                        "taxAmount" to updatedInvoice.taxAmount,
                        "paymentMode" to updatedInvoice.paymentMode,
                        "itemsSummary" to updatedInvoice.itemsSummary,
                        "timestamp" to updatedInvoice.timestamp,
                        "status" to updatedInvoice.status,
                        "doctorName" to updatedInvoice.doctorName,
                        "patientInfo" to updatedInvoice.patientInfo,
                        "dlNumber" to updatedInvoice.dlNumber,
                        "gstin" to updatedInvoice.gstin,
                        "tableNumber" to updatedInvoice.tableNumber,
                        "orderType" to updatedInvoice.orderType,
                        "isEdited" to updatedInvoice.isEdited,
                        "lastEditedTimestamp" to updatedInvoice.lastEditedTimestamp,
                        "itemsJson" to updatedInvoice.itemsJson
                    )
                    firestore.collection("users").document(activeUid).collection("invoices").document(updatedInvoice.firestoreId).set(invoiceData, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.e(TAG, "updateInvoiceAndAdjustStock firestore error: ${e.localizedMessage}")
                }
            }
        }
    }

    // --- Product & Inventory Management ---

    fun getProductsStream(userUid: String): Flow<List<ProductEntity>> = callbackFlow {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (activeUid.isBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val collectionRef = firestore.collection("users").document(activeUid).collection("products")

                val listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Products Firestore snapshot listener error for $activeUid: ${error.localizedMessage}")
                        CoroutineScope(Dispatchers.IO).launch {
                            productDao.getAllProducts().collect { list ->
                                trySend(list)
                            }
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val productList = snapshot.documents.map { doc ->
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
                        trySend(productList)
                    }
                }
                awaitClose { listenerRegistration.remove() }
            } else {
                close()
            }
        } else {
            productDao.getAllProducts().collect { list ->
                trySend(list)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveProduct(userUid: String, product: ProductEntity) = withContext(Dispatchers.IO) {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        var generatedFirestoreId = product.firestoreId

        if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                try {
                    val userProdsRef = firestore.collection("users").document(activeUid).collection("products")
                    val userInvRef = firestore.collection("users").document(activeUid).collection("inventory")

                    val docRef = if (generatedFirestoreId.isNotBlank()) {
                        userProdsRef.document(generatedFirestoreId)
                    } else {
                        userProdsRef.document()
                    }
                    generatedFirestoreId = docRef.id

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
                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                    userInvRef.document(generatedFirestoreId).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore saveProduct exception: ${e.localizedMessage}")
                }
            }
        }

        val productToSave = product.copy(
            firestoreId = generatedFirestoreId,
            updatedAt = System.currentTimeMillis()
        )
        productDao.insertProduct(productToSave)
    }

    suspend fun deleteProduct(userUid: String, product: ProductEntity) = withContext(Dispatchers.IO) {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (FirebaseManager.isFirebaseAvailable && product.firestoreId.isNotBlank() && activeUid.isNotBlank()) {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val userRef = firestore.collection("users").document(activeUid)
                    userRef.collection("products").document(product.firestoreId).delete().await()
                    userRef.collection("inventory").document(product.firestoreId).delete().await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore deleteProduct error: ${e.localizedMessage}")
            }
        }
        if (product.id != 0) {
            productDao.deleteProductById(product.id)
        }
    }

    // --- Udhar Khata (Customer Credit Ledger) ---

    suspend fun saveCustomer(userUid: String, customer: com.example.data.db.CustomerEntity) = withContext(Dispatchers.IO) {
        if (customer.mobileNumber.isBlank()) return@withContext
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        val docId = customer.mobileNumber.replace("+", "").replace(" ", "")
        customerDao.insertCustomer(customer)

        if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                try {
                    val userRef = firestore.collection("users").document(activeUid)
                    val customerData = mapOf(
                        "name" to customer.name,
                        "mobileNumber" to customer.mobileNumber,
                        "totalPendingBalance" to customer.totalPendingBalance,
                        "lastTransactionTimestamp" to customer.lastTransactionTimestamp,
                        "reminderScheduledDate" to customer.reminderScheduledDate,
                        "reminderStatus" to customer.reminderStatus
                    )
                    userRef.collection("customers").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()
                    userRef.collection("udhar_ledger").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore saveCustomer error: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun updateCustomerReminderSchedule(userUid: String, customerMobile: String, date: Long, status: String) {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        val docId = customerMobile.replace("+", "").replace(" ", "").replace("-", "")
        customerDao.updateReminderSchedule(customerMobile, date, status)

        if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                try {
                    val userRef = firestore.collection("users").document(activeUid)
                    val reminderData = mapOf(
                        "reminderScheduledDate" to date,
                        "reminderStatus" to status
                    )
                    userRef.collection("customers").document(docId).set(reminderData, com.google.firebase.firestore.SetOptions.merge()).await()
                    userRef.collection("udhar_ledger").document(docId).set(reminderData, com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore updateCustomerReminderSchedule error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun getCustomersStream(userUid: String): Flow<List<com.example.data.db.CustomerEntity>> = callbackFlow {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (activeUid.isBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val collectionRef = firestore.collection("users").document(activeUid).collection("customers")

                val listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Customers Firestore snapshot listener error for $activeUid: ${error.localizedMessage}")
                        CoroutineScope(Dispatchers.IO).launch {
                            customerDao.getAllCustomers().collect { list ->
                                trySend(list)
                            }
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val customerList = snapshot.documents.map { doc ->
                            com.example.data.db.CustomerEntity(
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
                        trySend(customerList)
                    }
                }
                awaitClose { listenerRegistration.remove() }
            } else {
                close()
            }
        } else {
            customerDao.getAllCustomers().collect { list ->
                trySend(list)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    fun getCustomerTransactionsStream(userUid: String, customerMobile: String): Flow<List<com.example.data.db.CustomerTransactionEntity>> = callbackFlow {
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")
        if (activeUid.isBlank() || customerMobile.isBlank()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        if (FirebaseManager.isFirebaseAvailable) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                val docId = customerMobile.replace("+", "").replace(" ", "")
                val collectionRef = firestore.collection("users").document(activeUid)
                    .collection("customers").document(docId)
                    .collection("transactions")

                val listenerRegistration = collectionRef
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Customer transactions listener error for $activeUid: ${error.localizedMessage}")
                            CoroutineScope(Dispatchers.IO).launch {
                                customerTransactionDao.getTransactionsForCustomer(customerMobile).collect { list ->
                                    trySend(list)
                                }
                            }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val txList = snapshot.documents.map { doc ->
                                com.example.data.db.CustomerTransactionEntity(
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
                            trySend(txList)
                        }
                    }
                awaitClose { listenerRegistration.remove() }
            } else {
                close()
            }
        } else {
            customerTransactionDao.getTransactionsForCustomer(customerMobile).collect { list ->
                trySend(list)
            }
            awaitClose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun removeUdharTransactionForInvoice(
        userUid: String,
        customerMobile: String,
        invoiceId: String
    ) = withContext(Dispatchers.IO) {
        if (invoiceId.isBlank() || customerMobile.isBlank()) return@withContext
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")

        val directMatch = customerTransactionDao.getTransactionByInvoiceId(
            id1 = invoiceId,
            id2 = "Bill #$invoiceId",
            id3 = invoiceId.removePrefix("Bill #"),
            id4 = if (invoiceId.contains("_") || invoiceId.length > 8) invoiceId else "Bill #$invoiceId"
        )
        val existingTx = directMatch ?: customerTransactionDao.getTransactionsForCustomerSync(customerMobile).firstOrNull { tx ->
            tx.invoiceId == invoiceId ||
            tx.invoiceId == "Bill #$invoiceId" ||
            (invoiceId.isNotBlank() && tx.note.contains("Bill #$invoiceId", ignoreCase = true)) ||
            (invoiceId.isNotBlank() && tx.note.contains("Bill #${invoiceId.removePrefix("Bill #")}", ignoreCase = true))
        } ?: return@withContext

        val now = System.currentTimeMillis()
        val docId = customerMobile.replace("+", "").replace(" ", "")
        val existingLocal = customerDao.getCustomerByMobile(customerMobile)
        val currentBalance = existingLocal?.totalPendingBalance ?: 0.0

        val newBalance = if (existingTx.type == "DEBIT") {
            (currentBalance - existingTx.amount).coerceAtLeast(0.0)
        } else {
            currentBalance + existingTx.amount
        }

        val updatedCustomer = com.example.data.db.CustomerEntity(
            id = existingLocal?.id ?: 0,
            firestoreId = docId,
            name = existingLocal?.name ?: "Customer",
            mobileNumber = customerMobile,
            totalPendingBalance = newBalance,
            lastTransactionTimestamp = now
        )

        customerDao.insertCustomer(updatedCustomer)
        customerTransactionDao.deleteTransactionByInvoiceId(
            id1 = existingTx.invoiceId,
            id2 = invoiceId,
            id3 = "Bill #$invoiceId",
            id4 = "Bill #${existingTx.invoiceId}"
        )

        if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
            val firestore = FirebaseManager.firestore
            if (firestore != null) {
                try {
                    val customerData = hashMapOf(
                        "name" to updatedCustomer.name,
                        "mobileNumber" to updatedCustomer.mobileNumber,
                        "totalPendingBalance" to updatedCustomer.totalPendingBalance,
                        "lastTransactionTimestamp" to now
                    )
                    val userRef = firestore.collection("users").document(activeUid)
                    userRef.collection("customers").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()
                    userRef.collection("udhar_ledger").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()
                    if (existingTx.firestoreId.isNotBlank()) {
                        userRef.collection("customers").document(docId)
                            .collection("transactions").document(existingTx.firestoreId).delete().await()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "removeUdharTransactionForInvoice Firestore error: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun recordUdharOrJamaTransaction(
        userUid: String,
        customerName: String,
        customerMobile: String,
        type: String, // "DEBIT" (Udhar) or "CREDIT" (Jama)
        amount: Double,
        paymentMode: String = "Cash",
        note: String = "",
        invoiceId: String = "",
        itemsJson: String = ""
    ) = withContext(Dispatchers.IO) {
        if (customerMobile.isBlank() || amount <= 0.0) return@withContext
        val activeUid = if (userUid.isNotBlank()) userUid else (FirebaseManager.auth?.currentUser?.uid ?: "")

        val now = System.currentTimeMillis()
        val docId = customerMobile.replace("+", "").replace(" ", "")

        val existingLocal = customerDao.getCustomerByMobile(customerMobile)
        val currentBalance = existingLocal?.totalPendingBalance ?: 0.0

        val existingTx = if (invoiceId.isNotBlank()) {
            val directMatch = customerTransactionDao.getTransactionByInvoiceId(
                id1 = invoiceId,
                id2 = "Bill #$invoiceId",
                id3 = invoiceId.removePrefix("Bill #"),
                id4 = if (invoiceId.contains("_") || invoiceId.length > 8) invoiceId else "Bill #$invoiceId"
            )
            directMatch ?: customerTransactionDao.getTransactionsForCustomerSync(customerMobile).firstOrNull { tx ->
                tx.invoiceId == invoiceId ||
                tx.invoiceId == "Bill #$invoiceId" ||
                (invoiceId.isNotBlank() && tx.note.contains("Bill #$invoiceId", ignoreCase = true)) ||
                (invoiceId.isNotBlank() && tx.note.contains("Bill #${invoiceId.removePrefix("Bill #")}", ignoreCase = true))
            }
        } else null

        if (existingTx != null) {
            val balanceWithoutOldTx = if (existingTx.type == "DEBIT") {
                (currentBalance - existingTx.amount).coerceAtLeast(0.0)
            } else {
                currentBalance + existingTx.amount
            }

            val newBalance = if (type == "DEBIT") {
                balanceWithoutOldTx + amount
            } else {
                (balanceWithoutOldTx - amount).coerceAtLeast(0.0)
            }

            val updatedCustomer = com.example.data.db.CustomerEntity(
                id = existingLocal?.id ?: 0,
                firestoreId = docId,
                name = if (customerName.isNotBlank()) customerName else (existingLocal?.name ?: "Customer"),
                mobileNumber = customerMobile,
                totalPendingBalance = newBalance,
                lastTransactionTimestamp = now
            )

            val updatedTx = existingTx.copy(
                customerMobile = customerMobile,
                customerName = updatedCustomer.name,
                type = type,
                amount = amount,
                paymentMode = paymentMode,
                note = note,
                invoiceId = invoiceId,
                itemsJson = itemsJson,
                isEdited = true,
                timestamp = now
            )

            if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    try {
                        val customerData = hashMapOf(
                            "name" to updatedCustomer.name,
                            "mobileNumber" to updatedCustomer.mobileNumber,
                            "totalPendingBalance" to updatedCustomer.totalPendingBalance,
                            "lastTransactionTimestamp" to now
                        )

                        val txData = hashMapOf(
                            "customerMobile" to customerMobile,
                            "customerName" to updatedCustomer.name,
                            "type" to type,
                            "amount" to amount,
                            "paymentMode" to paymentMode,
                            "note" to note,
                            "invoiceId" to invoiceId,
                            "itemsJson" to itemsJson,
                            "isEdited" to true,
                            "timestamp" to now
                        )

                        val userRef = firestore.collection("users").document(activeUid)
                        userRef.collection("customers").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()
                        userRef.collection("udhar_ledger").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()

                        val txDocId = existingTx.firestoreId.ifBlank { existingTx.id.toString() }
                        userRef.collection("customers").document(docId).collection("transactions").document(txDocId).set(txData, com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (e: Exception) {
                        Log.e(TAG, "recordUdharOrJamaTransaction Firestore update error: ${e.localizedMessage}")
                    }
                }
            }

            customerDao.insertCustomer(updatedCustomer)
            customerTransactionDao.insertTransaction(updatedTx)
        } else {
            val newBalance = if (type == "DEBIT") {
                currentBalance + amount
            } else {
                (currentBalance - amount).coerceAtLeast(0.0)
            }

            val updatedCustomer = com.example.data.db.CustomerEntity(
                id = existingLocal?.id ?: 0,
                firestoreId = docId,
                name = if (customerName.isNotBlank()) customerName else (existingLocal?.name ?: "Customer"),
                mobileNumber = customerMobile,
                totalPendingBalance = newBalance,
                lastTransactionTimestamp = now
            )

            var txEntity = com.example.data.db.CustomerTransactionEntity(
                customerMobile = customerMobile,
                customerName = updatedCustomer.name,
                type = type,
                amount = amount,
                paymentMode = paymentMode,
                note = note,
                invoiceId = invoiceId,
                itemsJson = itemsJson,
                isEdited = false,
                timestamp = now
            )

            if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    try {
                        val customerData = hashMapOf(
                            "name" to updatedCustomer.name,
                            "mobileNumber" to updatedCustomer.mobileNumber,
                            "totalPendingBalance" to updatedCustomer.totalPendingBalance,
                            "lastTransactionTimestamp" to now
                        )

                        val txData = hashMapOf(
                            "customerMobile" to customerMobile,
                            "customerName" to updatedCustomer.name,
                            "type" to type,
                            "amount" to amount,
                            "paymentMode" to paymentMode,
                            "note" to note,
                            "invoiceId" to invoiceId,
                            "itemsJson" to itemsJson,
                            "isEdited" to false,
                            "timestamp" to now
                        )

                        val userRef = firestore.collection("users").document(activeUid)
                        userRef.collection("customers").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()
                        userRef.collection("udhar_ledger").document(docId).set(customerData, com.google.firebase.firestore.SetOptions.merge()).await()

                        val addedDoc = userRef.collection("customers").document(docId).collection("transactions").add(txData).await()
                        txEntity = txEntity.copy(firestoreId = addedDoc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "recordUdharOrJamaTransaction Firestore error: ${e.localizedMessage}")
                    }
                }
            }

            customerDao.insertCustomer(updatedCustomer)
            customerTransactionDao.insertTransaction(txEntity)
        }
    }

    companion object {
        @Volatile
        private var instance: BillingRepository? = null

        fun getInstance(context: android.content.Context): BillingRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = com.example.data.db.AppDatabase.getDatabase(context.applicationContext)
                    BillingRepository(
                        userDao = db.userDao(),
                        categoryDao = db.categoryDao(),
                        invoiceDao = db.invoiceDao(),
                        productDao = db.productDao(),
                        customerDao = db.customerDao(),
                        customerTransactionDao = db.customerTransactionDao()
                    ).also { instance = it }
                }
            }
        }
    }
}
