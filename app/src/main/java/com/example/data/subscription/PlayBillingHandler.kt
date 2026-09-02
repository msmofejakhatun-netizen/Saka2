package com.example.data.subscription

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

object PlayBillingHandler {

    enum class BillingPlan(
        val planId: String,
        val title: String,
        val introductoryPrice: String,
        val introductoryPeriodDays: Int,
        val recurringPrice: String,
        val billingCycle: String
    ) {
        TRIAL_3_DAYS_1_INR(
            planId = "kirana_pro_trial_1inr",
            title = "3-Day Free Trial Mandate",
            introductoryPrice = "₹1",
            introductoryPeriodDays = 3,
            recurringPrice = "₹79",
            billingCycle = "1 Month"
        ),
        MONTHLY_79_INR(
            planId = "kirana_pro_monthly_79",
            title = "Pro Monthly Plan",
            introductoryPrice = "₹79",
            introductoryPeriodDays = 0,
            recurringPrice = "₹79",
            billingCycle = "1 Month"
        ),
        ANNUAL_799_INR(
            planId = "kirana_pro_annual_799",
            title = "Pro Annual Pass",
            introductoryPrice = "₹799",
            introductoryPeriodDays = 0,
            recurringPrice = "₹799",
            billingCycle = "1 Year"
        )
    }

    data class MandateVerificationResult(
        val isSuccess: Boolean,
        val transactionId: String,
        val mandateRef: String,
        val paymentMethod: String,
        val errorMessage: String? = null
    )

    fun executeMandatePaymentFlow(
        context: Context,
        plan: BillingPlan,
        paymentApp: String, // "Google Pay", "PhonePe", "Paytm", "BHIM", "Play Billing"
        upiId: String = "",
        userUid: String = "",
        onResult: (MandateVerificationResult) -> Unit
    ) {
        // Simulate real payment gateway API roundtrip / Play Billing purchase flow
        Handler(Looper.getMainLooper()).postDelayed({
            val txId = "TXN-" + UUID.randomUUID().toString().take(10).uppercase()
            val mandateRef = "MND-" + (10000000..99999999).random()

            val success = true
            if (success) {
                // Activate in manager
                if (plan == BillingPlan.TRIAL_3_DAYS_1_INR) {
                    SubscriptionManager.activateTrialMandate(
                        context = context,
                        paymentMethod = "$paymentApp ${if (upiId.isNotBlank()) "($upiId)" else ""}",
                        userUid = userUid,
                        onComplete = { _, _ -> }
                    )
                } else {
                    SubscriptionManager.activateSubscriptionPlan(
                        context = context,
                        tier = if (plan == BillingPlan.ANNUAL_799_INR) "ANNUAL_799_INR" else "MONTHLY_79_INR",
                        paymentMethod = "$paymentApp ${if (upiId.isNotBlank()) "($upiId)" else ""}",
                        userUid = userUid,
                        onComplete = { _, _ -> }
                    )
                }

                onResult(
                    MandateVerificationResult(
                        isSuccess = true,
                        transactionId = txId,
                        mandateRef = mandateRef,
                        paymentMethod = paymentApp
                    )
                )
            } else {
                onResult(
                    MandateVerificationResult(
                        isSuccess = false,
                        transactionId = "",
                        mandateRef = "",
                        paymentMethod = paymentApp,
                        errorMessage = "Payment authorization cancelled by user or bank server timeout."
                    )
                )
            }
        }, 1200)
    }
}
