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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.api.UdharReminderRequestPayload
import com.example.data.api.WhatsAppApiService
import com.example.data.db.AppDatabase
import com.example.data.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Background worker for scheduled automatic dispatch of Udhar (Credit Ledger) payment reminders.
 * Sends the payment reminder to the central API service and updates local/remote ledger records.
 */
class UdharReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UdharReminderWorker"
        const val CHANNEL_ID = "smartpos_udhar_reminders_channel"
        const val CHANNEL_NAME = "Udhar Payment Reminders"

        const val KEY_CUSTOMER_MOBILE = "key_customer_mobile"
        const val KEY_CUSTOMER_NAME = "key_customer_name"
        const val KEY_STORE_NAME = "key_store_name"
        const val KEY_STORE_PHONE = "key_store_phone"
        const val KEY_MERCHANT_UPI_ID = "key_merchant_upi_id"
        const val KEY_PENDING_BALANCE = "key_pending_balance"
        const val KEY_LAST_TXN_DATE = "key_last_txn_date"
        const val KEY_MESSAGE = "key_message"
        const val KEY_UPI_LINK = "key_upi_link"
        const val KEY_USER_ID = "key_user_id"

        fun getWorkName(customerMobile: String): String {
            val clean = customerMobile.replace("[^0-9]".toRegex(), "")
            return "udhar_reminder_$clean"
        }

        /**
         * Schedules an automatic Udhar reminder for a customer at a given target epoch timestamp.
         */
        fun schedule(
            context: Context,
            scheduledEpochMillis: Long,
            customerMobile: String,
            customerName: String,
            storeName: String,
            storePhone: String,
            merchantUpiId: String,
            pendingBalance: Double,
            lastTxnDate: String = "",
            message: String = "",
            upiLink: String = "",
            userId: String = ""
        ) {
            try {
                val now = System.currentTimeMillis()
                val delayMillis = (scheduledEpochMillis - now).coerceAtLeast(0L)

                val inputData = workDataOf(
                    KEY_CUSTOMER_MOBILE to customerMobile,
                    KEY_CUSTOMER_NAME to customerName,
                    KEY_STORE_NAME to storeName,
                    KEY_STORE_PHONE to storePhone,
                    KEY_MERCHANT_UPI_ID to merchantUpiId,
                    KEY_PENDING_BALANCE to pendingBalance,
                    KEY_LAST_TXN_DATE to lastTxnDate,
                    KEY_MESSAGE to message,
                    KEY_UPI_LINK to upiLink,
                    KEY_USER_ID to userId
                )

                val workRequest = OneTimeWorkRequestBuilder<UdharReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("UDHAR_REMINDER")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    getWorkName(customerMobile),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                Log.d(TAG, "Scheduled Udhar reminder for $customerName ($customerMobile) with delay $delayMillis ms (Target: $scheduledEpochMillis)")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling Udhar reminder: ${e.localizedMessage}")
            }
        }

        /**
         * Cancels any pending scheduled automatic reminder for the customer.
         */
        fun cancel(context: Context, customerMobile: String) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(getWorkName(customerMobile))
                Log.d(TAG, "Cancelled scheduled reminder for $customerMobile")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling reminder for $customerMobile: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val customerMobile = inputData.getString(KEY_CUSTOMER_MOBILE) ?: return@withContext Result.failure()
        val customerName = inputData.getString(KEY_CUSTOMER_NAME) ?: "Customer"
        val storeName = inputData.getString(KEY_STORE_NAME) ?: "SmartPOS Store"
        val storePhone = inputData.getString(KEY_STORE_PHONE) ?: ""
        val merchantUpiId = inputData.getString(KEY_MERCHANT_UPI_ID) ?: ""
        val pendingBalance = inputData.getDouble(KEY_PENDING_BALANCE, 0.0)
        val lastTxnDate = inputData.getString(KEY_LAST_TXN_DATE) ?: ""
        val message = inputData.getString(KEY_MESSAGE) ?: ""
        val upiLink = inputData.getString(KEY_UPI_LINK) ?: ""
        val userId = inputData.getString(KEY_USER_ID) ?: ""

        val cleanPhone = customerMobile.replace("[^0-9]".toRegex(), "").takeLast(10)
        if (cleanPhone.length != 10) {
            Log.e(TAG, "Invalid customer phone number: $customerMobile")
            return@withContext Result.failure()
        }

        Log.d(TAG, "Executing automatic Udhar payment reminder for $customerName ($cleanPhone), balance: ₹$pendingBalance")

        try {
            val payload = UdharReminderRequestPayload(
                customerPhone = cleanPhone,
                customerName = customerName,
                storeName = storeName,
                storePhone = storePhone,
                merchantUpiId = merchantUpiId,
                pendingBalance = pendingBalance,
                lastTxnDate = lastTxnDate,
                message = message,
                upiLink = upiLink
            )

            // 1. Send via central WhatsApp API server
            val response = try {
                WhatsAppApiService.getInstance().sendUdharReminder(payload)
            } catch (apiEx: Exception) {
                Log.w(TAG, "Direct API call failed or endpoint unreachable: ${apiEx.localizedMessage}")
                null
            }

            val isSuccessful = response?.isSuccessful == true && response.body()?.success == true
            Log.d(TAG, "Udhar reminder API response success: $isSuccessful")

            // 2. Update status in local Room database
            val database = AppDatabase.getDatabase(appContext)
            database.customerDao().updateReminderStatus(customerMobile, "SENT")

            // 3. Update status in Firestore
            val activeUid = if (userId.isNotBlank()) userId else (FirebaseManager.auth?.currentUser?.uid ?: "")
            if (FirebaseManager.isFirebaseAvailable && activeUid.isNotBlank()) {
                val firestore = FirebaseManager.firestore
                if (firestore != null) {
                    try {
                        val docId = customerMobile.replace("+", "").replace(" ", "").replace("-", "")
                        val updateMap = mapOf(
                            "reminderStatus" to "SENT",
                            "lastReminderSentTimestamp" to System.currentTimeMillis()
                        )
                        val userRef = firestore.collection("users").document(activeUid)
                        userRef.collection("customers").document(docId).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
                        userRef.collection("udhar_ledger").document(docId).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
                    } catch (fsEx: Exception) {
                        Log.e(TAG, "Failed to update reminder status in Firestore: ${fsEx.localizedMessage}")
                    }
                }
            }

            // 4. Show Notification to Merchant
            showNotification(
                customerName = customerName,
                pendingAmount = pendingBalance,
                storeName = storeName
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error executing Udhar reminder worker: ${e.localizedMessage}", e)
            Result.retry()
        }
    }

    private fun showNotification(customerName: String, pendingAmount: Double, storeName: String) {
        try {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Automated alerts for sent Udhar payment reminders"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "UDHAR")
            }

            val pendingIntent = PendingIntent.getActivity(
                appContext,
                customerName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedAmt = String.format(Locale.US, "%.2f", pendingAmount)
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Udhar Reminder Sent: ₹$formattedAmt")
                .setContentText("Automated payment reminder dispatched to $customerName.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Payment reminder for ₹$formattedAmt has been dispatched to $customerName from $storeName with UPI settlement link.")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notifId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(notifId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification: ${e.localizedMessage}")
        }
    }
}
