package com.example.data.repository

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Gets the currently authenticated Firebase user.
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Initiates Firebase Phone Number verification via official PhoneAuthProvider.
     * Passes foreground Activity to allow silent verification via Play Integrity API / safety handshake without failing.
     */
    fun verifyPhoneNumber(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        val cleanDigits = phoneNumber.replace("\\D".toRegex(), "")
        val formattedPhoneNumber = when {
            phoneNumber.startsWith("+") -> phoneNumber
            cleanDigits.length == 10 -> "+91$cleanDigits"
            else -> "+$cleanDigits"
        }

        Log.d(TAG, "Starting Firebase Phone Auth verification for: $formattedPhoneNumber with activity: ${activity.localClassName}")

        val optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(formattedPhoneNumber) // Must include country code, e.g. +91XXXXXXXXXX
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity) // Required for app verification
            .setCallbacks(callbacks)

        if (resendToken != null) {
            optionsBuilder.setForceResendingToken(resendToken)
        }

        val options = optionsBuilder.build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Signs in with a PhoneAuthCredential generated automatically or via verification code.
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                syncUserProfileAndSession(user, provider = "phone")
            }
            Result.success(authResult)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithPhoneCredential error: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Verifies the 6-digit OTP code using verification ID and PhoneAuthProvider credential.
     */
    suspend fun verifyOtp(verificationId: String, otpCode: String): Result<AuthResult> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otpCode.trim())
            signInWithPhoneCredential(credential)
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp error: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Authenticates with Firebase using a Google ID Token.
     */
    suspend fun signInWithGoogle(idToken: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                syncUserProfileAndSession(user, provider = "google")
            }
            Result.success(authResult)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In error: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Gets a configured GoogleSignInClient for legacy fallback sign-in.
     */
    fun getLegacyGoogleSignInClient(context: Context, webClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Authenticates with Firebase using a legacy GoogleSignInAccount.
     */
    suspend fun signInWithGoogleAccount(account: GoogleSignInAccount): Result<AuthResult> {
        val idToken = account.idToken
        if (idToken.isNullOrEmpty()) {
            return Result.failure(Exception("Google ID Token is null or empty from GoogleSignInAccount."))
        }
        return signInWithGoogle(idToken)
    }

    /**
     * Represents the server-side persistent trial & subscription status for a user.
     */
    data class UserTrialStatus(
        val hasUsedTrial: Boolean = false,
        val isProUser: Boolean = false,
        val subscriptionStatus: String = "FREE",
        val subscriptionTier: String = "FREE",
        val trialStartDate: Long = 0L,
        val trialExpiryDate: Long = 0L,
        val mandateId: String = ""
    )

    /**
     * Fetches the user's Firestore document to verify trial eligibility and subscription status from the server.
     */
    suspend fun fetchUserTrialStatus(userId: String): UserTrialStatus = withContext(Dispatchers.IO) {
        try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val subDoc = firestore.collection("users").document(userId)
                .collection("subscription").document("current").get().await()

            val hasUsedTrial = (userDoc.getBoolean("hasUsedTrial") ?: false) ||
                    (subDoc.getBoolean("hasUsedTrial") ?: false) ||
                    ((userDoc.getLong("trialStartDate") ?: 0L) > 0L) ||
                    ((subDoc.getLong("trialStartDate") ?: 0L) > 0L) ||
                    (subDoc.getString("subscriptionTier") == "TRIAL_1_INR")

            val isPro = subDoc.getBoolean("isProUser") ?: userDoc.getBoolean("isProUser") ?: false
            val status = subDoc.getString("status") ?: userDoc.getString("subscriptionStatus") ?: "FREE"
            val tier = subDoc.getString("subscriptionTier") ?: userDoc.getString("subscriptionTier") ?: "FREE"
            val trialStart = userDoc.getLong("trialStartDate") ?: subDoc.getLong("trialStartDate") ?: 0L
            val trialExpiry = userDoc.getLong("trialExpiryDate") ?: subDoc.getLong("expiryTimestamp") ?: 0L
            val mandateId = userDoc.getString("mandateId") ?: subDoc.getString("mandateId") ?: ""

            UserTrialStatus(
                hasUsedTrial = hasUsedTrial,
                isProUser = isPro,
                subscriptionStatus = status,
                subscriptionTier = tier,
                trialStartDate = trialStart,
                trialExpiryDate = trialExpiry,
                mandateId = mandateId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user trial status from Firestore: ${e.localizedMessage}")
            UserTrialStatus()
        }
    }

    /**
     * Syncs user details to Firestore under `users/{userId}` and initializes subscription session.
     * Enforces server-side trial persistence (hasUsedTrial, trialStartDate, trialExpiryDate, mandateId).
     */
    suspend fun syncUserProfileAndSession(
        user: FirebaseUser,
        provider: String,
        context: Context? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(user.uid)
            val docSnap = userRef.get().await()
            val isNewRegistration = !docSnap.exists()

            var hasAlreadyUsedTrial = false
            var serverTrialStart = 0L
            var serverTrialExpiry = 0L
            var serverMandateId = ""
            var serverSubStatus = "FREE"

            if (isNewRegistration) {
                val nowTime = System.currentTimeMillis()
                val profileData = hashMapOf<String, Any>(
                    "uid" to user.uid,
                    "email" to (user.email ?: ""),
                    "phoneNumber" to (user.phoneNumber ?: ""),
                    "displayName" to (user.displayName ?: "User ${user.uid.take(6)}"),
                    "createdAt" to nowTime,
                    "lastLoginAt" to nowTime,
                    "authProvider" to provider,
                    "role" to "user",
                    "hasUsedTrial" to false,
                    "subscriptionStatus" to "FREE"
                )
                userRef.set(profileData, SetOptions.merge()).await()
                Log.d(TAG, "Created new user profile document for ${user.uid} with hasUsedTrial=false")
            } else {
                hasAlreadyUsedTrial = docSnap.getBoolean("hasUsedTrial") ?: false
                serverTrialStart = docSnap.getLong("trialStartDate") ?: 0L
                serverTrialExpiry = docSnap.getLong("trialExpiryDate") ?: 0L
                serverMandateId = docSnap.getString("mandateId") ?: ""
                serverSubStatus = docSnap.getString("subscriptionStatus") ?: docSnap.getString("status") ?: "FREE"

                val updateData = hashMapOf<String, Any>(
                    "lastLoginAt" to System.currentTimeMillis()
                )
                if (!user.email.isNullOrEmpty()) updateData["email"] = user.email!!
                if (!user.phoneNumber.isNullOrEmpty()) updateData["phoneNumber"] = user.phoneNumber!!
                if (!user.displayName.isNullOrEmpty()) updateData["displayName"] = user.displayName!!
                userRef.set(updateData, SetOptions.merge()).await()
            }

            // Sync FCM Token to Firestore under users/{userId} as fcmToken
            com.example.service.MyFirebaseMessagingService.syncFcmTokenToFirestore(user.uid)

            // Check / sync users/{userId}/subscription/current path
            val subRef = firestore.collection("users")
                .document(user.uid)
                .collection("subscription")
                .document("current")

            val subSnap = subRef.get().await()
            if (subSnap.exists()) {
                val subHasUsedTrial = subSnap.getBoolean("hasUsedTrial") ?: false
                val subTrialStart = subSnap.getLong("trialStartDate") ?: 0L
                val subMandateId = subSnap.getString("mandateId") ?: ""
                if (subHasUsedTrial || subTrialStart > 0L) {
                    hasAlreadyUsedTrial = true
                }
                if (subTrialStart > 0L) serverTrialStart = subTrialStart
                if (subMandateId.isNotBlank()) serverMandateId = subMandateId
            }

            // Ensure root user document maintains hasUsedTrial flag if set in sub-document
            if (hasAlreadyUsedTrial) {
                val fixMap = hashMapOf<String, Any>(
                    "hasUsedTrial" to true
                )
                if (serverTrialStart > 0L) fixMap["trialStartDate"] = serverTrialStart
                if (serverTrialExpiry > 0L) fixMap["trialExpiryDate"] = serverTrialExpiry
                if (serverMandateId.isNotBlank()) fixMap["mandateId"] = serverMandateId
                userRef.set(fixMap, SetOptions.merge()).await()
            }

            // OneSignal User Identification & CRM Tagging
            try {
                com.onesignal.OneSignal.login(user.uid)
                val crmStatus = if (hasAlreadyUsedTrial) "TRIAL_CLAIMED" else "NEW_USER"
                com.onesignal.OneSignal.User.addTag("subscription_status", crmStatus)
                com.onesignal.OneSignal.User.addTag("user_role", "merchant")
                com.onesignal.OneSignal.User.addTag("auth_provider", provider)
                com.onesignal.OneSignal.User.addTag("has_used_trial", if (hasAlreadyUsedTrial) "true" else "false")

                // Welcome Push Notification Trigger & account_created_date tag upon new account registration
                if (isNewRegistration) {
                    val createdAtStr = System.currentTimeMillis().toString()
                    com.onesignal.OneSignal.User.addTag("account_created_date", createdAtStr)
                    com.onesignal.OneSignal.User.addTag("trial_active", "true")
                    com.onesignal.OneSignal.User.addTag("welcome_notified", "true")
                    Log.d(TAG, "Set OneSignal account_created_date tag: $createdAtStr for new registration")

                    // Trigger instant local notification / initial welcome alert
                    triggerWelcomeNotification(context)
                }

                if (!user.email.isNullOrBlank()) {
                    com.onesignal.OneSignal.User.addEmail(user.email!!)
                }
                if (!user.phoneNumber.isNullOrBlank()) {
                    com.onesignal.OneSignal.User.addSms(user.phoneNumber!!)
                }
                Log.d(TAG, "OneSignal user identified and tagged successfully for ${user.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "OneSignal login/tagging error: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user profile and subscription in Firestore: ${e.localizedMessage}")
        }
    }

    /**
     * Triggers an instant local notification or welcome alert for newly registered SmartPOS merchants.
     */
    fun triggerWelcomeNotification(context: Context?) {
        if (context == null) return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val channelId = "smartpos_welcome_channel"
            val channelName = "SmartPOS Welcome & Onboarding"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Welcome alerts and onboarding notifications for SmartPOS users"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("screen_route", "dashboard")
                putExtra("target_screen", "dashboard")
                putExtra("source", "welcome_registration")
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                2003,
                intent,
                pendingIntentFlags
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🎉 Welcome to SmartPOS!")
                .setContentText("Your 3-Day Free Trial is Active. Enjoy unlimited invoices, thermal printing & cloud sync!")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "🎉 Welcome to SmartPOS! 🚀\n\nYour 3-day free trial has started! Enjoy full access to unlimited invoices, Bluetooth thermal printing, Udhar Khata ledger & cloud sync. Tap to start billing now!"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(2003, notification)
            Log.d(TAG, "Instant welcome notification dispatched successfully to newly registered user")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger welcome notification: ${e.localizedMessage}")
        }
    }

    /**
     * Signs out the current user, clears offline Firestore persistence cache,
     * purges local Room DB data, and resets session state.
     */
    suspend fun signOut(
        context: Context? = null,
        billingRepository: BillingRepository? = null
    ) = withContext(Dispatchers.IO) {
        try {
            com.onesignal.OneSignal.logout()
            Log.d(TAG, "OneSignal logged out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "OneSignal logout error: ${e.localizedMessage}")
        }

        try {
            firebaseAuth.signOut()
            Log.d(TAG, "FirebaseAuth signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth signOut error: ${e.localizedMessage}")
        }

        try {
            firestore.clearPersistence().await()
            Log.d(TAG, "Firestore persistence cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore clearPersistence skipped or already closed: ${e.localizedMessage}")
        }

        try {
            billingRepository?.clearLocalCache()
        } catch (e: Exception) {
            Log.e(TAG, "Clear local database cache error: ${e.localizedMessage}")
        }

        if (context != null) {
            try {
                com.example.data.subscription.AppSessionManager.clearSession(context)
            } catch (e: Exception) {
                Log.e(TAG, "Clear session preferences error: ${e.localizedMessage}")
            }
        }
    }
}
