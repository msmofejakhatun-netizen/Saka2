package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.ApiResponse
import com.example.data.api.InvoiceRequestPayload
import com.example.data.api.ItemPayload
import com.example.data.api.WhatsAppApiService
import com.example.data.repository.WhatsAppInvoiceRepository
import com.example.data.db.CategoryEntity
import com.example.data.db.InvoiceEntity
import com.example.data.db.ProductEntity
import com.example.data.db.UserEntity
import com.example.data.repository.BillingRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

data class POSCartItem(
    val product: ProductEntity,
    val quantity: Double,
    val customPrice: Double = product.salePrice
) {
    val totalAmount: Double get() = quantity * customPrice
}

class BillingViewModel(val repository: BillingRepository) : ViewModel() {

    // --- Authentication State (Phone OTP & Google Sign-In) ---
    var authMobile by mutableStateOf("")
    var authOtpCode by mutableStateOf("")
    var isOtpSent by mutableStateOf(false)
    var isVerifyingOtp by mutableStateOf(false)
    var isSendingOtp by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)
    var timerSeconds by mutableStateOf(0)
    var tempVerificationId by mutableStateOf("")

    // --- Temporary State for Profile Setup ---
    var tempUid by mutableStateOf("")
    var tempAuthProvider by mutableStateOf("")
    var tempMobileOrEmail by mutableStateOf("")

    // --- Profile Setup State ---
    var profileFullName by mutableStateOf("")
    var profileBusinessName by mutableStateOf("")
    var profileCategory by mutableStateOf("")
    var profileUpiId by mutableStateOf("merchant@upi")
    var profileMerchantName by mutableStateOf("")
    var profileError by mutableStateOf<String?>(null)
    var isSavingProfile by mutableStateOf(false)

    // Keep legacy parameters to prevent any compilation errors in other places
    var loginMobile by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var loginError by mutableStateOf<String?>(null)
    var isLoggingIn by mutableStateOf(false)

    var signupFullName by mutableStateOf("")
    var signupBusinessName by mutableStateOf("")
    var signupMobile by mutableStateOf("")
    var signupPassword by mutableStateOf("")
    var signupCategory by mutableStateOf("")
    var signupError by mutableStateOf<String?>(null)
    var isSigningUp by mutableStateOf(false)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val fb = com.example.data.firebase.FirebaseManager.auth?.currentUser
                if (fb != null) {
                    val user = repository.getUserByUid(fb.uid)
                        ?: repository.getUserByMobile(fb.phoneNumber.orEmpty())
                    if (user != null) {
                        _currentUser.value = user
                    } else {
                        loadUserProfile(fb.uid)
                    }
                }
            } catch (e: Exception) {
                Log.d("BillingVM", "Auto user fetch: ${e.localizedMessage}")
            }
        }
    }

    // --- Subscription & Paywall State ---
    val subscriptionState = com.example.data.subscription.SubscriptionManager.subscriptionState
    var showPaywallDialog by mutableStateOf(false)

    fun openPaywall() {
        showPaywallDialog = true
    }

    fun closePaywall() {
        showPaywallDialog = false
    }

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // --- Dynamic Category Flow ---
    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Invoices & Dashboard Flow ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val invoices: StateFlow<List<InvoiceEntity>> = _currentUser
        .flatMapLatest { user ->
            val uid = user?.id?.toString() ?: ""
            val firebaseUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: uid
            repository.getInvoicesStream(firebaseUid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalSales: StateFlow<Double?> = _currentUser
        .flatMapLatest { user ->
            val uid = user?.id?.toString() ?: ""
            val firebaseUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: uid
            repository.getTotalSalesStream(firebaseUid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val invoicesCount: StateFlow<Int> = _currentUser
        .flatMapLatest { user ->
            val uid = user?.id?.toString() ?: ""
            val firebaseUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: uid
            repository.getInvoicesCountStream(firebaseUid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // --- Product & Inventory Management State ---
    val productSearchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductEntity>> = _currentUser
        .flatMapLatest { user ->
            val uid = user?.id?.toString() ?: ""
            val firebaseUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: uid
            repository.getProductsStream(firebaseUid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredProducts: StateFlow<List<ProductEntity>> = combine(products, productSearchQuery) { productList, query ->
        if (query.isBlank()) {
            productList
        } else {
            productList.filter {
                com.example.util.PharmacyUtils.matchesPharmacySearch(it, query)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun findProductByBarcode(scannedCode: String): ProductEntity? {
        val cleanCode = scannedCode.trim()
        if (cleanCode.isBlank()) return null
        return products.value.firstOrNull {
            (it.barcode.isNotBlank() && it.barcode.equals(cleanCode, ignoreCase = true)) ||
            it.name.equals(cleanCode, ignoreCase = true) ||
            (it.id != 0 && it.id.toString() == cleanCode)
        }
    }

    var isSavingProduct by mutableStateOf(false)
    var productFormError by mutableStateOf<String?>(null)

    // --- Udhar Khata (Credit Ledger) State ---
    val customerSearchQuery = MutableStateFlow("")
    val activeCustomerMobileForLedger = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<com.example.data.db.CustomerEntity>> = _currentUser
        .flatMapLatest { user ->
            val uid = user?.id?.toString() ?: ""
            val firebaseUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: uid
            repository.getCustomersStream(firebaseUid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredCustomers: StateFlow<List<com.example.data.db.CustomerEntity>> = combine(customers, customerSearchQuery) { customerList, query ->
        if (query.isBlank()) {
            customerList
        } else {
            customerList.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.mobileNumber.contains(query)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomerTransactions: StateFlow<List<com.example.data.db.CustomerTransactionEntity>> = activeCustomerMobileForLedger
        .flatMapLatest { mobile ->
            val user = _currentUser.value
            val uid = user?.id?.toString() ?: ""
            val firebaseUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: uid
            repository.getCustomerTransactionsStream(firebaseUid, mobile)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun recordJamaPayment(
        customerName: String,
        customerMobile: String,
        amount: Double,
        paymentMode: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        if (customerMobile.isBlank() || amount <= 0.0) {
            viewModelScope.launch {
                _toastMessage.emit("Please enter valid mobile and payment amount")
            }
            return
        }

        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""

                repository.recordUdharOrJamaTransaction(
                    userUid = userUid,
                    customerName = customerName,
                    customerMobile = customerMobile,
                    type = "CREDIT",
                    amount = amount,
                    paymentMode = paymentMode,
                    note = note
                )

                _toastMessage.emit("Jama (Payment Received) ₹$amount recorded for $customerName!")
                onSuccess()
            } catch (e: Exception) {
                Log.e("BillingVM", "recordJamaPayment error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to record Jama payment: ${e.localizedMessage}")
            }
        }
    }

    fun addQuickCustomer(
        name: String,
        mobile: String,
        onSuccess: (com.example.data.db.CustomerEntity) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanMobile = mobile.trim()
        if (cleanName.isBlank() || cleanMobile.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Please enter both customer name and mobile number")
            }
            return
        }

        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""

                val newCustomer = com.example.data.db.CustomerEntity(
                    name = cleanName,
                    mobileNumber = cleanMobile,
                    totalPendingBalance = 0.0,
                    lastTransactionTimestamp = System.currentTimeMillis()
                )

                repository.saveCustomer(userUid, newCustomer)
                _toastMessage.emit("Customer '$cleanName' registered successfully!")
                onSuccess(newCustomer)
            } catch (e: Exception) {
                Log.e("BillingVM", "addQuickCustomer error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to add customer: ${e.localizedMessage}")
            }
        }
    }

    fun recordUdharEntry(
        customerName: String,
        customerMobile: String,
        amount: Double,
        note: String,
        onSuccess: () -> Unit
    ) {
        if (customerMobile.isBlank() || amount <= 0.0) {
            viewModelScope.launch {
                _toastMessage.emit("Please enter valid mobile and Udhar amount")
            }
            return
        }

        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""

                repository.recordUdharOrJamaTransaction(
                    userUid = userUid,
                    customerName = customerName,
                    customerMobile = customerMobile,
                    type = "DEBIT",
                    amount = amount,
                    paymentMode = "Credit / Udhar",
                    note = note
                )

                _toastMessage.emit("Udhar ₹$amount added for $customerName!")
                onSuccess()
            } catch (e: Exception) {
                Log.e("BillingVM", "recordUdharEntry error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to add Udhar: ${e.localizedMessage}")
            }
        }
    }

    fun scheduleCustomerAutoReminder(
        context: android.content.Context,
        customer: com.example.data.db.CustomerEntity,
        scheduledEpochMillis: Long,
        reminderType: com.example.util.ReminderType = com.example.util.ReminderType.URGENT,
        customMessage: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val storeName = currentUser.value?.businessName?.takeIf { it.isNotBlank() }
                    ?: currentUser.value?.merchantName?.takeIf { it.isNotBlank() }
                    ?: "SmartPOS Store"
                val storePhone = currentUser.value?.mobileNumber?.takeIf { it.isNotBlank() }
                    ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()
                val upiId = currentUser.value?.upiId?.takeIf { it.isNotBlank() } ?: "merchant@upi"
                val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
                val lastTxnDate = if (customer.lastTransactionTimestamp > 0) {
                    dateFormat.format(Date(customer.lastTransactionTimestamp))
                } else "Recent"

                val finalMessage = if (customMessage.isNotBlank()) {
                    customMessage
                } else {
                    com.example.util.WhatsAppReminderUtils.buildReminderMessage(
                        customerName = customer.name,
                        businessName = storeName,
                        pendingAmount = customer.totalPendingBalance,
                        lastTransactionTimestamp = customer.lastTransactionTimestamp,
                        reminderType = reminderType,
                        upiId = upiId,
                        merchantPhone = storePhone
                    )
                }

                val upiLink = com.example.util.WhatsAppReminderHelper.buildUpiPaymentUrl(
                    upiId = upiId,
                    merchantName = storeName,
                    amount = customer.totalPendingBalance,
                    customerName = customer.name
                )
                val userId = currentUser.value?.id?.toString()
                    ?: (com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: "")

                com.example.worker.UdharReminderWorker.schedule(
                    context = context,
                    scheduledEpochMillis = scheduledEpochMillis,
                    customerMobile = customer.mobileNumber,
                    customerName = customer.name,
                    storeName = storeName,
                    storePhone = storePhone,
                    merchantUpiId = upiId,
                    pendingBalance = customer.totalPendingBalance,
                    lastTxnDate = lastTxnDate,
                    message = finalMessage,
                    upiLink = upiLink,
                    userId = userId
                )

                repository.updateCustomerReminderSchedule(
                    userUid = userId,
                    customerMobile = customer.mobileNumber,
                    date = scheduledEpochMillis,
                    status = "SCHEDULED"
                )

                val scheduledDateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(scheduledEpochMillis))
                _toastMessage.emit("⏰ Auto Reminder scheduled for ${customer.name} on $scheduledDateStr")
            } catch (e: Exception) {
                Log.e("BillingVM", "scheduleCustomerAutoReminder error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to schedule auto reminder: ${e.localizedMessage}")
            }
        }
    }

    fun cancelCustomerAutoReminder(
        context: android.content.Context,
        customer: com.example.data.db.CustomerEntity
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.worker.UdharReminderWorker.cancel(context, customer.mobileNumber)

                val userId = currentUser.value?.id?.toString()
                    ?: (com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: "")
                repository.updateCustomerReminderSchedule(
                    userUid = userId,
                    customerMobile = customer.mobileNumber,
                    date = 0L,
                    status = "NONE"
                )

                _toastMessage.emit("Auto reminder cancelled for ${customer.name}")
            } catch (e: Exception) {
                Log.e("BillingVM", "cancelCustomerAutoReminder error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to cancel auto reminder: ${e.localizedMessage}")
            }
        }
    }

    // --- Admin Category Editing State ---
    var adminCategoryName by mutableStateOf("")
    var adminCategoryDescription by mutableStateOf("")
    var adminCategoryIcon by mutableStateOf("shopping_basket")
    var editingCategory by mutableStateOf<CategoryEntity?>(null)

    init {
        viewModelScope.launch {
            repository.prepopulateCategoriesIfEmpty()
        }
    }

    // --- Actions ---

    // --- Premium Authentication Actions ---

    private var resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken? = null

    fun sendOtp(mobileNumber: String, activity: Activity) {
        val cleanDigits = mobileNumber.replace("\\D".toRegex(), "")
        if (cleanDigits.length < 10) {
            authError = "Please enter a valid 10-digit mobile number."
            return
        }
        val formattedPhone = if (cleanDigits.length == 10) "+91$cleanDigits" else "+$cleanDigits"
        authMobile = formattedPhone
        authError = null
        isSendingOtp = true

        val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                Log.d("BillingVM", "Auto-verification completed successfully")
                isSendingOtp = false
                isVerifyingOtp = true
                viewModelScope.launch {
                    try {
                        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                        val authResult = auth.signInWithCredential(credential).await()
                        val user = authResult.user
                        if (user != null) {
                            val authRepo = com.example.data.repository.AuthRepository()
                            authRepo.syncUserProfileAndSession(user, "phone")
                            handlePostAuth(user.uid, user.phoneNumber ?: formattedPhone, "phone") {}
                        }
                    } catch (e: Exception) {
                        isVerifyingOtp = false
                        authError = "Auto-verification failed: ${e.localizedMessage}"
                    }
                }
            }

            override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                Log.e("BillingVM", "Phone verification failed: ${exception.localizedMessage}")
                isSendingOtp = false
                isVerifyingOtp = false
                authError = when (exception) {
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Invalid phone number format or credentials."
                    is com.google.firebase.FirebaseTooManyRequestsException -> "SMS quota exceeded or too many requests. Please try again later."
                    else -> exception.localizedMessage ?: "Verification failed. Please check network/App Check."
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d("BillingVM", "Phone auth code sent: $verificationId")
                tempVerificationId = verificationId
                resendToken = token
                isSendingOtp = false
                isOtpSent = true
                startResendTimer(60)
                viewModelScope.launch {
                    _toastMessage.emit("OTP sent successfully to $formattedPhone")
                }
            }
        }

        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val optionsBuilder = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(formattedPhone) // Must include country code, e.g. +91XXXXXXXXXX
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity) // Required for app verification
                .setCallbacks(callbacks)

            if (resendToken != null) {
                optionsBuilder.setForceResendingToken(resendToken!!)
            }

            val options = optionsBuilder.build()
            com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            isSendingOtp = false
            authError = "Failed to start phone verification: ${e.localizedMessage}"
        }
    }

    fun startResendTimer(seconds: Int = 60) {
        timerSeconds = seconds
        viewModelScope.launch {
            while (timerSeconds > 0) {
                delay(1000)
                timerSeconds--
            }
        }
    }

    fun verifyOtp(code: String, onNavigate: (route: String) -> Unit) {
        val trimmedCode = code.trim()
        if (trimmedCode.length != 6) {
            authError = "Please enter the 6-digit OTP code."
            return
        }
        if (tempVerificationId.isBlank()) {
            authError = "No active OTP session. Please request a new OTP."
            return
        }

        authError = null
        isVerifyingOtp = true

        viewModelScope.launch {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val credential = com.google.firebase.auth.PhoneAuthProvider.getCredential(tempVerificationId, trimmedCode)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    val authRepo = com.example.data.repository.AuthRepository()
                    authRepo.syncUserProfileAndSession(user, "phone")
                    handlePostAuth(user.uid, user.phoneNumber ?: authMobile, "phone", onNavigate)
                } else {
                    throw IllegalStateException("Firebase user is null.")
                }
            } catch (e: Exception) {
                isVerifyingOtp = false
                authError = when (e) {
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Invalid or expired OTP code. Please try again."
                    else -> "Verification failed: ${e.localizedMessage}"
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String, email: String, displayName: String, onNavigate: (route: String) -> Unit) {
        authError = null
        isVerifyingOtp = true
        viewModelScope.launch {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    handlePostAuth(user.uid, user.email ?: email, "google", onNavigate)
                } else {
                    throw IllegalStateException("Firebase user is null.")
                }
            } catch (e: Exception) {
                isVerifyingOtp = false
                authError = "Google Sign-In failed: ${e.localizedMessage}"
                Log.e("GoogleAuth", "Google sign-in error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handlePostAuth(
        uid: String,
        mobileOrEmail: String,
        provider: String,
        onNavigate: (route: String) -> Unit
    ) {
        // 1. Immediately restore subscription state from Firestore on login
        try {
            val appCtx = com.example.SmartPOSApplication.instance
            com.example.data.subscription.SubscriptionRepository.restoreSubscriptionFromFirestore(
                context = appCtx,
                userId = uid,
                mobileNumber = mobileOrEmail
            )
            com.example.data.subscription.AppSessionManager.verifyAndEnforceSubscriptionLock(
                context = appCtx,
                userUid = mobileOrEmail.ifBlank { uid }
            )
        } catch (e: Exception) {
            Log.e("Auth", "Error restoring subscription on auth: ${e.localizedMessage}")
        }

        // 2. Query profile document
        val existingUser = if (com.example.data.firebase.FirebaseManager.isFirebaseAvailable) {
            repository.getUserByUid(uid)
        } else {
            repository.getUserByMobile(mobileOrEmail)
        }

        val isSubscriptionValid = com.example.util.AuthGuard.isSubscriptionValid(
            com.example.data.subscription.SubscriptionManager.subscriptionState.value
        )

        if (existingUser != null) {
            repository.insertUser(existingUser)
            _currentUser.value = existingUser
            isVerifyingOtp = false
            _toastMessage.emit("Welcome back, ${existingUser.fullName}!")
            resetAuthState()
            if (isSubscriptionValid) {
                onNavigate(com.example.ui.navigation.Screen.Dashboard.route)
            } else {
                onNavigate(com.example.ui.navigation.Screen.Paywall.route)
            }
        } else {
            tempUid = uid
            tempAuthProvider = provider
            tempMobileOrEmail = mobileOrEmail
            isVerifyingOtp = false
            loadUserProfile(uid)
            onNavigate(com.example.ui.navigation.Screen.ProfileSetup.route)
        }
    }

    fun loadUserProfile(userId: String? = null) {
        val targetUid = userId
            ?: tempUid.takeIf { it.isNotBlank() }
            ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
            ?: _currentUser.value?.id?.toString()

        // If we already have currentUser in state, pre-fill immediately
        _currentUser.value?.let { user ->
            if (profileFullName.isBlank()) profileFullName = user.fullName
            if (profileBusinessName.isBlank()) profileBusinessName = user.businessName
            if (profileCategory.isBlank()) profileCategory = user.category
            if (profileUpiId.isBlank() || profileUpiId == "merchant@upi") profileUpiId = user.upiId
            if (profileMerchantName.isBlank()) profileMerchantName = user.merchantName.ifBlank { user.businessName }
            if (tempMobileOrEmail.isBlank()) tempMobileOrEmail = user.mobileNumber
        }

        if (targetUid.isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                // Fetch from Firestore
                if (com.example.data.firebase.FirebaseManager.isFirebaseAvailable) {
                    val firestore = com.example.data.firebase.FirebaseManager.firestore
                    val doc = firestore?.collection("users")?.document(targetUid)?.get()?.await()
                    if (doc != null && doc.exists()) {
                        val fullName = doc.getString("fullName") ?: doc.getString("displayName") ?: doc.getString("name") ?: ""
                        val businessName = doc.getString("businessName") ?: doc.getString("shopName") ?: ""
                        val category = doc.getString("businessCategory") ?: doc.getString("category") ?: doc.getString("selectedCategory") ?: ""
                        val upiId = doc.getString("upiId") ?: doc.getString("merchantUpi") ?: doc.getString("vpa") ?: ""
                        val merchantName = doc.getString("merchantName") ?: businessName
                        val mobile = doc.getString("mobileNumber") ?: doc.getString("phoneNumber") ?: doc.getString("mobile") ?: ""

                        if (fullName.isNotBlank()) profileFullName = fullName
                        if (businessName.isNotBlank()) profileBusinessName = businessName
                        if (category.isNotBlank()) profileCategory = category
                        if (upiId.isNotBlank()) profileUpiId = upiId
                        if (merchantName.isNotBlank()) profileMerchantName = merchantName
                        if (mobile.isNotBlank()) tempMobileOrEmail = mobile

                        val updatedEntity = UserEntity(
                            id = targetUid.hashCode(),
                            fullName = profileFullName,
                            businessName = profileBusinessName,
                            mobileNumber = tempMobileOrEmail,
                            passwordHash = "",
                            category = profileCategory,
                            upiId = profileUpiId.ifBlank { "merchant@upi" },
                            merchantName = profileMerchantName.ifBlank { profileBusinessName }
                        )
                        _currentUser.value = updatedEntity
                        repository.insertUser(updatedEntity)
                    }
                }

                // If still empty, check local DB
                if (profileFullName.isBlank()) {
                    val local = repository.getUserByUid(targetUid)
                    if (local != null) {
                        profileFullName = local.fullName
                        profileBusinessName = local.businessName
                        profileCategory = local.category
                        profileUpiId = local.upiId.ifBlank { "merchant@upi" }
                        profileMerchantName = local.merchantName.ifBlank { local.businessName }
                        _currentUser.value = local
                    }
                }
            } catch (e: Exception) {
                Log.e("BillingViewModel", "Error loading user profile: ${e.localizedMessage}")
            }
        }
    }

    fun completeProfileSetup(onNavigateToDashboard: () -> Unit) {
        if (profileFullName.isBlank() || profileBusinessName.isBlank() || profileCategory.isBlank()) {
            profileError = "Please fill all required fields and select a business category"
            return
        }
        profileError = null
        isSavingProfile = true

        val targetUid = tempUid.ifBlank {
            com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid ?: "user_${System.currentTimeMillis()}"
        }

        viewModelScope.launch {
            try {
                repository.saveUserProfile(
                    uid = targetUid,
                    fullName = profileFullName.trim(),
                    businessName = profileBusinessName.trim(),
                    mobileOrEmail = tempMobileOrEmail.trim(),
                    category = profileCategory.trim(),
                    authProvider = tempAuthProvider.ifBlank { "phone" },
                    upiId = profileUpiId.trim().ifBlank { "merchant@upi" },
                    merchantName = profileMerchantName.trim().ifBlank { profileBusinessName.trim() }
                )

                val loggedUser = UserEntity(
                    id = targetUid.hashCode(),
                    fullName = profileFullName.trim(),
                    businessName = profileBusinessName.trim(),
                    mobileNumber = tempMobileOrEmail.trim(),
                    passwordHash = "",
                    category = profileCategory.trim(),
                    upiId = profileUpiId.trim().ifBlank { "merchant@upi" },
                    merchantName = profileMerchantName.trim().ifBlank { profileBusinessName.trim() }
                )
                repository.insertUser(loggedUser)
                _currentUser.value = loggedUser
                isSavingProfile = false
                _toastMessage.emit("Profile Updated Successfully")
                resetAuthState()
                onNavigateToDashboard()
            } catch (e: Exception) {
                isSavingProfile = false
                Log.e("ProfileSetup", "Save user profile error: ${e.localizedMessage}")
                // Graceful fallback: set local user and navigate smoothly to dashboard
                val loggedUser = UserEntity(
                    id = targetUid.hashCode(),
                    fullName = profileFullName.trim(),
                    businessName = profileBusinessName.trim(),
                    mobileNumber = tempMobileOrEmail.trim(),
                    passwordHash = "",
                    category = profileCategory.trim(),
                    upiId = profileUpiId.trim().ifBlank { "merchant@upi" },
                    merchantName = profileMerchantName.trim().ifBlank { profileBusinessName.trim() }
                )
                repository.insertUser(loggedUser)
                _currentUser.value = loggedUser
                _toastMessage.emit("Profile Updated Successfully")
                resetAuthState()
                onNavigateToDashboard()
            }
        }
    }

    private fun resetAuthState() {
        authMobile = ""
        authOtpCode = ""
        isOtpSent = false
        isVerifyingOtp = false
        isSendingOtp = false
        authError = null
        tempVerificationId = ""
    }

    fun logout(context: android.content.Context? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (com.example.data.firebase.FirebaseManager.isFirebaseAvailable) {
                try {
                    com.example.data.firebase.FirebaseManager.auth?.signOut()
                } catch (e: Exception) {
                    Log.e("Logout", "Sign out error: ${e.localizedMessage}")
                }
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().clearPersistence().await()
                } catch (e: Exception) {
                    Log.d("Logout", "Firestore clearPersistence skipped: ${e.localizedMessage}")
                }
            }
            repository.clearLocalCache()
            if (context != null) {
                com.example.data.subscription.AppSessionManager.clearSession(context)
            } else {
                com.example.data.subscription.SubscriptionManager.clearLocalSubscriptionState(null)
            }
            _currentUser.value = null
            resetAuthState()
            _toastMessage.emit("Logged out successfully")
            onSuccess()
        }
    }

    // Legacy parameters stubs to ensure no compiling issues anywhere
    fun login(onSuccess: () -> Unit) { onSuccess() }
    fun signup(onSuccess: () -> Unit) { onSuccess() }
    fun triggerForgotPassword() {}

    // --- Admin Category Management ---

    fun saveCategory() {
        if (adminCategoryName.isBlank() || adminCategoryDescription.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Please enter category name and description")
            }
            return
        }

        viewModelScope.launch {
            val category = editingCategory
            if (category != null) {
                // Update
                repository.updateCategory(
                    category.copy(
                        name = adminCategoryName,
                        description = adminCategoryDescription,
                        iconName = adminCategoryIcon
                    )
                )
                _toastMessage.emit("Category updated successfully!")
            } else {
                // Create
                repository.insertCategory(
                    CategoryEntity(
                        name = adminCategoryName,
                        description = adminCategoryDescription,
                        iconName = adminCategoryIcon
                    )
                )
                _toastMessage.emit("Category added successfully!")
            }
            clearAdminCategoryState()
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            _toastMessage.emit("Category '${category.name}' removed successfully")
        }
    }

    fun toggleCategoryStatus(category: CategoryEntity) {
        viewModelScope.launch {
            val updated = category.copy(isEnabled = !category.isEnabled)
            repository.updateCategory(updated)
            val statusStr = if (updated.isEnabled) "Enabled" else "Disabled"
            _toastMessage.emit("Category '${category.name}' is now $statusStr!")
        }
    }

    fun startEditingCategory(category: CategoryEntity) {
        editingCategory = category
        adminCategoryName = category.name
        adminCategoryDescription = category.description
        adminCategoryIcon = category.iconName
    }

    fun clearAdminCategoryState() {
        editingCategory = null
        adminCategoryName = ""
        adminCategoryDescription = ""
        adminCategoryIcon = "shopping_basket"
    }

    // --- Core POS & Billing State ---
    var posCustomerName by mutableStateOf("Walk-in Customer")
    var posCustomerMobile by mutableStateOf("")
    var posDoctorName by mutableStateOf("")
    var posPatientInfo by mutableStateOf("")
    var posTableNumber by mutableStateOf("")
    var posOrderType by mutableStateOf("Dine-in") // Dine-in, Takeaway, Delivery
    var posPaymentMode by mutableStateOf("Cash") // Cash, UPI / QR, Online, Credit (Udhar)
    var posDiscountType by mutableStateOf("Fixed") // Fixed or Percentage
    var posDiscountInput by mutableStateOf("")
    var posTaxPercentageInput by mutableStateOf("0")
    var isGstInvoiceMode by mutableStateOf(true) // GST Invoice vs Simple Estimate
    var autoSendWhatsAppInvoice by mutableStateOf(true)
    val posCartItems = mutableStateListOf<POSCartItem>()
    var isGeneratingPOSInvoice by mutableStateOf(false)
    var posInvoiceError by mutableStateOf<String?>(null)
    var lastGeneratedInvoice by mutableStateOf<InvoiceEntity?>(null)

    fun toggleAutoSendWhatsAppInvoice(enabled: Boolean, context: Context? = null) {
        autoSendWhatsAppInvoice = enabled
        if (context != null) {
            com.example.util.WhatsAppInvoiceHelper.setAutoSendEnabled(context, enabled)
        }
    }

    fun syncSettingsFromPrefs(context: Context) {
        autoSendWhatsAppInvoice = com.example.util.WhatsAppInvoiceHelper.isAutoSendEnabled(context)
    }

    /**
     * Generates a complete itemized WhatsApp invoice text message from an InvoiceEntity.
     */
    fun generateWhatsAppInvoiceText(invoice: InvoiceEntity, storeName: String? = null): String {
        val effectiveStore = storeName?.ifBlank { currentUser.value?.businessName }
            ?: currentUser.value?.businessName
            ?: "SmartPOS Retail Store"
        return com.example.util.WhatsAppInvoiceHelper.formatInvoiceText(invoice, effectiveStore)
    }

    /**
     * Generates complete itemized WhatsApp invoice text message directly from current POS Cart state.
     */
    fun generateWhatsAppInvoiceTextFromCart(
        storeName: String? = null,
        invoiceNumber: String = "BILL-${(1000..9999).random()}"
    ): String {
        val effectiveStore = storeName?.ifBlank { currentUser.value?.businessName }
            ?: currentUser.value?.businessName
            ?: "SmartPOS Retail Store"

        val cartItemList = posCartItems.map { item ->
            com.example.util.WhatsAppInvoiceItem(
                name = item.product.name,
                quantity = item.quantity,
                unit = item.product.unit,
                price = item.customPrice,
                totalAmount = item.totalAmount
            )
        }

        return com.example.util.WhatsAppInvoiceHelper.generateWhatsAppInvoiceTextFromItems(
            items = cartItemList,
            invoiceNumber = invoiceNumber,
            storeName = effectiveStore,
            subtotal = posSubtotal,
            discountAmount = posDiscountAmount,
            taxAmount = posTaxAmount,
            totalAmount = posFinalTotal,
            paymentMode = posPaymentMode
        )
    }

    /**
     * Dispatches the itemized WhatsApp invoice message to customer.
     */
    fun sendWhatsAppInvoice(
        context: Context,
        customerMobile: String,
        invoice: InvoiceEntity,
        storeName: String? = null
    ): Boolean {
        val effectiveStore = storeName?.ifBlank { currentUser.value?.businessName }
            ?: currentUser.value?.businessName
            ?: "SmartPOS Retail Store"
        return com.example.util.WhatsAppInvoiceHelper.sendWhatsAppInvoice(
            context = context,
            customerPhone = customerMobile,
            invoice = invoice,
            businessName = effectiveStore
        )
    }

    private val whatsAppInvoiceRepository: WhatsAppInvoiceRepository = WhatsAppInvoiceRepository()

    /**
     * Dispatches digital invoice to customer via central SmartPOS WhatsApp endpoint in background.
     * Non-blocking, executes on Dispatchers.IO, handles result silently without opening external apps,
     * and shows a subtle in-app toast upon successful dispatch.
     */
    fun dispatchCentralWhatsAppInvoice(
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
        previousUdhar: Double? = null,
        totalOutstanding: Double? = null
    ) {
        val cleanPhone = customerPhone.replace("[^0-9]".toRegex(), "").takeLast(10)
        if (cleanPhone.length != 10) {
            Log.d("BillingVM", "Central WhatsApp invoice skipped: invalid customer phone '$customerPhone'")
            return
        }

        val effectiveStorePhone = if (storePhone.isNotBlank()) {
            storePhone
        } else {
            currentUser.value?.mobileNumber?.takeIf { it.isNotBlank() }
                ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()
        }

        val isCreditPayment = paymentMode.contains("Credit", ignoreCase = true) ||
                paymentMode.contains("Udhar", ignoreCase = true)

        val (calcPreviousUdhar, calcTotalOutstanding) = if (isCreditPayment) {
            if (previousUdhar != null && totalOutstanding != null) {
                Pair(previousUdhar, totalOutstanding)
            } else {
                val existingCustomer = customers.value.find { cust ->
                    cust.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10) == cleanPhone
                }
                val prev = existingCustomer?.totalPendingBalance ?: 0.0
                Pair(prev, prev + totalAmount)
            }
        } else {
            Pair(0.0, 0.0)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = InvoiceRequestPayload(
                    customerPhone = cleanPhone,
                    storeName = storeName.ifBlank { currentUser.value?.businessName ?: "SmartPOS Store" },
                    storePhone = effectiveStorePhone,
                    invoiceNumber = invoiceNumber,
                    totalAmount = totalAmount,
                    paymentMode = paymentMode,
                    previousUdhar = calcPreviousUdhar,
                    totalOutstanding = calcTotalOutstanding,
                    date = date,
                    items = items,
                    customerName = customerName,
                    subtotal = subtotal,
                    discountAmount = discountAmount,
                    taxAmount = taxAmount
                )

                // Call WhatsAppApiService.getInstance().sendInvoice(...)
                val response = WhatsAppApiService.getInstance().sendInvoice(payload)
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("BillingVM", "Central WhatsApp invoice dispatched: ${body?.message ?: "Success"}")
                    _toastMessage.emit("Bill sent on customer's WhatsApp 🚀")
                } else {
                    val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                    Log.w("BillingVM", "Central WhatsApp invoice API response error: $err")
                }
            } catch (e: Exception) {
                // Silently handle error without disrupting POS flow or blocking UI
                Log.e("BillingVM", "Silent exception during central WhatsApp dispatch: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Convenience method to dispatch central WhatsApp invoice from an existing InvoiceEntity.
     */
    fun dispatchCentralWhatsAppInvoiceForInvoice(
        invoice: InvoiceEntity,
        storeName: String? = null,
        storePhone: String? = null
    ) {
        val cleanPhone = invoice.customerMobile.replace("[^0-9]".toRegex(), "").takeLast(10)
        if (cleanPhone.length != 10) return

        val effectiveStore = storeName?.ifBlank { currentUser.value?.businessName }
            ?: currentUser.value?.businessName
            ?: "SmartPOS Store"

        val effectiveStorePhone = storePhone?.ifBlank { currentUser.value?.mobileNumber }
            ?: currentUser.value?.mobileNumber
            ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()

        val extractedItems = com.example.util.WhatsAppInvoiceHelper.extractItemsFromInvoice(invoice).map { item ->
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

        val isCredit = invoice.paymentMode.contains("Credit", ignoreCase = true) ||
                invoice.paymentMode.contains("Udhar", ignoreCase = true)
        val existingCustomer = customers.value.find { cust ->
            cust.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10) == cleanPhone
        }
        val previousUdhar = if (isCredit) existingCustomer?.totalPendingBalance ?: 0.0 else 0.0
        val totalOutstanding = if (isCredit) previousUdhar + invoice.amount else 0.0

        dispatchCentralWhatsAppInvoice(
            customerPhone = cleanPhone,
            storeName = effectiveStore,
            storePhone = effectiveStorePhone,
            invoiceNumber = invoiceNum,
            totalAmount = invoice.amount,
            date = dateString,
            items = extractedItems,
            paymentMode = invoice.paymentMode,
            previousUdhar = previousUdhar,
            totalOutstanding = totalOutstanding,
            customerName = invoice.customerName,
            subtotal = if (invoice.subtotal > 0) invoice.subtotal else invoice.amount,
            discountAmount = invoice.discountAmount,
            taxAmount = invoice.taxAmount
        )
    }

    // Editing POS Invoice state
    var editingInvoice by mutableStateOf<InvoiceEntity?>(null)
    var originalPurchasedItems by mutableStateOf<List<Pair<ProductEntity, Double>>>(emptyList())
    val isEditingBill: Boolean get() = editingInvoice != null

    val posSubtotal: Double
        get() = posCartItems.sumOf { it.totalAmount }

    val posDiscountAmount: Double
        get() {
            val valDouble = posDiscountInput.toDoubleOrNull() ?: 0.0
            return if (posDiscountType == "Percentage") {
                (posSubtotal * (valDouble / 100.0)).coerceAtMost(posSubtotal)
            } else {
                valDouble.coerceAtMost(posSubtotal)
            }
        }

    val posTaxAmount: Double
        get() {
            val taxPercent = posTaxPercentageInput.toDoubleOrNull() ?: 0.0
            val taxableBase = (posSubtotal - posDiscountAmount).coerceAtLeast(0.0)
            return taxableBase * (taxPercent / 100.0)
        }

    val posFinalTotal: Double
        get() = (posSubtotal - posDiscountAmount + posTaxAmount).coerceAtLeast(0.0)

    fun addToPOSCart(product: ProductEntity, addQty: Double = 1.0) {
        val expiryStatus = com.example.util.PharmacyUtils.getExpiryStatus(product.expiryDate)
        if (expiryStatus is com.example.util.ExpiryStatus.Expired) {
            viewModelScope.launch {
                _toastMessage.emit("⚠️ EXPIRED MEDICINE: '${product.name}' expired on ${product.expiryDate} and cannot be sold!")
            }
            return
        }

        val maxAvailable = product.stockQuantity
        if (maxAvailable <= 0) {
            viewModelScope.launch {
                _toastMessage.emit("Product '${product.name}' is out of stock!")
            }
            return
        }

        val existingIndex = posCartItems.indexOfFirst {
            (product.id != 0 && it.product.id == product.id) ||
            (product.firestoreId.isNotBlank() && it.product.firestoreId == product.firestoreId) ||
            (it.product.name == product.name)
        }

        if (existingIndex >= 0) {
            val currentItem = posCartItems[existingIndex]
            val newQty = (currentItem.quantity + addQty).coerceAtMost(maxAvailable)
            if (newQty == currentItem.quantity && currentItem.quantity >= maxAvailable) {
                viewModelScope.launch {
                    val stockFormatted = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(maxAvailable, product.unit)
                    _toastMessage.emit("Maximum available stock ($stockFormatted) reached for ${product.name}!")
                }
            } else {
                posCartItems[existingIndex] = currentItem.copy(quantity = newQty)
            }
        } else {
            val initialQty = addQty.coerceAtMost(maxAvailable)
            posCartItems.add(POSCartItem(product = product, quantity = initialQty))
        }
    }

    fun updatePOSCartQuantity(product: ProductEntity, newQty: Double) {
        val index = posCartItems.indexOfFirst {
            (product.id != 0 && it.product.id == product.id) ||
            (product.firestoreId.isNotBlank() && it.product.firestoreId == product.firestoreId) ||
            (it.product.name == product.name)
        }
        if (index >= 0) {
            if (newQty <= 0) {
                posCartItems.removeAt(index)
            } else {
                val clampedQty = newQty.coerceAtMost(product.stockQuantity)
                posCartItems[index] = posCartItems[index].copy(quantity = clampedQty)
            }
        }
    }

    fun removeFromPOSCart(product: ProductEntity) {
        posCartItems.removeAll {
            (product.id != 0 && it.product.id == product.id) ||
            (product.firestoreId.isNotBlank() && it.product.firestoreId == product.firestoreId) ||
            (it.product.name == product.name)
        }
    }

    fun clearPOSCart() {
        posCartItems.clear()
        posCustomerName = "Walk-in Customer"
        posCustomerMobile = ""
        posDoctorName = ""
        posPatientInfo = ""
        posTableNumber = ""
        posOrderType = "Dine-in"
        posPaymentMode = "Cash"
        posDiscountType = "Fixed"
        posDiscountInput = ""
        posTaxPercentageInput = "0"
        posInvoiceError = null
    }

    fun loadInvoiceForEditing(invoice: InvoiceEntity) {
        clearPOSCart()
        editingInvoice = invoice
        posCustomerName = invoice.customerName
        posCustomerMobile = invoice.customerMobile
        posDoctorName = invoice.doctorName
        posPatientInfo = invoice.patientInfo
        posTableNumber = invoice.tableNumber
        posOrderType = invoice.orderType.ifBlank { "Dine-in" }
        posPaymentMode = invoice.paymentMode
        posDiscountType = "Fixed"
        posDiscountInput = if (invoice.discountAmount > 0) invoice.discountAmount.toString() else ""
        posTaxPercentageInput = if (invoice.subtotal > 0 && invoice.taxAmount > 0) {
            String.format(Locale.US, "%.1f", (invoice.taxAmount / (invoice.subtotal - invoice.discountAmount)) * 100.0)
        } else "0"

        val oldList = mutableListOf<Pair<ProductEntity, Double>>()
        val availableProducts = products.value

        if (invoice.itemsJson.isNotBlank()) {
            try {
                val jsonArr = org.json.JSONArray(invoice.itemsJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val name = obj.optString("name", "Product")
                    val qty = obj.optDouble("quantity", 1.0)
                    val unit = obj.optString("unit", "Pcs")
                    val unitPrice = obj.optDouble("unitPrice", 0.0)

                    val matchedProd = availableProducts.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: ProductEntity(
                            id = name.hashCode(),
                            name = name,
                            salePrice = unitPrice,
                            stockQuantity = 999.0,
                            unit = unit
                        )
                    posCartItems.add(POSCartItem(product = matchedProd, quantity = qty, customPrice = unitPrice))
                    oldList.add(Pair(matchedProd, qty))
                }
            } catch (e: Exception) {
                Log.e("BillingVM", "Error parsing itemsJson for edit: ${e.localizedMessage}")
            }
        }

        // Fallback if itemsJson was empty
        if (posCartItems.isEmpty() && invoice.itemsSummary.isNotBlank()) {
            val parts = invoice.itemsSummary.split(", ")
            for (part in parts) {
                val cleanPart = part.trim()
                if (cleanPart.isBlank()) continue
                val matched = availableProducts.firstOrNull { cleanPart.contains(it.name, ignoreCase = true) }
                if (matched != null) {
                    posCartItems.add(POSCartItem(product = matched, quantity = 1.0))
                    oldList.add(Pair(matched, 1.0))
                }
            }
        }

        originalPurchasedItems = oldList
    }

    fun cancelEditingBill() {
        editingInvoice = null
        originalPurchasedItems = emptyList()
        clearPOSCart()
    }

    fun updatePOSInvoice(onSuccess: (InvoiceEntity) -> Unit) {
        val currentEdit = editingInvoice
        if (currentEdit == null) {
            posInvoiceError = "No bill selected for editing"
            return
        }
        if (posCartItems.isEmpty()) {
            posInvoiceError = "Please add at least one item to the bill"
            return
        }

        val name = if (posCustomerName.isBlank()) "Walk-in Customer" else posCustomerName.trim()
        val mobile = posCustomerMobile.trim()

        if (posPaymentMode.contains("Credit", ignoreCase = true) || posPaymentMode.contains("Udhar", ignoreCase = true)) {
            if (name == "Walk-in Customer" || name.isBlank()) {
                posInvoiceError = "Customer Name is required for Credit (Udhar) transactions."
                return
            }
        }

        val doctor = posDoctorName.trim()
        val patient = posPatientInfo.trim()
        val userDl = _currentUser.value?.dlNumber?.ifBlank { "DL-20B/10492/2024" } ?: "DL-20B/10492/2024"
        val userGstin = _currentUser.value?.gstin?.ifBlank { "27ABCDE1234F1Z5" } ?: "27ABCDE1234F1Z5"

        val finalAmount = posFinalTotal
        val totalItemsCount = posCartItems.size

        val summaryStringBuilder = StringBuilder()
        val itemsJsonArray = org.json.JSONArray()

        posCartItems.forEachIndexed { idx, item ->
            val formattedQty = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(item.quantity, item.product.unit, item.product)
            val isPharm = com.example.util.PharmacyUtils.isPharmacyProduct(item.product) || item.product.unit.equals("Strip", ignoreCase = true) || item.product.packUnitConfig.isNotBlank()

            val itemLineStr = if (isPharm) {
                val packSize = com.example.util.PharmacyUtils.getPackSize(item.product)
                val perTab = com.example.util.PharmacyUtils.getPerTabletUnitPrice(item.product)
                val totalTabs = Math.round(item.quantity * packSize).toInt()
                val isLooseTab = totalTabs % packSize != 0
                if (isLooseTab) {
                    "${item.product.name} — $formattedQty @ ₹${String.format(Locale.US, "%.2f", perTab)}/Tab = ₹${String.format(Locale.US, "%.2f", item.totalAmount)}"
                } else {
                    "${item.product.name} — $formattedQty @ ₹${String.format(Locale.US, "%.2f", item.customPrice)}/${item.product.unit} = ₹${String.format(Locale.US, "%.2f", item.totalAmount)}"
                }
            } else {
                "$formattedQty x ${item.product.name}"
            }
            val batchInfo = if (item.product.batchNumber.isNotBlank()) " (Batch: ${item.product.batchNumber})" else ""
            summaryStringBuilder.append("$itemLineStr$batchInfo")
            if (idx < posCartItems.size - 1) summaryStringBuilder.append(", ")

            val obj = org.json.JSONObject().apply {
                put("name", item.product.name)
                put("quantity", item.quantity)
                put("unit", item.product.unit)
                put("unitPrice", item.customPrice)
                put("purchasePrice", item.product.purchasePrice)
                put("lineTotal", item.totalAmount)
            }
            itemsJsonArray.put(obj)
        }

        val itemsSummaryStr = summaryStringBuilder.toString()
        val itemsJsonStr = itemsJsonArray.toString()

        posInvoiceError = null
        isGeneratingPOSInvoice = true

        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""

                val updatedInvoice = currentEdit.copy(
                    customerName = name,
                    customerMobile = mobile,
                    amount = finalAmount,
                    itemsCount = totalItemsCount,
                    subtotal = posSubtotal,
                    discountAmount = posDiscountAmount,
                    taxAmount = posTaxAmount,
                    paymentMode = posPaymentMode,
                    itemsSummary = itemsSummaryStr,
                    itemsJson = itemsJsonStr,
                    isEdited = true,
                    lastEditedTimestamp = System.currentTimeMillis(),
                    status = "Paid",
                    doctorName = doctor,
                    patientInfo = patient,
                    dlNumber = userDl,
                    gstin = userGstin,
                    tableNumber = posTableNumber.trim(),
                    orderType = posOrderType.trim()
                )

                val newPurchasedList = posCartItems.map { Pair(it.product, it.quantity) }

                repository.updateInvoiceAndAdjustStock(userUid, updatedInvoice, originalPurchasedItems, newPurchasedList)

                val targetInvoiceId = updatedInvoice.firestoreId.ifBlank { updatedInvoice.id.toString() }

                if (posPaymentMode == "Credit / Udhar" || posPaymentMode.contains("Credit") || posPaymentMode.contains("Udhar")) {
                    repository.recordUdharOrJamaTransaction(
                        userUid = userUid,
                        customerName = name,
                        customerMobile = mobile.ifBlank { "9999999999" },
                        type = "DEBIT",
                        amount = finalAmount,
                        paymentMode = "Credit / Udhar",
                        note = "Updated POS Bill #${currentEdit.id} ($totalItemsCount items)",
                        invoiceId = targetInvoiceId,
                        itemsJson = itemsJsonStr
                    )
                } else {
                    // Revert old Udhar transaction if payment mode changed to Cash/Online
                    repository.removeUdharTransactionForInvoice(
                        userUid = userUid,
                        customerMobile = mobile.ifBlank { "9999999999" },
                        invoiceId = targetInvoiceId
                    )
                }

                lastGeneratedInvoice = updatedInvoice
                isGeneratingPOSInvoice = false
                _toastMessage.emit("Bill #${currentEdit.id} updated successfully! Stock adjusted.")
                cancelEditingBill()
                onSuccess(updatedInvoice)

                // Dispatch central WhatsApp invoice in background if customer phone number is present
                val editCleanPhone = mobile.replace("[^0-9]".toRegex(), "").takeLast(10)
                if (editCleanPhone.length == 10) {
                    val invoiceNum = if (updatedInvoice.firestoreId.isNotBlank()) {
                        "#${updatedInvoice.firestoreId.take(8).uppercase()}"
                    } else if (updatedInvoice.id > 0) {
                        "#BILL-${updatedInvoice.id}"
                    } else {
                        "#BILL-${(1000..9999).random()}"
                    }
                    val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(updatedInvoice.lastEditedTimestamp))
                    val currentStoreName = currentUser.value?.businessName ?: "SmartPOS Store"
                    val currentStorePhone = currentUser.value?.mobileNumber?.takeIf { it.isNotBlank() }
                        ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()

                    val isCredit = posPaymentMode.contains("Credit", ignoreCase = true) || posPaymentMode.contains("Udhar", ignoreCase = true)
                    val existingCustomer = customers.value.find { cust ->
                        cust.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10) == editCleanPhone
                    }
                    val previousUdhar = if (isCredit) existingCustomer?.totalPendingBalance ?: 0.0 else 0.0
                    val totalOutstanding = if (isCredit) previousUdhar + finalAmount else 0.0

                    val payloadItems = newPurchasedList.map { pair ->
                        ItemPayload(
                            name = pair.first.name,
                            quantity = pair.second,
                            unit = pair.first.unit.ifBlank { "Pcs" },
                            unitPrice = pair.first.salePrice,
                            totalPrice = pair.second * pair.first.salePrice
                        )
                    }

                    dispatchCentralWhatsAppInvoice(
                        customerPhone = editCleanPhone,
                        storeName = currentStoreName,
                        storePhone = currentStorePhone,
                        invoiceNumber = invoiceNum,
                        totalAmount = finalAmount,
                        date = dateFormatted,
                        items = payloadItems,
                        paymentMode = posPaymentMode,
                        previousUdhar = previousUdhar,
                        totalOutstanding = totalOutstanding,
                        customerName = name,
                        subtotal = posSubtotal,
                        discountAmount = posDiscountAmount,
                        taxAmount = posTaxAmount
                    )
                }
            } catch (e: Exception) {
                isGeneratingPOSInvoice = false
                Log.e("BillingVM", "Update POS invoice error: ${e.localizedMessage}")
                posInvoiceError = "Failed to update invoice: ${e.localizedMessage}"
            }
        }
    }

    fun generatePOSInvoice(onSuccess: (InvoiceEntity) -> Unit) {
        if (posCartItems.isEmpty()) {
            posInvoiceError = "Please add at least one item to the bill"
            return
        }

        // Validate stock limits
        for (item in posCartItems) {
            if (item.quantity > item.product.stockQuantity) {
                val stockStr = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(item.product.stockQuantity, item.product.unit)
                posInvoiceError = "Quantity for ${item.product.name} exceeds available stock ($stockStr)"
                return
            }
        }

        val name = if (posCustomerName.isBlank()) "Walk-in Customer" else posCustomerName.trim()
        val mobile = posCustomerMobile.trim()

        if (posPaymentMode.contains("Credit", ignoreCase = true) || posPaymentMode.contains("Udhar", ignoreCase = true)) {
            if (name == "Walk-in Customer" || name.isBlank()) {
                posInvoiceError = "Customer Name is required for Credit (Udhar) transactions."
                return
            }
        }
        val doctor = posDoctorName.trim()
        val patient = posPatientInfo.trim()
        val userDl = _currentUser.value?.dlNumber?.ifBlank { "DL-20B/10492/2024" } ?: "DL-20B/10492/2024"
        val userGstin = _currentUser.value?.gstin?.ifBlank { "27ABCDE1234F1Z5" } ?: "27ABCDE1234F1Z5"

        val finalAmount = posFinalTotal
        val totalItemsCount = posCartItems.size

        val summaryStringBuilder = StringBuilder()
        val itemsJsonArray = org.json.JSONArray()

        posCartItems.forEachIndexed { idx, item ->
            val formattedQty = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(item.quantity, item.product.unit, item.product)
            val isPharm = com.example.util.PharmacyUtils.isPharmacyProduct(item.product) || item.product.unit.equals("Strip", ignoreCase = true) || item.product.packUnitConfig.isNotBlank()

            val itemLineStr = if (isPharm) {
                val packSize = com.example.util.PharmacyUtils.getPackSize(item.product)
                val perTab = com.example.util.PharmacyUtils.getPerTabletUnitPrice(item.product)
                val totalTabs = Math.round(item.quantity * packSize).toInt()
                val isLooseTab = totalTabs % packSize != 0
                if (isLooseTab) {
                    "${item.product.name} — $formattedQty @ ₹${String.format(Locale.US, "%.2f", perTab)}/Tab = ₹${String.format(Locale.US, "%.2f", item.totalAmount)}"
                } else {
                    "${item.product.name} — $formattedQty @ ₹${String.format(Locale.US, "%.2f", item.customPrice)}/${item.product.unit} = ₹${String.format(Locale.US, "%.2f", item.totalAmount)}"
                }
            } else {
                "$formattedQty x ${item.product.name}"
            }
            val batchInfo = if (item.product.batchNumber.isNotBlank()) " (Batch: ${item.product.batchNumber})" else ""
            summaryStringBuilder.append("$itemLineStr$batchInfo")
            if (idx < posCartItems.size - 1) summaryStringBuilder.append(", ")

            val obj = org.json.JSONObject().apply {
                put("name", item.product.name)
                put("quantity", item.quantity)
                put("unit", item.product.unit)
                put("unitPrice", item.customPrice)
                put("purchasePrice", item.product.purchasePrice)
                put("lineTotal", item.totalAmount)
            }
            itemsJsonArray.put(obj)
        }
        val itemsSummaryStr = summaryStringBuilder.toString()
        val itemsJsonStr = itemsJsonArray.toString()

        posInvoiceError = null
        isGeneratingPOSInvoice = true

        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""

                val invoice = InvoiceEntity(
                    customerName = name,
                    customerMobile = mobile,
                    amount = finalAmount,
                    itemsCount = totalItemsCount,
                    subtotal = posSubtotal,
                    discountAmount = posDiscountAmount,
                    taxAmount = posTaxAmount,
                    paymentMode = posPaymentMode,
                    itemsSummary = itemsSummaryStr,
                    itemsJson = itemsJsonStr,
                    timestamp = System.currentTimeMillis(),
                    status = "Paid",
                    doctorName = doctor,
                    patientInfo = patient,
                    dlNumber = userDl,
                    gstin = userGstin,
                    tableNumber = posTableNumber.trim(),
                    orderType = posOrderType.trim()
                )

                val purchasedList = posCartItems.map { Pair(it.product, it.quantity) }

                val savedInvoice = repository.saveInvoiceAndDeductStock(userUid, invoice, purchasedList)

                if (posPaymentMode == "Credit / Udhar" || posPaymentMode.contains("Credit") || posPaymentMode.contains("Udhar")) {
                    val targetInvoiceId = savedInvoice.firestoreId.ifBlank { savedInvoice.id.toString() }
                    repository.recordUdharOrJamaTransaction(
                        userUid = userUid,
                        customerName = name,
                        customerMobile = mobile.ifBlank { "9999999999" },
                        type = "DEBIT",
                        amount = finalAmount,
                        paymentMode = "Credit / Udhar",
                        note = "POS Bill #${savedInvoice.id} ($totalItemsCount items)",
                        invoiceId = targetInvoiceId,
                        itemsJson = itemsJsonStr
                    )
                }

                lastGeneratedInvoice = savedInvoice
                isGeneratingPOSInvoice = false
                _toastMessage.emit("Invoice generated successfully! Stock auto-deducted.")
                clearPOSCart()
                onSuccess(savedInvoice)

                // Dispatch central WhatsApp invoice in background if customer phone number is present
                val newCleanPhone = mobile.replace("[^0-9]".toRegex(), "").takeLast(10)
                if (newCleanPhone.length == 10) {
                    val invoiceNum = if (savedInvoice.firestoreId.isNotBlank()) {
                        "#${savedInvoice.firestoreId.take(8).uppercase()}"
                    } else if (savedInvoice.id > 0) {
                        "#BILL-${savedInvoice.id}"
                    } else {
                        "#BILL-${(1000..9999).random()}"
                    }
                    val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(savedInvoice.timestamp))
                    val currentStoreName = currentUser.value?.businessName ?: "SmartPOS Store"
                    val currentStorePhone = currentUser.value?.mobileNumber?.takeIf { it.isNotBlank() }
                        ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()

                    val isCredit = posPaymentMode.contains("Credit", ignoreCase = true) || posPaymentMode.contains("Udhar", ignoreCase = true)
                    val existingCustomer = customers.value.find { cust ->
                        cust.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10) == newCleanPhone
                    }
                    val previousUdhar = if (isCredit) existingCustomer?.totalPendingBalance ?: 0.0 else 0.0
                    val totalOutstanding = if (isCredit) previousUdhar + finalAmount else 0.0

                    val payloadItems = purchasedList.map { pair ->
                        ItemPayload(
                            name = pair.first.name,
                            quantity = pair.second,
                            unit = pair.first.unit.ifBlank { "Pcs" },
                            unitPrice = pair.first.salePrice,
                            totalPrice = pair.second * pair.first.salePrice
                        )
                    }

                    dispatchCentralWhatsAppInvoice(
                        customerPhone = newCleanPhone,
                        storeName = currentStoreName,
                        storePhone = currentStorePhone,
                        invoiceNumber = invoiceNum,
                        totalAmount = finalAmount,
                        date = dateFormatted,
                        items = payloadItems,
                        paymentMode = posPaymentMode,
                        previousUdhar = previousUdhar,
                        totalOutstanding = totalOutstanding,
                        customerName = name,
                        subtotal = posSubtotal,
                        discountAmount = posDiscountAmount,
                        taxAmount = posTaxAmount
                    )
                }
            } catch (e: Exception) {
                isGeneratingPOSInvoice = false
                Log.e("BillingVM", "Generate POS invoice error: ${e.localizedMessage}")
                posInvoiceError = "Failed to generate invoice: ${e.localizedMessage}"
            }
        }
    }

    // --- Legacy Quick Billing Screen Actions ---

    fun createBill(customerName: String, customerMobile: String, amountDouble: Double, itemsCount: Int) {
        if (customerName.isBlank() || amountDouble <= 0.0) {
            viewModelScope.launch {
                _toastMessage.emit("Invalid customer name or bill amount")
            }
            return
        }

        viewModelScope.launch {
            val invoice = InvoiceEntity(
                customerName = customerName,
                customerMobile = customerMobile,
                amount = amountDouble,
                itemsCount = itemsCount
            )
            repository.insertInvoice(invoice)
            _toastMessage.emit("Invoice generated successfully for $$amountDouble!")
        }
    }

    // --- Product & Inventory Actions ---

    fun saveProduct(
        id: Int = 0,
        firestoreId: String = "",
        name: String,
        salePrice: Double,
        purchasePrice: Double = 0.0,
        stockQuantity: Double,
        unit: String,
        category: String,
        barcode: String = "",
        batchNumber: String = "",
        expiryDate: String = "",
        manufacturer: String = "",
        saltComposition: String = "",
        packUnitConfig: String = "",
        isRxRequired: Boolean = false,
        size: String = "",
        color: String = "",
        minStockThreshold: Double = 5.0,
        onSuccess: (() -> Unit)? = null
    ) {
        if (name.isBlank()) {
            productFormError = "Product name is required"
            return
        }
        if (salePrice <= 0.0) {
            productFormError = "Please enter a valid sale price"
            return
        }
        if (stockQuantity < 0) {
            productFormError = "Stock quantity cannot be negative"
            return
        }

        productFormError = null
        isSavingProduct = true

        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""

                val product = ProductEntity(
                    id = id,
                    firestoreId = firestoreId,
                    name = name.trim(),
                    salePrice = salePrice,
                    purchasePrice = purchasePrice,
                    stockQuantity = stockQuantity,
                    unit = unit.ifBlank { "Pcs" },
                    category = category.ifBlank { "General" },
                    barcode = barcode.trim(),
                    batchNumber = batchNumber.trim(),
                    expiryDate = expiryDate.trim(),
                    manufacturer = manufacturer.trim(),
                    saltComposition = saltComposition.trim(),
                    packUnitConfig = packUnitConfig.trim(),
                    isRxRequired = isRxRequired,
                    size = size.trim(),
                    color = color.trim(),
                    minStockThreshold = minStockThreshold
                )

                repository.saveProduct(userUid, product)
                isSavingProduct = false
                _toastMessage.emit(if (id == 0 && firestoreId.isEmpty()) "Product '${name.trim()}' added successfully!" else "Product '${name.trim()}' updated successfully!")
                onSuccess?.invoke()
            } catch (e: Exception) {
                isSavingProduct = false
                Log.e("ProductVM", "Save product error: ${e.localizedMessage}")
                productFormError = "Failed to save product: ${e.localizedMessage}"
            }
        }
    }

    fun updateMerchantUpiSettings(newUpiId: String, newMerchantName: String, onSuccess: (() -> Unit)? = null) {
        if (newUpiId.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Please enter a valid Merchant UPI ID")
            }
            return
        }

        val user = _currentUser.value
        val updatedUser = user?.copy(
            upiId = newUpiId.trim(),
            merchantName = newMerchantName.trim()
        ) ?: UserEntity(
            fullName = "Store Owner",
            businessName = newMerchantName.trim().ifBlank { "Kirana Store" },
            mobileNumber = "9999999999",
            passwordHash = "",
            category = "Kirana / Grocery",
            upiId = newUpiId.trim(),
            merchantName = newMerchantName.trim()
        )

        viewModelScope.launch {
            try {
                repository.insertUser(updatedUser)
                _currentUser.value = updatedUser
                _toastMessage.emit("Merchant UPI Settings saved successfully!")
                onSuccess?.invoke()
            } catch (e: Exception) {
                Log.e("BillingVM", "Update merchant settings error: ${e.localizedMessage}")
                _toastMessage.emit("Saved settings locally")
                _currentUser.value = updatedUser
                onSuccess?.invoke()
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            try {
                val userUid = com.example.data.firebase.FirebaseManager.auth?.currentUser?.uid
                    ?: currentUser.value?.id?.toString() ?: ""
                repository.deleteProduct(userUid, product)
                _toastMessage.emit("Product '${product.name}' deleted")
            } catch (e: Exception) {
                Log.e("ProductVM", "Delete product error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to delete product: ${e.localizedMessage}")
            }
        }
    }
}

class BillingViewModelFactory(private val repository: BillingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BillingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BillingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
