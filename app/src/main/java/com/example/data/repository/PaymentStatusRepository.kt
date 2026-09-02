package com.example.data.repository

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PaymentEvent(
    val amount: Double,
    val appName: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
)

object PaymentStatusRepository {
    private const val TAG = "PaymentStatusRepository"

    private val _paymentEvents = MutableSharedFlow<PaymentEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val paymentEvents: SharedFlow<PaymentEvent> = _paymentEvents.asSharedFlow()

    private val _lastDetectedPayment = MutableStateFlow<PaymentEvent?>(null)
    val lastDetectedPayment: StateFlow<PaymentEvent?> = _lastDetectedPayment.asStateFlow()

    fun emitPaymentEvent(event: PaymentEvent) {
        Log.d(TAG, "Emitting payment event: $event")
        _lastDetectedPayment.value = event
        _paymentEvents.tryEmit(event)
    }

    /**
     * Checks if NotificationListenerService is granted permission in system settings.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        try {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification listener status: ${e.localizedMessage}")
        }
        return false
    }

    /**
     * Plays a pleasant payment success audio chime.
     */
    fun playPaymentSuccessChime(context: Context) {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing payment chime: ${e.localizedMessage}")
        }
    }

    /**
     * Parses amount from notification text matching currency symbols or keywords.
     */
    fun parseAmountFromText(text: String): Double? {
        if (text.isBlank()) return null
        try {
            // Regex patterns for currency like ₹180, Rs. 180.50, INR 250, or 180 credited
            val regex = Regex(
                """(?:(?:₹|Rs\.?|INR)\s*([0-9,]+(?:\.[0-9]{1,2})?))|(?:([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|received))""",
                RegexOption.IGNORE_CASE
            )
            val match = regex.find(text)
            if (match != null) {
                val group1 = match.groupValues[1]
                val group2 = match.groupValues[2]
                val rawAmount = (if (group1.isNotBlank()) group1 else group2).replace(",", "")
                return rawAmount.toDoubleOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Regex parsing error: ${e.localizedMessage}")
        }
        return null
    }
}
