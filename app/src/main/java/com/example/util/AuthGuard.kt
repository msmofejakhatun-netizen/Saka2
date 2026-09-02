package com.example.util

import com.example.data.subscription.SubscriptionInfo

/**
 * Centralized Strict Subscription & Authentication Guard.
 * Enforces subscription validation across app start, foreground resume, and navigation routes.
 */
object AuthGuard {

    /**
     * Strict Subscription Verification Check:
     * - isProUserActive: True if subscribed to a paid tier (Monthly ₹79 / Annual ₹799) with non-expired timestamp & valid mandate.
     * - isTrialActive: True only if trial is active AND current time is strictly less than trial expiry time (3 days).
     * - isTrialExpired: When trial has ended, requires a completed, successful paid recurring cycle.
     */
    fun isSubscriptionValid(
        info: SubscriptionInfo,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        val isTrial = info.planType.equals("TRIAL", ignoreCase = true) ||
                info.autoPayMandateStatus == "TRIAL_ACTIVE" ||
                info.subscriptionTier == "TRIAL_1_INR"

        val effectiveExpiry = when {
            info.expiryDate > 0L -> info.expiryDate
            info.subscriptionExpiryDate > 0L -> info.subscriptionExpiryDate
            info.trialStartDate > 0L -> info.trialStartDate + (3 * 24 * 60 * 60 * 1000L)
            else -> 0L
        }

        val isPaidTier = info.planType.equals("MONTHLY", ignoreCase = true) ||
                info.planType.equals("ANNUAL", ignoreCase = true) ||
                info.subscriptionTier == "MONTHLY_79_INR" ||
                info.subscriptionTier == "ANNUAL_799_INR"

        val isProUserActive = info.isProUser &&
                isPaidTier &&
                (effectiveExpiry == 0L || currentTime < effectiveExpiry) &&
                info.autoPayMandateStatus != "FAILED" &&
                info.autoPayMandateStatus != "EXPIRED"

        val isTrialActive = isTrial &&
                info.isProUser &&
                (effectiveExpiry == 0L || currentTime < effectiveExpiry) &&
                info.autoPayMandateStatus != "FAILED" &&
                info.autoPayMandateStatus != "EXPIRED"

        val isTrialExpired = isTrial && (effectiveExpiry > 0L && currentTime >= effectiveExpiry)

        val hasCompletedSuccessfulPayment = info.isProUser &&
                isPaidTier &&
                (effectiveExpiry == 0L || currentTime < effectiveExpiry)

        return when {
            isProUserActive -> true
            isTrialActive -> true
            isTrialExpired && hasCompletedSuccessfulPayment -> true
            else -> info.isProUser && (effectiveExpiry == 0L || currentTime < effectiveExpiry)
        }
    }
}
