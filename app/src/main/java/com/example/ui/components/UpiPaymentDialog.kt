package com.example.ui.components

import androidx.compose.runtime.Composable

@Composable
fun UpiPaymentDialog(
    amount: Double,
    merchantUpiId: String,
    merchantName: String,
    onPaymentConfirmed: () -> Unit,
    onConfigureUpiClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    POSCheckoutDialog(
        amount = amount,
        merchantUpiId = merchantUpiId,
        merchantName = merchantName,
        onPaymentConfirmed = onPaymentConfirmed,
        onConfigureUpiClicked = onConfigureUpiClicked,
        onDismiss = onDismiss
    )
}

