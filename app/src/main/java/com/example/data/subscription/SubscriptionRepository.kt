package com.example.data.subscription

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

object SubscriptionRepository {
    private const val TAG = "SubscriptionRepository"

    suspend fun saveSubscription(
        userId: String,
        subscription: SubscriptionModel
    ): Boolean {
        if (userId.isBlank() || !FirebaseManager.isFirebaseAvailable) return false
        val firestore = FirebaseManager.firestore ?: return false

        return try {
            val startDateTimestamp = Timestamp(Date(subscription.startDate))
            val expiryDateTimestamp = Timestamp(Date(subscription.expiryDate))

            val subscriptionData = hashMapOf<String, Any>(
                "planType" to subscription.planType,
                "planName" to subscription.planName,
                "status" to subscription.status,
                "isProUser" to subscription.isProUser,
                "hasUsedTrial" to subscription.hasUsedTrial,
                "amountPaid" to subscription.amountPaid,
                "startDate" to startDateTimestamp,
                "expiryDate" to expiryDateTimestamp,
                "startTimestamp" to subscription.startDate,
                "expiryTimestamp" to subscription.expiryDate,
                "subscriptionExpiryDate" to subscription.expiryDate,
                "subscriptionTier" to if (subscription.planType == "ANNUAL") "ANNUAL_799_INR" else if (subscription.planType == "MONTHLY") "MONTHLY_79_INR" else "TRIAL_1_INR",
                "mandateId" to subscription.mandateId,
                "gatewayProvider" to subscription.gatewayProvider,
                "paymentMethod" to subscription.paymentMethod,
                "lastUpdated" to System.currentTimeMillis()
            )

            // Write to users/{userId}/subscription/current
            firestore.collection("users").document(userId)
                .collection("subscription").document("current")
                .set(subscriptionData, SetOptions.merge())
                .await()

            // Update top-level user document
            val userData = hashMapOf<String, Any>(
                "isProUser" to subscription.isProUser,
                "subscriptionTier" to if (subscription.planType == "ANNUAL") "ANNUAL_799_INR" else if (subscription.planType == "MONTHLY") "MONTHLY_79_INR" else "TRIAL_1_INR",
                "planType" to subscription.planType,
                "planName" to subscription.planName,
                "subscriptionStatus" to subscription.status,
                "hasUsedTrial" to true,
                "expiryTimestamp" to subscription.expiryDate,
                "updatedAt" to System.currentTimeMillis()
            )
            if (subscription.mandateId.isNotBlank()) {
                userData["mandateId"] = subscription.mandateId
            }

            firestore.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .await()

            Log.d(TAG, "Successfully synced subscription (${subscription.planType}) to Firestore for $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscription to Firestore: ${e.localizedMessage}")
            false
        }
    }

    suspend fun fetchSubscription(userId: String): SubscriptionModel? {
        if (userId.isBlank() || !FirebaseManager.isFirebaseAvailable) return null
        val firestore = FirebaseManager.firestore ?: return null

        return try {
            val doc = firestore.collection("users").document(userId)
                .collection("subscription").document("current").get().await()
            val userDoc = firestore.collection("users").document(userId).get().await()

            if (!doc.exists() && !userDoc.exists()) return null

            val isPro = doc.getBoolean("isProUser") ?: userDoc.getBoolean("isProUser") ?: false
            val tier = doc.getString("planType") ?: doc.getString("subscriptionTier") ?: userDoc.getString("planType") ?: userDoc.getString("subscriptionTier") ?: "FREE"
            val planName = doc.getString("planName") ?: userDoc.getString("planName") ?: when (tier) {
                "MONTHLY", "MONTHLY_79_INR" -> "Monthly Pro Plan (₹79/mo)"
                "ANNUAL", "ANNUAL_799_INR" -> "Annual Pro Plan (₹799/yr)"
                "TRIAL", "TRIAL_1_INR" -> "Free Trial (3 Days)"
                else -> "Free Plan"
            }
            val status = doc.getString("status") ?: doc.getString("autoPayMandateStatus") ?: userDoc.getString("subscriptionStatus") ?: "FREE"
            val amountPaid = doc.getDouble("amountPaid") ?: 0.0

            val startMillis = extractTimestampMillis(doc.get("startDate"))
                ?: doc.getLong("startTimestamp")
                ?: doc.getLong("trialStartDate")
                ?: 0L

            val expiryMillis = extractTimestampMillis(doc.get("expiryDate"))
                ?: doc.getLong("expiryTimestamp")
                ?: doc.getLong("subscriptionExpiryDate")
                ?: doc.getLong("trialExpiryDate")
                ?: 0L

            val hasUsedTrial = doc.getBoolean("hasUsedTrial")
                ?: userDoc.getBoolean("hasUsedTrial")
                ?: (startMillis > 0L || tier.contains("TRIAL") || tier.contains("MONTHLY") || tier.contains("ANNUAL"))

            val mandateId = doc.getString("mandateId") ?: doc.getString("autoPayMandateId") ?: userDoc.getString("mandateId") ?: ""
            val gatewayProvider = doc.getString("gatewayProvider") ?: "RAZORPAY"
            val paymentMethod = doc.getString("paymentMethod") ?: ""

            // Standardize planType
            val standardizedPlanType = when {
                tier.contains("MONTHLY", ignoreCase = true) -> "MONTHLY"
                tier.contains("ANNUAL", ignoreCase = true) -> "ANNUAL"
                tier.contains("TRIAL", ignoreCase = true) -> "TRIAL"
                else -> "FREE"
            }

            SubscriptionModel(
                planType = standardizedPlanType,
                planName = planName,
                status = status,
                isProUser = isPro,
                amountPaid = amountPaid,
                startDate = startMillis,
                expiryDate = expiryMillis,
                hasUsedTrial = hasUsedTrial,
                mandateId = mandateId,
                gatewayProvider = gatewayProvider,
                paymentMethod = paymentMethod
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching subscription from Firestore: ${e.localizedMessage}")
            null
        }
    }

    suspend fun restoreSubscriptionFromFirestore(
        context: Context,
        userId: String,
        mobileNumber: String = ""
    ): SubscriptionInfo? {
        if (!FirebaseManager.isFirebaseAvailable) return null
        val firestore = FirebaseManager.firestore ?: return null

        return try {
            var targetUid = userId.trim()
            var doc: com.google.firebase.firestore.DocumentSnapshot? = null
            var userDoc: com.google.firebase.firestore.DocumentSnapshot? = null

            // 1. Try finding by primary userId
            if (targetUid.isNotBlank()) {
                doc = firestore.collection("users").document(targetUid)
                    .collection("subscription").document("current").get().await()
                userDoc = firestore.collection("users").document(targetUid).get().await()
            }

            // 2. If docs don't exist and mobileNumber is available, search by mobile/phone fields
            if ((doc == null || !doc.exists()) && (userDoc == null || !userDoc.exists()) && mobileNumber.isNotBlank()) {
                val cleanDigits = mobileNumber.replace("\\D".toRegex(), "")
                val formattedPhone = if (cleanDigits.length == 10) "+91$cleanDigits" else "+$cleanDigits"

                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("phoneNumber", formattedPhone)
                    .limit(1)
                    .get()
                    .await()

                val foundDoc = if (!querySnapshot.isEmpty) {
                    querySnapshot.documents.firstOrNull()
                } else {
                    val altQuery = firestore.collection("users")
                        .whereEqualTo("mobileNumber", cleanDigits)
                        .limit(1)
                        .get()
                        .await()
                    altQuery.documents.firstOrNull()
                }

                if (foundDoc != null) {
                    targetUid = foundDoc.id
                    userDoc = foundDoc
                    doc = firestore.collection("users").document(targetUid)
                        .collection("subscription").document("current").get().await()
                }
            }

            if ((doc == null || !doc.exists()) && (userDoc == null || !userDoc.exists())) {
                Log.d(TAG, "No remote subscription records found in Firestore for $userId / $mobileNumber")
                return null
            }

            val isProDoc = doc?.getBoolean("isProUser") ?: userDoc?.getBoolean("isProUser") ?: false
            val tier = doc?.getString("planType") ?: doc?.getString("subscriptionTier")
                ?: userDoc?.getString("planType") ?: userDoc?.getString("subscriptionTier") ?: "FREE"

            val planType = when {
                tier.contains("ANNUAL", ignoreCase = true) -> "ANNUAL"
                tier.contains("MONTHLY", ignoreCase = true) -> "MONTHLY"
                tier.contains("TRIAL", ignoreCase = true) -> "TRIAL"
                else -> "FREE"
            }

            val planName = doc?.getString("planName") ?: userDoc?.getString("planName") ?: when (planType) {
                "ANNUAL" -> "Annual Pro Plan (₹799/yr)"
                "MONTHLY" -> "Monthly Pro Plan (₹79/mo)"
                "TRIAL" -> "Free Trial (3 Days)"
                else -> "Free Plan"
            }

            val status = doc?.getString("status")
                ?: doc?.getString("autoPayMandateStatus")
                ?: userDoc?.getString("subscriptionStatus")
                ?: userDoc?.getString("status")
                ?: "FREE"

            val amountPaid = doc?.getDouble("amountPaid") ?: when (planType) {
                "ANNUAL" -> 799.0
                "MONTHLY" -> 79.0
                "TRIAL" -> 1.0
                else -> 0.0
            }

            val startMillis = extractTimestampMillis(doc?.get("startDate"))
                ?: doc?.getLong("startTimestamp")
                ?: doc?.getLong("trialStartDate")
                ?: extractTimestampMillis(userDoc?.get("trialStartDate"))
                ?: userDoc?.getLong("trialStartDate")
                ?: 0L

            val expiryTimestamp = extractTimestampMillis(doc?.get("expiryDate"))
                ?: doc?.getLong("expiryTimestamp")
                ?: doc?.getLong("subscriptionExpiryDate")
                ?: doc?.getLong("trialExpiryDate")
                ?: extractTimestampMillis(userDoc?.get("expiryDate"))
                ?: userDoc?.getLong("expiryTimestamp")
                ?: userDoc?.getLong("subscriptionExpiryDate")
                ?: userDoc?.getLong("trialExpiryDate")
                ?: 0L

            val mandateStatus = doc?.getString("autoPayMandateStatus")
                ?: userDoc?.getString("subscriptionStatus")
                ?: userDoc?.getString("status")
                ?: "NONE"

            val mandateId = doc?.getString("mandateId")
                ?: doc?.getString("autoPayMandateId")
                ?: userDoc?.getString("mandateId")
                ?: ""

            val gatewayProvider = doc?.getString("gatewayProvider")
                ?: userDoc?.getString("gatewayProvider")
                ?: "RAZORPAY"

            val paymentMethod = doc?.getString("paymentMethod")
                ?: userDoc?.getString("paymentMethod")
                ?: ""

            val hasUsedTrialDoc = doc?.getBoolean("hasUsedTrial")
                ?: userDoc?.getBoolean("hasUsedTrial")
                ?: (startMillis > 0L || tier.contains("TRIAL") || tier.contains("MONTHLY") || tier.contains("ANNUAL"))

            val currentTime = System.currentTimeMillis()
            val isStatusActive = status.equals("ACTIVE", ignoreCase = true) ||
                    status.equals("TRIAL_ACTIVE", ignoreCase = true) ||
                    mandateStatus.equals("ACTIVE", ignoreCase = true) ||
                    mandateStatus.equals("TRIAL_ACTIVE", ignoreCase = true)

            val isPlanActive = (isStatusActive || isProDoc) && (expiryTimestamp == 0L || expiryTimestamp > currentTime)

            val restoredInfo = SubscriptionInfo(
                isProUser = isPlanActive,
                subscriptionTier = tier,
                planType = planType,
                planName = planName,
                status = if (isPlanActive) (if (planType == "TRIAL") "TRIAL_ACTIVE" else "ACTIVE") else "EXPIRED",
                amountPaid = amountPaid,
                startDate = startMillis,
                expiryDate = expiryTimestamp,
                subscriptionExpiryDate = expiryTimestamp,
                autoPayMandateStatus = if (isPlanActive) (if (planType == "TRIAL") "TRIAL_ACTIVE" else "ACTIVE") else "EXPIRED",
                autoPayMandateId = mandateId,
                gatewayProvider = gatewayProvider,
                gatewaySubscriptionId = mandateId,
                settlementAccount = PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED,
                trialStartDate = if (planType == "TRIAL") startMillis else 0L,
                paymentMethod = paymentMethod,
                hasUsedTrial = hasUsedTrialDoc
            )

            // Save to local SharedPreferences and update in-memory StateFlow
            SubscriptionManager.saveLocal(context, restoredInfo)
            SubscriptionManager.updateState(restoredInfo)

            // Sync OneSignal Tags for CRM & push campaigns
            try {
                if (isPlanActive) {
                    com.onesignal.OneSignal.User.addTag("subscription_status", "PRO_ACTIVE")
                    com.onesignal.OneSignal.User.addTag("is_pro_user", "true")
                    com.onesignal.OneSignal.User.addTag("plan_type", planType)
                } else {
                    com.onesignal.OneSignal.User.addTag("subscription_status", "EXPIRED")
                    com.onesignal.OneSignal.User.addTag("is_pro_user", "false")
                }
            } catch (e: Exception) {
                Log.d(TAG, "OneSignal tag update error during restore: ${e.localizedMessage}")
            }

            Log.d(TAG, "Successfully restored subscription from Firestore: plan=$planType, isPro=$isPlanActive, expiry=$expiryTimestamp")
            restoredInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring subscription from Firestore: ${e.localizedMessage}")
            null
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
