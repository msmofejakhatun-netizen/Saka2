package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMessaging"
        const val CHANNEL_ID = "smart_pos_alerts"
        const val CHANNEL_NAME = "Smart POS Alerts"

        /**
         * Utility function to sync the current FCM token to Firestore under `users/{userId}` as `fcmToken`.
         */
        fun syncFcmTokenToFirestore(userId: String? = null) {
            val uid = if (!userId.isNullOrEmpty()) userId else FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrEmpty()) return

            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.d(TAG, "Fetching FCM registration token skipped or failed: ${task.exception?.localizedMessage}")
                        return@addOnCompleteListener
                    }
                    val token = task.result ?: return@addOnCompleteListener
                    Log.d(TAG, "Syncing FCM device token to Firestore for user $uid: $token")

                    val firestore = FirebaseFirestore.getInstance()
                    val userRef = firestore.collection("users").document(uid)
                    userRef.set(mapOf("fcmToken" to token), SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "FCM token successfully synced to Firestore for $uid")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to sync FCM token to Firestore for $uid", e)
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncFcmTokenToFirestore: ${e.localizedMessage}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // Extract title, body, and optional deepLink / intentAction payload
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Smart POS Pro Alert"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "New notification or system update received."

        val deepLink = remoteMessage.data["deepLink"] ?: remoteMessage.data["link"]
        val intentAction = remoteMessage.data["intentAction"] ?: remoteMessage.data["action"]

        showSystemNotification(title, body, deepLink, intentAction)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token generated: $token")

        // Save updated token locally in SharedPreferences
        val prefs = getSharedPreferences("smart_pos_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        // Sync token to Firestore under `users/{userId}` as `fcmToken`
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrEmpty()) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users").document(uid)
                    .set(mapOf("fcmToken" to token), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Saved new FCM Token to Firestore for user: $uid")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error saving FCM token to Firestore", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing Firestore in onNewToken: ${e.localizedMessage}")
            }
        }
    }

    private fun showSystemNotification(
        title: String,
        body: String,
        deepLink: String?,
        intentAction: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high-priority Notification Channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart POS Pro notification channel for sales alerts, billing, and updates"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build Intent for launching MainActivity with deep link & action payloads
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!deepLink.isNullOrEmpty()) {
                putExtra("deepLink", deepLink)
            }
            if (!intentAction.isNullOrEmpty()) {
                putExtra("intentAction", intentAction)
                action = intentAction
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .run {
                if (largeIconBitmap != null) setLargeIcon(largeIconBitmap) else this
            }
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
