package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.UdharReminderRequestPayload
import com.example.data.api.WhatsAppApiService
import com.example.data.db.AppDatabase
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerTransactionEntity
import com.example.data.db.UserEntity
import com.example.data.firebase.FirebaseManager
import com.example.data.repository.BillingRepository
import com.example.util.ReminderType
import com.example.util.WhatsAppReminderHelper
import com.example.util.WhatsAppReminderUtils
import com.example.worker.UdharReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel managing Udhar Khata (Credit Ledger), dynamic merchant profile injection,
 * and scheduled automated payment reminders.
 */
class UdharViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BillingRepository.getInstance(application)
    private val database = AppDatabase.getDatabase(application)

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // --- Merchant Profile State ---
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    val merchantStoreName: StateFlow<String> = _currentUser.map { user ->
        user?.businessName?.takeIf { it.isNotBlank() }
            ?: user?.merchantName?.takeIf { it.isNotBlank() }
            ?: "SmartPOS Store"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SmartPOS Store")

    val merchantPhone: StateFlow<String> = _currentUser.map { user ->
        user?.mobileNumber?.takeIf { it.isNotBlank() }
            ?: FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val merchantUpiId: StateFlow<String> = _currentUser.map { user ->
        user?.upiId?.takeIf { it.isNotBlank() } ?: "merchant@upi"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "merchant@upi")

    // --- Udhar Khata State ---
    val searchQuery = MutableStateFlow("")
    val activeCustomerMobileForLedger = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> = _currentUser
        .flatMapLatest { user: UserEntity? ->
            val uid = user?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")
            repository.getCustomersStream(uid)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredCustomers: StateFlow<List<CustomerEntity>> = combine(customers, searchQuery) { customerList, query ->
        if (query.isBlank()) {
            customerList
        } else {
            val cleanQuery = query.trim().lowercase(Locale.ROOT)
            customerList.filter { customer ->
                customer.name.lowercase(Locale.ROOT).contains(cleanQuery) ||
                customer.mobileNumber.contains(cleanQuery)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCustomerTransactions: StateFlow<List<CustomerTransactionEntity>> = activeCustomerMobileForLedger
        .flatMapLatest { mobile: String ->
            val user = _currentUser.value
            val uid = user?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")
            repository.getCustomerTransactionsStream(uid, mobile)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadMerchantProfile()
    }

    private fun loadMerchantProfile() {
        viewModelScope.launch {
            try {
                val fb = FirebaseManager.auth?.currentUser
                if (fb != null) {
                    val user = repository.getUserByUid(fb.uid)
                        ?: repository.getUserByMobile(fb.phoneNumber.orEmpty())
                    if (user != null) {
                        _currentUser.value = user
                    }
                }
            } catch (e: Exception) {
                Log.d("UdharVM", "Auto user fetch: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Constructs dynamic UPI link using real merchant data.
     */
    fun getDynamicUpiLink(customer: CustomerEntity): String {
        return WhatsAppReminderHelper.buildUpiPaymentUrl(
            upiId = merchantUpiId.value,
            merchantName = merchantStoreName.value,
            amount = customer.totalPendingBalance,
            customerName = customer.name
        )
    }

    /**
     * Generates a fully dynamic reminder message using current merchant store name,
     * phone, and UPI ID.
     */
    fun buildFormattedReminderMessage(
        customer: CustomerEntity,
        reminderType: ReminderType = ReminderType.URGENT,
        transactions: List<CustomerTransactionEntity> = emptyList()
    ): String {
        return WhatsAppReminderUtils.buildReminderMessage(
            customerName = customer.name,
            businessName = merchantStoreName.value,
            pendingAmount = customer.totalPendingBalance,
            lastTransactionTimestamp = customer.lastTransactionTimestamp,
            reminderType = reminderType,
            transactions = transactions,
            upiId = merchantUpiId.value,
            merchantPhone = merchantPhone.value
        )
    }

    /**
     * Schedules automatic background dispatch of Udhar payment reminder via WorkManager.
     */
    fun scheduleAutoReminder(
        context: Context,
        customer: CustomerEntity,
        scheduledEpochMillis: Long,
        reminderType: ReminderType = ReminderType.URGENT,
        customMessage: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
                val lastTxnDate = if (customer.lastTransactionTimestamp > 0) {
                    dateFormat.format(Date(customer.lastTransactionTimestamp))
                } else "Recent"

                val finalMessage = if (customMessage.isNotBlank()) {
                    customMessage
                } else {
                    buildFormattedReminderMessage(customer, reminderType)
                }

                val upiLink = getDynamicUpiLink(customer)
                val userId = _currentUser.value?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")

                // 1. Enqueue WorkManager background task
                UdharReminderWorker.schedule(
                    context = context,
                    scheduledEpochMillis = scheduledEpochMillis,
                    customerMobile = customer.mobileNumber,
                    customerName = customer.name,
                    storeName = merchantStoreName.value,
                    storePhone = merchantPhone.value,
                    merchantUpiId = merchantUpiId.value,
                    pendingBalance = customer.totalPendingBalance,
                    lastTxnDate = lastTxnDate,
                    message = finalMessage,
                    upiLink = upiLink,
                    userId = userId
                )

                // 2. Persist scheduled date and status in Firestore & Room DB
                repository.updateCustomerReminderSchedule(
                    userUid = userId,
                    customerMobile = customer.mobileNumber,
                    date = scheduledEpochMillis,
                    status = "SCHEDULED"
                )

                val scheduledDateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(scheduledEpochMillis))
                _toastMessage.emit("⏰ Auto Reminder scheduled for ${customer.name} on $scheduledDateStr")
            } catch (e: Exception) {
                Log.e("UdharViewModel", "Error scheduling auto reminder: ${e.localizedMessage}", e)
                _toastMessage.emit("Failed to schedule auto reminder: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Cancels an existing scheduled automatic reminder.
     */
    fun cancelScheduledReminder(context: Context, customer: CustomerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                UdharReminderWorker.cancel(context, customer.mobileNumber)

                val userId = _currentUser.value?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")
                repository.updateCustomerReminderSchedule(
                    userUid = userId,
                    customerMobile = customer.mobileNumber,
                    date = 0L,
                    status = "NONE"
                )

                _toastMessage.emit("Scheduled reminder cancelled for ${customer.name}")
            } catch (e: Exception) {
                Log.e("UdharViewModel", "Error cancelling auto reminder: ${e.localizedMessage}")
                _toastMessage.emit("Failed to cancel reminder: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Directly calls central WhatsApp API server to send reminder immediately.
     */
    fun sendDirectServerUdharReminder(
        customer: CustomerEntity,
        reminderType: ReminderType = ReminderType.URGENT,
        customMessage: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanPhone = customer.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10)
            if (cleanPhone.length != 10) {
                onResult(false, "Invalid 10-digit mobile number")
                return@launch
            }

            val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
            val lastTxnDate = if (customer.lastTransactionTimestamp > 0) {
                dateFormat.format(Date(customer.lastTransactionTimestamp))
            } else "Recent"

            val message = if (customMessage.isNotBlank()) {
                customMessage
            } else {
                buildFormattedReminderMessage(customer, reminderType)
            }

            val payload = UdharReminderRequestPayload(
                customerPhone = cleanPhone,
                customerName = customer.name,
                storeName = merchantStoreName.value,
                storePhone = merchantPhone.value,
                merchantUpiId = merchantUpiId.value,
                pendingBalance = customer.totalPendingBalance,
                lastTxnDate = lastTxnDate,
                message = message,
                upiLink = getDynamicUpiLink(customer)
            )

            try {
                val response = WhatsAppApiService.getInstance().sendUdharReminder(payload)
                if (response.isSuccessful && response.body()?.success == true) {
                    val userId = _currentUser.value?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")
                    repository.updateCustomerReminderSchedule(
                        userUid = userId,
                        customerMobile = customer.mobileNumber,
                        date = System.currentTimeMillis(),
                        status = "SENT"
                    )
                    onResult(true, "Reminder sent successfully via WhatsApp!")
                } else {
                    val msg = response.body()?.message ?: "Server returned error (${response.code()})"
                    onResult(false, msg)
                }
            } catch (e: Exception) {
                Log.e("UdharViewModel", "Send server reminder error: ${e.localizedMessage}")
                onResult(false, e.localizedMessage ?: "Failed to connect to WhatsApp dispatch server")
            }
        }
    }

    /**
     * Records a Jama (Credit / Payment Received) transaction.
     */
    fun recordJamaPayment(
        customerName: String,
        customerMobile: String,
        amount: Double,
        paymentMode: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        if (customerMobile.isBlank() || amount <= 0.0) {
            viewModelScope.launch { _toastMessage.emit("Please enter valid amount and mobile") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = _currentUser.value
                val uid = user?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")
                repository.recordUdharOrJamaTransaction(
                    userUid = uid,
                    customerName = customerName.trim(),
                    customerMobile = customerMobile.trim(),
                    type = "CREDIT",
                    amount = amount,
                    paymentMode = paymentMode,
                    note = note
                )
                _toastMessage.emit("Jama (Payment Received) ₹$amount recorded for $customerName!")
                onSuccess()
            } catch (e: Exception) {
                Log.e("UdharViewModel", "recordJamaPayment error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to record Jama: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Records an Udhar (Debit / Loan Given) entry.
     */
    fun recordUdharEntry(
        customerName: String,
        customerMobile: String,
        amount: Double,
        paymentMode: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        if (customerMobile.isBlank() || amount <= 0.0) {
            viewModelScope.launch { _toastMessage.emit("Please enter valid amount and mobile") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = _currentUser.value
                val uid = user?.id?.toString() ?: (FirebaseManager.auth?.currentUser?.uid ?: "")
                repository.recordUdharOrJamaTransaction(
                    userUid = uid,
                    customerName = customerName.trim(),
                    customerMobile = customerMobile.trim(),
                    type = "DEBIT",
                    amount = amount,
                    paymentMode = paymentMode,
                    note = note
                )
                _toastMessage.emit("Udhar ₹$amount added for $customerName!")
                onSuccess()
            } catch (e: Exception) {
                Log.e("UdharViewModel", "recordUdharEntry error: ${e.localizedMessage}")
                _toastMessage.emit("Failed to add Udhar: ${e.localizedMessage}")
            }
        }
    }
}
