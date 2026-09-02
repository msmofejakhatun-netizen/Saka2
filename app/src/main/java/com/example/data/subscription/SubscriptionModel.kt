package com.example.data.subscription

import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

data class SubscriptionModel(
    val planType: String = "FREE", // "MONTHLY", "ANNUAL", "TRIAL", "FREE"
    val planName: String = "Free Plan", // "Monthly Pro Plan (₹79/mo)", "Annual Pro Plan (₹799/yr)", "Free Trial (3 Days)"
    val status: String = "FREE", // "ACTIVE", "EXPIRED", "CANCELLED", "TRIAL_ACTIVE"
    val isProUser: Boolean = false,
    val amountPaid: Double = 0.0,
    val startDate: Long = 0L,
    val expiryDate: Long = 0L,
    val hasUsedTrial: Boolean = false,
    val mandateId: String = "",
    val gatewayProvider: String = "RAZORPAY",
    val paymentMethod: String = ""
) {
    val daysLeft: Long
        get() {
            val exp = if (expiryDate > 0L) expiryDate else 0L
            return if (exp > 0L) {
                ((exp - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
            } else 0L
        }

    val displayBadgeTitle: String
        get() = when (planType.uppercase()) {
            "MONTHLY", "MONTHLY_79_INR" -> "Monthly Pro (Active)"
            "ANNUAL", "ANNUAL_799_INR" -> "Annual Pro (Active)"
            "TRIAL", "TRIAL_1_INR", "TRIAL_3_DAYS_1_INR" -> "Free Trial (3 Days)"
            else -> if (isProUser) "Pro Active" else "Free Plan"
        }

    val displayDaysText: String
        get() = "$daysLeft Days Left"

    companion object {
        fun createMonthly(amountPaid: Double = 79.0, mandateId: String = "", gateway: String = "RAZORPAY", method: String = "UPI Autopay"): SubscriptionModel {
            val currentCalendar = Calendar.getInstance()
            val start = currentCalendar.timeInMillis
            currentCalendar.add(Calendar.DAY_OF_YEAR, 30) // 30 days validity
            val expiry = currentCalendar.timeInMillis

            return SubscriptionModel(
                planType = "MONTHLY",
                planName = "Monthly Pro Plan (₹79/mo)",
                status = "ACTIVE",
                isProUser = true,
                amountPaid = amountPaid,
                startDate = start,
                expiryDate = expiry,
                hasUsedTrial = true,
                mandateId = mandateId,
                gatewayProvider = gateway,
                paymentMethod = method
            )
        }

        fun createAnnual(amountPaid: Double = 799.0, mandateId: String = "", gateway: String = "RAZORPAY", method: String = "UPI Autopay"): SubscriptionModel {
            val currentCalendar = Calendar.getInstance()
            val start = currentCalendar.timeInMillis
            currentCalendar.add(Calendar.DAY_OF_YEAR, 365) // 365 days validity
            val expiry = currentCalendar.timeInMillis

            return SubscriptionModel(
                planType = "ANNUAL",
                planName = "Annual Pro Plan (₹799/yr)",
                status = "ACTIVE",
                isProUser = true,
                amountPaid = amountPaid,
                startDate = start,
                expiryDate = expiry,
                hasUsedTrial = true,
                mandateId = mandateId,
                gatewayProvider = gateway,
                paymentMethod = method
            )
        }

        fun createTrial(mandateId: String = "", gateway: String = "RAZORPAY", method: String = "UPI Mandate (₹1)"): SubscriptionModel {
            val currentCalendar = Calendar.getInstance()
            val start = currentCalendar.timeInMillis
            currentCalendar.add(Calendar.DAY_OF_YEAR, 3) // 3 days trial validity
            val expiry = currentCalendar.timeInMillis

            return SubscriptionModel(
                planType = "TRIAL",
                planName = "Free Trial (3 Days)",
                status = "TRIAL_ACTIVE",
                isProUser = true,
                amountPaid = 1.0,
                startDate = start,
                expiryDate = expiry,
                hasUsedTrial = true,
                mandateId = mandateId,
                gatewayProvider = gateway,
                paymentMethod = method
            )
        }
    }
}
