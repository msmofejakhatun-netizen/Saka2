package com.example.data.subscription

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

data class SubscriptionInfo(
    val isProUser: Boolean = false,
    val subscriptionTier: String = "FREE", // "FREE", "TRIAL_1_INR", "MONTHLY_79_INR", "ANNUAL_799_INR"
    val planType: String = "FREE", // "MONTHLY", "ANNUAL", "TRIAL", "FREE"
    val planName: String = "Free Plan", // "Monthly Pro Plan (₹79/mo)", "Annual Pro Plan (₹799/yr)", "Free Trial (3 Days)"
    val status: String = "FREE", // "ACTIVE", "EXPIRED", "CANCELLED", "TRIAL_ACTIVE"
    val amountPaid: Double = 0.0,
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val subscriptionExpiryDate: Long = 0L,
    val autoPayMandateStatus: String = "NONE", // "NONE", "ACTIVE", "TRIAL_ACTIVE", "CANCELLED", "FAILED", "EXPIRED"
    val autoPayMandateId: String = "",
    val gatewayProvider: String = "RAZORPAY", // "RAZORPAY", "PHONEPE", "DIRECT_UPI_MANDATE"
    val gatewaySubscriptionId: String = "",
    val settlementAccount: String = PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED,
    val trialStartDate: Long = 0L,
    val paymentMethod: String = "",
    val hasUsedTrial: Boolean = false
) {
    val effectiveExpiry: Long
        get() = when {
            expiryDate > 0L -> expiryDate
            subscriptionExpiryDate > 0L -> subscriptionExpiryDate
            trialStartDate > 0L -> trialStartDate + TimeUnit.DAYS.toMillis(3)
            else -> 0L
        }

    val daysLeft: Long
        get() {
            val exp = effectiveExpiry
            return if (exp > 0L) {
                ((exp - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
            } else 0L
        }

    val displayBadgeTitle: String
        get() = when (planType.uppercase()) {
            "MONTHLY", "MONTHLY_79_INR" -> "Monthly Pro (Active)"
            "ANNUAL", "ANNUAL_799_INR" -> "Annual Pro (Active)"
            "TRIAL", "TRIAL_1_INR", "TRIAL_3_DAYS_1_INR" -> "Free Trial (3 Days)"
            else -> when (subscriptionTier) {
                "MONTHLY_79_INR" -> "Monthly Pro (Active)"
                "ANNUAL_799_INR" -> "Annual Pro (Active)"
                "TRIAL_1_INR" -> "Free Trial (3 Days)"
                else -> if (isProUser) "Pro Active" else "Free Plan"
            }
        }

    val displayDaysText: String
        get() = "$daysLeft Days Left"
}

object SubscriptionManager {
    private const val TAG = "SubscriptionManager"
    private const val PREFS_NAME = "kirana_subscription_prefs"

    private const val KEY_IS_PRO = "is_pro_user"
    private const val KEY_TIER = "subscription_tier"
    private const val KEY_PLAN_TYPE = "plan_type"
    private const val KEY_PLAN_NAME = "plan_name"
    private const val KEY_STATUS = "subscription_status"
    private const val KEY_AMOUNT_PAID = "amount_paid"
    private const val KEY_START_DATE = "start_date"
    private const val KEY_EXPIRY_DATE = "expiry_date"
    private const val KEY_EXPIRY = "subscription_expiry"
    private const val KEY_MANDATE_STATUS = "auto_pay_mandate_status"
    private const val KEY_MANDATE_ID = "auto_pay_mandate_id"
    private const val KEY_GATEWAY_PROVIDER = "gateway_provider"
    private const val KEY_GATEWAY_SUB_ID = "gateway_subscription_id"
    private const val KEY_SETTLEMENT_ACCT = "settlement_account"
    private const val KEY_TRIAL_START = "trial_start_date"
    private const val KEY_PAYMENT_METHOD = "payment_method"
    private const val KEY_HAS_USED_TRIAL = "has_used_trial"

    // Holds the last selected plan before checkout
    var pendingPlan: PaymentGatewayHandler.SubscriptionPlan = PaymentGatewayHandler.SubscriptionPlan.MONTHLY_79_INR

    private val _subscriptionState = MutableStateFlow(SubscriptionInfo())
    val subscriptionState: StateFlow<SubscriptionInfo> = _subscriptionState.asStateFlow()

    fun init(context: Context, userUid: String = "") {
        val effectiveUid = userUid.ifBlank { FirebaseManager.auth?.currentUser?.uid ?: "" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSavedUid = prefs.getString("last_user_uid", "") ?: ""

        // Clear local state if a different user logged in
        if (effectiveUid.isNotBlank() && lastSavedUid.isNotBlank() && effectiveUid != lastSavedUid) {
            prefs.edit().clear().apply()
            _subscriptionState.value = SubscriptionInfo()
        }

        if (effectiveUid.isNotBlank()) {
            prefs.edit().putString("last_user_uid", effectiveUid).apply()
        }

        val isPro = prefs.getBoolean(KEY_IS_PRO, false)
        val tier = prefs.getString(KEY_TIER, "FREE") ?: "FREE"
        val planType = prefs.getString(KEY_PLAN_TYPE, if (tier.contains("ANNUAL")) "ANNUAL" else if (tier.contains("MONTHLY")) "MONTHLY" else if (tier.contains("TRIAL")) "TRIAL" else "FREE") ?: "FREE"
        val planName = prefs.getString(KEY_PLAN_NAME, if (planType == "ANNUAL") "Annual Pro Plan (₹799/yr)" else if (planType == "MONTHLY") "Monthly Pro Plan (₹79/mo)" else if (planType == "TRIAL") "Free Trial (3 Days)" else "Free Plan") ?: "Free Plan"
        val status = prefs.getString(KEY_STATUS, if (isPro) "ACTIVE" else "FREE") ?: "FREE"
        val amountPaid = prefs.getFloat(KEY_AMOUNT_PAID, if (planType == "ANNUAL") 799f else if (planType == "MONTHLY") 79f else 0f).toDouble()
        val startDate = prefs.getLong(KEY_START_DATE, 0L)
        val expiryDate = prefs.getLong(KEY_EXPIRY_DATE, prefs.getLong(KEY_EXPIRY, 0L))
        val mandateStatus = prefs.getString(KEY_MANDATE_STATUS, "NONE") ?: "NONE"
        val mandateId = prefs.getString(KEY_MANDATE_ID, "") ?: ""
        val provider = prefs.getString(KEY_GATEWAY_PROVIDER, "RAZORPAY") ?: "RAZORPAY"
        val subId = prefs.getString(KEY_GATEWAY_SUB_ID, "") ?: ""
        val settlement = prefs.getString(KEY_SETTLEMENT_ACCT, PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED) ?: PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED
        val trialStart = prefs.getLong(KEY_TRIAL_START, 0L)
        val method = prefs.getString(KEY_PAYMENT_METHOD, "") ?: ""
        val hasUsedTrial = prefs.getBoolean(KEY_HAS_USED_TRIAL, false) || trialStart > 0L || tier == "TRIAL_1_INR" || planType == "TRIAL" || planType == "MONTHLY" || planType == "ANNUAL"

        val now = System.currentTimeMillis()
        var validPro = isPro
        var currentMandateStatus = mandateStatus
        var currentStatus = status

        if (expiryDate > 0L && now >= expiryDate) {
            validPro = false
            currentMandateStatus = "EXPIRED"
            currentStatus = "EXPIRED"
            prefs.edit()
                .putBoolean(KEY_IS_PRO, false)
                .putString(KEY_STATUS, "EXPIRED")
                .putString(KEY_MANDATE_STATUS, "EXPIRED")
                .apply()
        } else if (mandateStatus == "FAILED" || mandateStatus == "CANCELLED" || mandateStatus == "EXPIRED") {
            validPro = expiryDate > now
            prefs.edit().putBoolean(KEY_IS_PRO, validPro).apply()
        }

        val info = SubscriptionInfo(
            isProUser = validPro,
            subscriptionTier = tier,
            planType = planType,
            planName = planName,
            status = currentStatus,
            amountPaid = amountPaid,
            startDate = startDate,
            expiryDate = expiryDate,
            subscriptionExpiryDate = expiryDate,
            autoPayMandateStatus = currentMandateStatus,
            autoPayMandateId = mandateId,
            gatewayProvider = provider,
            gatewaySubscriptionId = subId,
            settlementAccount = settlement,
            trialStartDate = trialStart,
            paymentMethod = method,
            hasUsedTrial = hasUsedTrial
        )
        _subscriptionState.value = info

        if (effectiveUid.isNotBlank()) {
            fetchRemoteSubscription(effectiveUid, context)
        }
    }

    fun clearLocalSubscriptionState(context: Context? = null) {
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing prefs: ${e.localizedMessage}")
            }
        }
        _subscriptionState.value = SubscriptionInfo()
        Log.d(TAG, "Cleared local subscription session state on logout")
    }

    fun activateTrialMandate(
        context: Context,
        paymentMethod: String,
        userUid: String,
        gatewayProvider: String = "RAZORPAY",
        subscriptionId: String = "",
        onComplete: (Boolean, String) -> Unit
    ) {
        val currentCalendar = Calendar.getInstance()
        val start = currentCalendar.timeInMillis
        currentCalendar.add(Calendar.DAY_OF_YEAR, 3) // 3-day trial period
        val expiry = currentCalendar.timeInMillis
        val mandateId = if (subscriptionId.isNotBlank()) subscriptionId else "MND-RZP-" + (100000..999999).random()

        val info = SubscriptionInfo(
            isProUser = true,
            subscriptionTier = "TRIAL_1_INR",
            planType = "TRIAL",
            planName = "Free Trial (3 Days)",
            status = "TRIAL_ACTIVE",
            amountPaid = 1.0,
            startDate = start,
            expiryDate = expiry,
            subscriptionExpiryDate = expiry,
            autoPayMandateStatus = "TRIAL_ACTIVE",
            autoPayMandateId = mandateId,
            gatewayProvider = gatewayProvider,
            gatewaySubscriptionId = mandateId,
            settlementAccount = PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED,
            trialStartDate = start,
            paymentMethod = paymentMethod,
            hasUsedTrial = true
        )

        saveLocal(context, info)
        _subscriptionState.value = info

        syncToFirebase(userUid, info)
        onComplete(true, "3-Day Trial @ ₹1 Activated Successfully via $gatewayProvider! Mandate Ref: $mandateId")
    }

    /**
     * Immediate client-side success callback handler for Razorpay / PhonePe SDK payment completion.
     * Accurately sets planType ("MONTHLY", "ANNUAL", "TRIAL"), 30 or 365 days validity, and persists to Firestore.
     */
    fun onPaymentSuccess(
        context: Context,
        userUid: String,
        razorpayPaymentId: String,
        paymentData: Any? = null,
        planType: String? = null,
        amountPaid: Double? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val effectiveUid = resolveUserUid(userUid)
        val selectedPlanType = planType ?: when (pendingPlan) {
            PaymentGatewayHandler.SubscriptionPlan.ANNUAL_799_INR -> "ANNUAL"
            PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR -> "TRIAL"
            else -> "MONTHLY"
        }

        val selectedAmount = amountPaid ?: when (selectedPlanType) {
            "ANNUAL" -> 799.0
            "TRIAL" -> 1.0
            else -> 79.0
        }

        val currentCalendar = Calendar.getInstance()
        val startDate = currentCalendar.time
        val startMillis = startDate.time

        val validityDays = when (selectedPlanType) {
            "ANNUAL" -> 365
            "TRIAL" -> 3
            else -> 30
        }
        currentCalendar.add(Calendar.DAY_OF_YEAR, validityDays)
        val expiryDate = currentCalendar.time
        val expiryMillis = expiryDate.time

        val mandateId = razorpayPaymentId.ifBlank { "MND-RZP-" + (100000..999999).random() }

        val planName = when (selectedPlanType) {
            "ANNUAL" -> "Annual Pro Plan (₹799/yr)"
            "TRIAL" -> "Free Trial (3 Days)"
            else -> "Monthly Pro Plan (₹79/mo)"
        }

        val tier = when (selectedPlanType) {
            "ANNUAL" -> "ANNUAL_799_INR"
            "TRIAL" -> "TRIAL_1_INR"
            else -> "MONTHLY_79_INR"
        }

        val status = if (selectedPlanType == "TRIAL") "TRIAL_ACTIVE" else "ACTIVE"

        val info = SubscriptionInfo(
            isProUser = true,
            subscriptionTier = tier,
            planType = selectedPlanType,
            planName = planName,
            status = status,
            amountPaid = selectedAmount,
            startDate = startMillis,
            expiryDate = expiryMillis,
            subscriptionExpiryDate = expiryMillis,
            autoPayMandateStatus = if (selectedPlanType == "TRIAL") "TRIAL_ACTIVE" else "ACTIVE",
            autoPayMandateId = mandateId,
            gatewayProvider = "RAZORPAY",
            gatewaySubscriptionId = mandateId,
            settlementAccount = PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED,
            trialStartDate = if (selectedPlanType == "TRIAL") startMillis else _subscriptionState.value.trialStartDate,
            paymentMethod = "Razorpay Checkout (UPI Autopay)",
            hasUsedTrial = true
        )

        saveLocal(context, info)
        _subscriptionState.value = info

        if (effectiveUid.isNotBlank() && FirebaseManager.isFirebaseAvailable) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val firestore = FirebaseManager.firestore
                    if (firestore != null) {
                        val subMap = hashMapOf<String, Any>(
                            "planType" to selectedPlanType,
                            "planName" to planName,
                            "status" to status,
                            "isProUser" to true,
                            "hasUsedTrial" to true,
                            "startDate" to Timestamp(startDate),
                            "expiryDate" to Timestamp(expiryDate),
                            "startTimestamp" to startMillis,
                            "expiryTimestamp" to expiryMillis,
                            "amountPaid" to selectedAmount,
                            "mandateId" to mandateId,
                            "subscriptionTier" to tier,
                            "subscriptionExpiryDate" to expiryMillis,
                            "autoPayMandateStatus" to if (selectedPlanType == "TRIAL") "TRIAL_ACTIVE" else "ACTIVE",
                            "gatewayProvider" to "RAZORPAY",
                            "gatewaySubscriptionId" to mandateId,
                            "settlementAccount" to PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED,
                            "paymentMethod" to "Razorpay Checkout (UPI Autopay)",
                            "lastUpdated" to System.currentTimeMillis()
                        )

                        firestore.collection("users").document(effectiveUid)
                            .collection("subscription").document("current")
                            .set(subMap, com.google.firebase.firestore.SetOptions.merge()).await()

                        firestore.collection("users").document(effectiveUid)
                            .set(
                                hashMapOf(
                                    "isProUser" to true,
                                    "subscriptionTier" to tier,
                                    "planType" to selectedPlanType,
                                    "planName" to planName,
                                    "subscriptionStatus" to status,
                                    "hasUsedTrial" to true,
                                    "expiryTimestamp" to expiryMillis,
                                    "updatedAt" to System.currentTimeMillis()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            ).await()
                        Log.d(TAG, "Successfully updated Firestore users/$effectiveUid/subscription/current with $selectedPlanType ($validityDays days)")
                    }

                    // Update OneSignal CRM Tagging
                    try {
                        com.onesignal.OneSignal.User.addTag("subscription_status", "PRO_ACTIVE")
                        com.onesignal.OneSignal.User.addTag("is_pro_user", "true")
                        com.onesignal.OneSignal.User.addTag("has_used_trial", "true")
                        com.onesignal.OneSignal.User.addTag("plan_type", selectedPlanType)
                    } catch (e: Exception) {
                        Log.d(TAG, "OneSignal tag update error: ${e.localizedMessage}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating Firestore in onPaymentSuccess: ${e.localizedMessage}")
                }
            }
        }

        com.example.data.subscription.AppSessionManager.verifyAndEnforceSubscriptionLock(context, effectiveUid)
        onComplete?.invoke()
    }

    fun activateSubscriptionPlan(
        context: Context,
        tier: String, // "MONTHLY_79_INR", "ANNUAL_799_INR", "MONTHLY", "ANNUAL"
        paymentMethod: String,
        userUid: String,
        gatewayProvider: String = "RAZORPAY",
        subscriptionId: String = "",
        onComplete: (Boolean, String) -> Unit
    ) {
        val isAnnual = tier.contains("ANNUAL", ignoreCase = true)
        val planType = if (isAnnual) "ANNUAL" else "MONTHLY"
        val amount = if (isAnnual) 799.0 else 79.0
        val planName = if (isAnnual) "Annual Pro Plan (₹799/yr)" else "Monthly Pro Plan (₹79/mo)"

        val currentCalendar = Calendar.getInstance()
        val start = currentCalendar.timeInMillis
        currentCalendar.add(Calendar.DAY_OF_YEAR, if (isAnnual) 365 else 30)
        val expiry = currentCalendar.timeInMillis
        val mandateId = if (subscriptionId.isNotBlank()) subscriptionId else "SUB-GATEWAY-" + (100000..999999).random()

        val info = SubscriptionInfo(
            isProUser = true,
            subscriptionTier = if (isAnnual) "ANNUAL_799_INR" else "MONTHLY_79_INR",
            planType = planType,
            planName = planName,
            status = "ACTIVE",
            amountPaid = amount,
            startDate = start,
            expiryDate = expiry,
            subscriptionExpiryDate = expiry,
            autoPayMandateStatus = "ACTIVE",
            autoPayMandateId = mandateId,
            gatewayProvider = gatewayProvider,
            gatewaySubscriptionId = mandateId,
            settlementAccount = PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED,
            trialStartDate = if (_subscriptionState.value.trialStartDate > 0) _subscriptionState.value.trialStartDate else start,
            paymentMethod = paymentMethod,
            hasUsedTrial = true
        )

        saveLocal(context, info)
        _subscriptionState.value = info

        syncToFirebase(userUid, info)
        onComplete(true, "Successfully subscribed to $planName via $gatewayProvider! Mandate Ref: $mandateId")
    }

    fun cancelSubscription(
        context: Context,
        userUid: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val current = _subscriptionState.value
        val now = System.currentTimeMillis()
        val updated = current.copy(
            autoPayMandateStatus = "CANCELLED",
            status = "CANCELLED",
            isProUser = current.effectiveExpiry > now
        )

        saveLocal(context, updated)
        _subscriptionState.value = updated
        syncToFirebase(userUid, updated)

        onComplete(true, "Auto-pay mandate cancelled on ${current.gatewayProvider}. You retain Pro benefits until ${current.displayDaysText}.")
    }

    /**
     * Handles background Webhooks from Razorpay or PhonePe (e.g. Mandate Authenticated, Monthly Auto-Debit Success/Failure).
     */
    fun handleWebhookUpdate(
        context: Context,
        userUid: String,
        eventType: PaymentGatewayHandler.WebhookEventType,
        mandateRef: String
    ) {
        val current = _subscriptionState.value
        val now = System.currentTimeMillis()

        val updated = when (eventType) {
            PaymentGatewayHandler.WebhookEventType.SUBSCRIPTION_AUTHENTICATED -> {
                current.copy(
                    isProUser = true,
                    status = "ACTIVE",
                    autoPayMandateStatus = "ACTIVE",
                    autoPayMandateId = mandateRef.ifBlank { current.autoPayMandateId }
                )
            }
            PaymentGatewayHandler.WebhookEventType.RECURRING_DEBIT_SUCCESS -> {
                val newExpiry = (if (current.effectiveExpiry > now) current.effectiveExpiry else now) + TimeUnit.DAYS.toMillis(30)
                current.copy(
                    isProUser = true,
                    status = "ACTIVE",
                    autoPayMandateStatus = "ACTIVE",
                    expiryDate = newExpiry,
                    subscriptionExpiryDate = newExpiry
                )
            }
            PaymentGatewayHandler.WebhookEventType.RECURRING_DEBIT_FAILED -> {
                current.copy(
                    autoPayMandateStatus = "FAILED",
                    status = "EXPIRED",
                    isProUser = false
                )
            }
            PaymentGatewayHandler.WebhookEventType.SUBSCRIPTION_CANCELLED -> {
                current.copy(
                    autoPayMandateStatus = "CANCELLED",
                    status = "CANCELLED",
                    isProUser = current.effectiveExpiry > now
                )
            }
            PaymentGatewayHandler.WebhookEventType.SUBSCRIPTION_HALTED -> {
                current.copy(
                    autoPayMandateStatus = "FAILED",
                    status = "EXPIRED",
                    isProUser = false
                )
            }
        }

        saveLocal(context, updated)
        _subscriptionState.value = updated
        syncToFirebase(userUid, updated)
    }

    fun updateState(info: SubscriptionInfo) {
        _subscriptionState.value = info
    }

    fun saveLocal(context: Context, info: SubscriptionInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_PRO, info.isProUser)
            .putString(KEY_TIER, info.subscriptionTier)
            .putString(KEY_PLAN_TYPE, info.planType)
            .putString(KEY_PLAN_NAME, info.planName)
            .putString(KEY_STATUS, info.status)
            .putFloat(KEY_AMOUNT_PAID, info.amountPaid.toFloat())
            .putLong(KEY_START_DATE, info.startDate)
            .putLong(KEY_EXPIRY_DATE, info.expiryDate)
            .putLong(KEY_EXPIRY, info.effectiveExpiry)
            .putString(KEY_MANDATE_STATUS, info.autoPayMandateStatus)
            .putString(KEY_MANDATE_ID, info.autoPayMandateId)
            .putString(KEY_GATEWAY_PROVIDER, info.gatewayProvider)
            .putString(KEY_GATEWAY_SUB_ID, info.gatewaySubscriptionId)
            .putString(KEY_SETTLEMENT_ACCT, info.settlementAccount)
            .putLong(KEY_TRIAL_START, info.trialStartDate)
            .putString(KEY_PAYMENT_METHOD, info.paymentMethod)
            .putBoolean(KEY_HAS_USED_TRIAL, info.hasUsedTrial || info.trialStartDate > 0L || info.subscriptionTier == "TRIAL_1_INR" || info.planType == "TRIAL" || info.planType == "MONTHLY" || info.planType == "ANNUAL")
            .apply()
    }

    private fun resolveUserUid(providedUid: String): String {
        if (providedUid.isNotBlank()) return providedUid
        return FirebaseManager.auth?.currentUser?.uid ?: ""
    }

    private fun syncToFirebase(userUid: String, info: SubscriptionInfo) {
        val targetUid = resolveUserUid(userUid)
        if (targetUid.isBlank() || !FirebaseManager.isFirebaseAvailable) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val statusStr = if (info.isProUser) {
                        if (info.autoPayMandateStatus == "TRIAL_ACTIVE" || info.planType == "TRIAL" || info.subscriptionTier == "TRIAL_1_INR") "TRIAL_ACTIVE"
                        else if (info.autoPayMandateStatus == "CANCELLED") "CANCELLED"
                        else "ACTIVE"
                    } else {
                        "EXPIRED"
                    }
                    val usedTrialFlag = info.hasUsedTrial || info.trialStartDate > 0L || info.subscriptionTier == "TRIAL_1_INR" || info.planType.isNotBlank() && info.planType != "FREE"

                    val startDateTimestamp = if (info.startDate > 0L) Timestamp(Date(info.startDate)) else null
                    val expiryDateTimestamp = if (info.effectiveExpiry > 0L) Timestamp(Date(info.effectiveExpiry)) else null

                    val subMap = hashMapOf<String, Any>(
                        "isProUser" to info.isProUser,
                        "subscriptionTier" to info.subscriptionTier,
                        "planType" to info.planType,
                        "planName" to info.planName,
                        "status" to statusStr,
                        "amountPaid" to info.amountPaid,
                        "mandateId" to info.autoPayMandateId,
                        "startTimestamp" to info.startDate,
                        "expiryTimestamp" to info.effectiveExpiry,
                        "subscriptionExpiryDate" to info.effectiveExpiry,
                        "lastUpdated" to System.currentTimeMillis(),
                        "autoPayMandateStatus" to info.autoPayMandateStatus,
                        "gatewayProvider" to info.gatewayProvider,
                        "gatewaySubscriptionId" to info.gatewaySubscriptionId,
                        "settlementAccount" to info.settlementAccount,
                        "trialStartDate" to info.trialStartDate,
                        "trialExpiryDate" to info.effectiveExpiry,
                        "paymentMethod" to info.paymentMethod,
                        "hasUsedTrial" to usedTrialFlag
                    )
                    if (startDateTimestamp != null) subMap["startDate"] = startDateTimestamp
                    if (expiryDateTimestamp != null) subMap["expiryDate"] = expiryDateTimestamp

                    firestore.collection("users").document(targetUid)
                        .collection("subscription").document("current")
                        .set(subMap, com.google.firebase.firestore.SetOptions.merge()).await()

                    val userMap = hashMapOf<String, Any>(
                        "isProUser" to info.isProUser,
                        "subscriptionTier" to info.subscriptionTier,
                        "planType" to info.planType,
                        "planName" to info.planName,
                        "subscriptionStatus" to statusStr,
                        "hasUsedTrial" to usedTrialFlag,
                        "expiryTimestamp" to info.effectiveExpiry,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (info.trialStartDate > 0L) {
                        userMap["trialStartDate"] = info.trialStartDate
                    }
                    if (info.effectiveExpiry > 0L) {
                        userMap["trialExpiryDate"] = info.effectiveExpiry
                    }
                    if (info.autoPayMandateId.isNotBlank()) {
                        userMap["mandateId"] = info.autoPayMandateId
                    }

                    firestore.collection("users").document(targetUid)
                        .set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing subscription to Firebase: ${e.localizedMessage}")
            }
        }
    }

    private fun fetchRemoteSubscription(userUid: String, context: Context) {
        val targetUid = resolveUserUid(userUid)
        if (targetUid.isBlank() || !FirebaseManager.isFirebaseAvailable) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    val doc = firestore.collection("users").document(targetUid)
                        .collection("subscription").document("current").get().await()
                    val userDoc = firestore.collection("users").document(targetUid).get().await()

                    val hasUsedTrialDoc = doc.getBoolean("hasUsedTrial")
                        ?: userDoc.getBoolean("hasUsedTrial")
                        ?: false

                    if (doc.exists() || userDoc.exists()) {
                        val isPro = doc.getBoolean("isProUser") ?: userDoc.getBoolean("isProUser") ?: false
                        val tier = doc.getString("subscriptionTier") ?: userDoc.getString("subscriptionTier") ?: "FREE"
                        val rawPlanType = doc.getString("planType") ?: userDoc.getString("planType") ?: tier

                        val planType = when {
                            rawPlanType.contains("ANNUAL", ignoreCase = true) -> "ANNUAL"
                            rawPlanType.contains("MONTHLY", ignoreCase = true) -> "MONTHLY"
                            rawPlanType.contains("TRIAL", ignoreCase = true) -> "TRIAL"
                            else -> "FREE"
                        }

                        val planName = doc.getString("planName") ?: userDoc.getString("planName") ?: when (planType) {
                            "ANNUAL" -> "Annual Pro Plan (₹799/yr)"
                            "MONTHLY" -> "Monthly Pro Plan (₹79/mo)"
                            "TRIAL" -> "Free Trial (3 Days)"
                            else -> "Free Plan"
                        }

                        val status = doc.getString("status") ?: doc.getString("autoPayMandateStatus") ?: userDoc.getString("subscriptionStatus") ?: "FREE"
                        val amountPaid = doc.getDouble("amountPaid") ?: when (planType) {
                            "ANNUAL" -> 799.0
                            "MONTHLY" -> 79.0
                            "TRIAL" -> 1.0
                            else -> 0.0
                        }

                        val startMillis = extractTimestampMillis(doc.get("startDate"))
                            ?: doc.getLong("startTimestamp")
                            ?: doc.getLong("trialStartDate")
                            ?: 0L

                        val expiry = extractTimestampMillis(doc.get("expiryDate"))
                            ?: doc.getLong("expiryTimestamp")
                            ?: doc.getLong("subscriptionExpiryDate")
                            ?: doc.getLong("trialExpiryDate")
                            ?: 0L

                        val mandateStatus = doc.getString("autoPayMandateStatus") ?: userDoc.getString("subscriptionStatus") ?: "NONE"
                        val mandateId = doc.getString("mandateId") ?: doc.getString("autoPayMandateId") ?: userDoc.getString("mandateId") ?: ""
                        val provider = doc.getString("gatewayProvider") ?: "RAZORPAY"
                        val subId = doc.getString("gatewaySubscriptionId") ?: ""
                        val settlement = doc.getString("settlementAccount") ?: PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED
                        val trialStart = doc.getLong("trialStartDate") ?: startMillis
                        val method = doc.getString("paymentMethod") ?: ""
                        val hasUsedTrial = hasUsedTrialDoc || trialStart > 0L || tier.contains("TRIAL") || planType != "FREE"

                        val now = System.currentTimeMillis()
                        val validPro = isPro && (expiry == 0L || expiry > now)

                        val remoteInfo = SubscriptionInfo(
                            isProUser = validPro,
                            subscriptionTier = tier,
                            planType = planType,
                            planName = planName,
                            status = status,
                            amountPaid = amountPaid,
                            startDate = startMillis,
                            expiryDate = expiry,
                            subscriptionExpiryDate = expiry,
                            autoPayMandateStatus = mandateStatus,
                            autoPayMandateId = mandateId,
                            gatewayProvider = provider,
                            gatewaySubscriptionId = subId,
                            settlementAccount = settlement,
                            trialStartDate = trialStart,
                            paymentMethod = method,
                            hasUsedTrial = hasUsedTrial
                        )

                        saveLocal(context, remoteInfo)
                        _subscriptionState.value = remoteInfo
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching remote subscription: ${e.localizedMessage}")
            }
        }
    }

    private fun extractTimestampMillis(field: Any?): Long? {
        return when (field) {
            is Timestamp -> field.toDate().time
            is Date -> field.time
            is Long -> field
            is Number -> field.toLong()
            else -> null
        }
    }
}
