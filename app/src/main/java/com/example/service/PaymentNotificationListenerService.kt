package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.repository.PaymentEvent
import com.example.data.repository.PaymentStatusRepository

class PaymentNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val pkgName = sbn.packageName?.lowercase() ?: ""
            val extras = sbn.notification?.extras ?: return

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            val fullText = "$title $text $bigText $subText".trim()
            if (fullText.isBlank()) return

            // Check if from UPI apps or SMS
            val isUpiOrSmsApp = pkgName.contains("phonepe") ||
                    pkgName.contains("paytm") ||
                    pkgName.contains("google.android.apps.n2p") ||
                    pkgName.contains("wallet") ||
                    pkgName.contains("upiapp") ||
                    pkgName.contains("bhim") ||
                    pkgName.contains("messaging") ||
                    pkgName.contains("sms") ||
                    pkgName.contains("mms")

            // Check keywords indicating payment received / credited
            val containsKeyword = fullText.contains("received", ignoreCase = true) ||
                    fullText.contains("credited", ignoreCase = true) ||
                    fullText.contains("payment received", ignoreCase = true) ||
                    fullText.contains("paid you", ignoreCase = true) ||
                    fullText.contains("sent you", ignoreCase = true) ||
                    fullText.contains("has credited", ignoreCase = true) ||
                    fullText.contains("account credited", ignoreCase = true)

            if (isUpiOrSmsApp || containsKeyword) {
                val amount = PaymentStatusRepository.parseAmountFromText(fullText)
                if (amount != null && amount > 0) {
                    val appName = when {
                        pkgName.contains("phonepe") -> "PhonePe"
                        pkgName.contains("paytm") -> "Paytm"
                        pkgName.contains("google") -> "Google Pay"
                        pkgName.contains("bhim") || pkgName.contains("upiapp") -> "BHIM UPI"
                        else -> "Bank SMS / UPI"
                    }

                    Log.d(TAG, "Payment notification captured! App: $appName, Amount: ₹$amount, Details: $fullText")

                    val event = PaymentEvent(
                        amount = amount,
                        appName = appName,
                        rawText = fullText
                    )
                    PaymentStatusRepository.emitPaymentEvent(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming notification: ${e.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "PaymentNotifService"
    }
}
