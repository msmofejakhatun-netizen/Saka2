package com.example.data.subscription

import android.content.Context
import android.util.Log
import com.example.util.AuthGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SessionAccessState {
    object Granted : SessionAccessState()
    data class Locked(val reason: String) : SessionAccessState()
}

object AppSessionManager {
    private const val TAG = "AppSessionManager"

    private val _accessState = MutableStateFlow<SessionAccessState>(SessionAccessState.Granted)
    val accessState: StateFlow<SessionAccessState> = _accessState.asStateFlow()

    /**
     * Checks subscriptionStatus and subscriptionExpiryDate against current time using AuthGuard.
     * Active States:
     * - TRIAL_ACTIVE (strictly within 3 days of ₹1 setup) OR
     * - PRO_ACTIVE (recurring ₹79 / ₹799 paid and active).
     *
     * Expired / Cancelled States:
     * - If Autopay is expired, cancelled, revoked, or fails after trial/cycle, immediately block app access.
     */
    fun verifyAndEnforceSubscriptionLock(context: Context, userUid: String = ""): SessionAccessState {
        SubscriptionManager.init(context, userUid)
        val info = SubscriptionManager.subscriptionState.value
        val now = System.currentTimeMillis()

        val isSubscriptionValid = AuthGuard.isSubscriptionValid(info, now)

        val result = if (isSubscriptionValid) {
            SessionAccessState.Granted
        } else {
            val message = if (info.hasUsedTrial || info.autoPayMandateStatus == "EXPIRED" || (info.subscriptionExpiryDate > 0L && now >= info.subscriptionExpiryDate)) {
                com.example.worker.TrialTrackerWorker.checkAndNotifyIfExpired(
                    context = context,
                    trialStartDate = info.trialStartDate,
                    isProActive = false,
                    tier = info.subscriptionTier
                )
                "Subscription Expired. Complete payment of ₹79 to unlock all features."
            } else {
                "Mandatory ₹1 Trial Setup required to activate SmartPOS features."
            }
            SessionAccessState.Locked(reason = message)
        }

        _accessState.value = result
        Log.d(TAG, "Subscription lock check result: $result (valid=$isSubscriptionValid, tier=${info.subscriptionTier}, mandate=${info.autoPayMandateStatus})")
        return result
    }

    fun isAccessGranted(context: Context, userUid: String = ""): Boolean {
        return verifyAndEnforceSubscriptionLock(context, userUid) is SessionAccessState.Granted
    }

    /**
     * Clears all session data and shared preferences upon logout.
     */
    fun clearSession(context: Context) {
        try {
            val smartPosPrefs = context.getSharedPreferences("smart_pos_prefs", Context.MODE_PRIVATE)
            smartPosPrefs.edit().clear().apply()

            val subPrefs = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
            subPrefs.edit().clear().apply()

            SubscriptionManager.clearLocalSubscriptionState(context)
            _accessState.value = SessionAccessState.Granted
            Log.d(TAG, "AppSessionManager session and preferences cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session in AppSessionManager: ${e.localizedMessage}")
        }
    }
}

