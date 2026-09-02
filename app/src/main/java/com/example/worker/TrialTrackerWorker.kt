package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.firebase.FirebaseManager
import com.example.data.subscription.SubscriptionManager
import com.onesignal.OneSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that checks 3-day trial expiry for SmartPOS users.
 * When trial expires:
 * 1. Dispatches rich local notification leading to Paywall.
 * 2. Tags OneSignal user with trial_expired = true for server-side push campaigns.
 * 3. Updates local & remote subscription state.
 */
class TrialTrackerWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "TrialTrackerWorker"
        const val WORK_NAME_PERIODIC = "SmartPOSTrialTrackerPeriodic"
        const val WORK_NAME_ONE_TIME = "SmartPOSTrialTrackerOneTime"

        const val CHANNEL_ID = "smartpos_trial_expiry_channel"
        const val CHANNEL_NAME = "Trial & Subscription Alerts"
        const val NOTIFICATION_ID = 2001

        private const val PREFS_NAME = "smartpos_trial_worker_prefs"
        private const val KEY_LAST_NOTIFIED_TRIAL_START = "last_notified_trial_start"

        /**
         * Schedules periodic check every 1 hour (WorkManager minimum interval is 15 minutes).
         */
        fun schedule(context: Context) {
            try {
                val periodicWork = PeriodicWorkRequestBuilder<TrialTrackerWorker>(
                    1, TimeUnit.HOURS,
                    15, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWork
                )
                Log.d(TAG, "Periodic TrialTrackerWorker scheduled successfully (1 hour interval)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule TrialTrackerWorker: ${e.localizedMessage}")
            }
        }

        /**
         * Schedules a one-time check with a delay or immediately.
         */
        fun scheduleOneTime(context: Context, delayMillis: Long = 0L) {
            try {
                val builder = OneTimeWorkRequestBuilder<TrialTrackerWorker>()
                if (delayMillis > 0) {
                    builder.setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                }
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    builder.build()
                )
                Log.d(TAG, "One-time TrialTrackerWorker scheduled with delay: $delayMillis ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule one-time TrialTrackerWorker: ${e.localizedMessage}")
            }
        }

        /**
         * Direct static helper to check trial expiry immediately (e.g. on app startup or resume).
         */
        fun checkAndNotifyIfExpired(context: Context, trialStartDate: Long, isProActive: Boolean, tier: String) {
            if (trialStartDate <= 0L) return

            val now = System.currentTimeMillis()
            val threeDaysMillis = TimeUnit.DAYS.toMillis(3) // 3-day trial period
            val isExpired = now >= (trialStartDate + threeDaysMillis)

            // Check if user upgraded to full paid monthly / annual plan
            val isFullPaid = tier.equals("MONTHLY_79_INR", ignoreCase = true) ||
                    tier.equals("ANNUAL_799_INR", ignoreCase = true)

            if (isExpired && !isFullPaid) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastNotified = prefs.getLong(KEY_LAST_NOTIFIED_TRIAL_START, 0L)

                if (lastNotified != trialStartDate) {
                    triggerTrialExpiryNotification(context)
                    updateTagsAndLockTrial(context, trialStartDate)
                    prefs.edit().putLong(KEY_LAST_NOTIFIED_TRIAL_START, trialStartDate).apply()
                }
            }
        }

        /**
         * Creates notification channel and shows the high-priority trial expired notification.
         */
        fun triggerTrialExpiryNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Create Notification Channel for Android O (8.0) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts when SmartPOS free trial ends or payment is required"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Click Intent leading directly to Paywall Screen
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("screen_route", "paywall")
                putExtra("target_screen", "paywall")
                putExtra("source", "trial_expiry_worker")
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                pendingIntentFlags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⚠️ Your SmartPOS Free Trial Has Ended!")
                .setContentText("Upgrade to Pro now to keep billing, printing, and inventory synced without interruptions.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "⚠️ Your SmartPOS Free Trial Has Ended!\n\nUpgrade to Pro now to keep billing, printing, and inventory synced without interruptions. Tap here to continue with unlimited POS billing."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Trial expiry notification dispatched successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display notification: ${e.localizedMessage}")
            }
        }

        private fun updateTagsAndLockTrial(context: Context, trialStartDate: Long) {
            try {
                // OneSignal Tagging for server-side CRM automation & push campaigns
                OneSignal.User.addTag("trial_expired", "true")
                OneSignal.User.addTag("subscription_status", "TRIAL_EXPIRED")
                Log.d(TAG, "OneSignal trial_expired tag updated to true")
            } catch (e: Exception) {
                Log.e(TAG, "OneSignal tagging error: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing TrialTrackerWorker check...")

            val userUid = FirebaseManager.auth?.currentUser?.uid ?: ""
            var trialStartDate = 0L
            var isProUser = false
            var subscriptionTier = "FREE"

            // 1. Check local SubscriptionManager
            val currentLocalState = SubscriptionManager.subscriptionState.value
            trialStartDate = currentLocalState.trialStartDate
            isProUser = currentLocalState.isProUser
            subscriptionTier = currentLocalState.subscriptionTier

            // 2. Also check Firestore if user is authenticated
            if (userUid.isNotBlank() && FirebaseManager.isFirebaseAvailable) {
                try {
                    val firestore = FirebaseManager.firestore
                    val doc = firestore?.collection("users")
                        ?.document(userUid)
                        ?.collection("subscription")
                        ?.document("current")
                        ?.get()
                        ?.await()

                    if (doc != null && doc.exists()) {
                        val fsTrialStart = doc.getLong("trialStartDate")
                            ?: doc.getLong("createdAt")
                            ?: 0L
                        val fsTier = doc.getString("plan")
                            ?: doc.getString("subscriptionTier")
                            ?: subscriptionTier
                        val fsIsPro = doc.getBoolean("isProUser") ?: isProUser

                        if (fsTrialStart > 0) {
                            trialStartDate = fsTrialStart
                        }
                        subscriptionTier = fsTier
                        isProUser = fsIsPro
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query Firestore subscription document: ${e.localizedMessage}")
                }
            }

            if (trialStartDate > 0L) {
                val now = System.currentTimeMillis()
                val threeDaysMillis = TimeUnit.DAYS.toMillis(3)
                val isExpired = now >= (trialStartDate + threeDaysMillis)

                val isFullPaid = subscriptionTier.equals("MONTHLY_79_INR", ignoreCase = true) ||
                        subscriptionTier.equals("ANNUAL_799_INR", ignoreCase = true)

                if (isExpired && !isFullPaid) {
                    Log.w(TAG, "3-Day Trial has expired! (Started: $trialStartDate, Now: $now)")

                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastNotified = prefs.getLong(KEY_LAST_NOTIFIED_TRIAL_START, 0L)

                    if (lastNotified != trialStartDate) {
                        triggerTrialExpiryNotification(appContext)
                        updateTagsAndLockTrial(appContext, trialStartDate)
                        prefs.edit().putLong(KEY_LAST_NOTIFIED_TRIAL_START, trialStartDate).apply()
                    }
                } else {
                    Log.d(TAG, "Trial active or user has full paid plan. Remaining: ${(trialStartDate + threeDaysMillis - now) / 1000}s")
                }
            } else {
                Log.d(TAG, "No active trial start date found.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TrialTrackerWorker execution failed: ${e.localizedMessage}")
            Result.retry()
        }
    }
}
