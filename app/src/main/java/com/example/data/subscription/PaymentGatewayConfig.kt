package com.example.data.subscription

import android.content.Context

object PaymentGatewayConfig {
    // Razorpay Merchant Credentials
    var razorpayKeyId: String = "rzp_test_TK5VKyAOfxOCGJ"
    var razorpayKeySecret: String = "VatUmvBxEprYVwjZcdC1a0m6"

    // PhonePe Merchant Credentials
    var phonePeMerchantId: String = "MERCHANT_KIRANA_PRO_UAT"
    var phonePeSaltKey: String = "099eb0cd-02fe-4e0a-a10d-b42f4439e728"
    var phonePeSaltIndex: Int = 1

    // Merchant Profile & Settlement Account Details
    const val MERCHANT_NAME = "Smart POS Pro Solutions"
    const val MERCHANT_VPA = "kiranapos@ybl"
    const val SETTLEMENT_BANK = "HDFC Bank Ltd"
    const val SETTLEMENT_ACCOUNT_MASKED = "A/C XX8902"

    // Gateway Provider Modes
    enum class GatewayProvider {
        RAZORPAY,
        PHONEPE,
        DIRECT_UPI_MANDATE
    }

    fun getActiveGatewayName(provider: GatewayProvider): String {
        return when (provider) {
            GatewayProvider.RAZORPAY -> "Razorpay Subscriptions (UPI Autopay)"
            GatewayProvider.PHONEPE -> "PhonePe Recurring Mandate SDK"
            GatewayProvider.DIRECT_UPI_MANDATE -> "Direct UPI Intent Autopay"
        }
    }

    /**
     * Client-side success callback handler for Razorpay or PhonePe SDK payment completion.
     * Performs immediate optimistic update to Firestore users/{userId}/subscription/current and local session state.
     */
    fun handlePaymentSuccess(
        context: Context,
        userUid: String,
        razorpayPaymentId: String,
        paymentData: Any? = null,
        planType: String? = null,
        amountPaid: Double? = null,
        onComplete: (() -> Unit)? = null
    ) {
        SubscriptionManager.onPaymentSuccess(
            context = context,
            userUid = userUid,
            razorpayPaymentId = razorpayPaymentId,
            paymentData = paymentData,
            planType = planType,
            amountPaid = amountPaid,
            onComplete = onComplete
        )
    }
}
