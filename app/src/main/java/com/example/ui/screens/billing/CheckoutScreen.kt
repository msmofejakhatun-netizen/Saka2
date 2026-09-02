package com.example.ui.screens.billing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.ItemPayload
import com.example.data.db.CustomerEntity
import com.example.data.db.InvoiceEntity
import com.example.data.db.UserEntity
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.DarkSlateNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.viewmodel.BillingViewModel
import java.util.Locale

/**
 * Dedicated POS Checkout Screen & Dialog component supporting:
 * - Dynamic merchant identification and store phone extraction
 * - Accurate payment mode selection (Cash, UPI, Credit / Udhar)
 * - Automatic previous Udhar balance lookup and total outstanding calculations
 * - Instant Central WhatsApp invoice dispatch integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: BillingViewModel,
    currentUser: UserEntity?,
    onNavigateBack: () -> Unit,
    onCheckoutSuccess: (InvoiceEntity) -> Unit,
    onShowUpiQr: () -> Unit = {}
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsStateWithLifecycle(emptyList())
    val cleanCustomerPhone = viewModel.posCustomerMobile.replace("[^0-9]".toRegex(), "").takeLast(10)
    val matchedCustomer = customers.find {
        cleanCustomerPhone.isNotEmpty() && it.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10) == cleanCustomerPhone
    }

    val isCredit = viewModel.posPaymentMode.contains("Credit", ignoreCase = true) ||
            viewModel.posPaymentMode.contains("Udhar", ignoreCase = true)
    val previousUdhar = if (isCredit) matchedCustomer?.totalPendingBalance ?: 0.0 else 0.0
    val totalOutstanding = if (isCredit) previousUdhar + viewModel.posFinalTotal else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout & Payment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DeepNavy
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bill Summary Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSlateNavy),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bill Summary", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Items Total", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text("₹${String.format(Locale.US, "%.2f", viewModel.posSubtotal)}", color = Color.White, fontSize = 13.sp)
                            }
                            if (viewModel.posDiscountAmount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Discount", color = Color(0xFF10B981), fontSize = 13.sp)
                                    Text("-₹${String.format(Locale.US, "%.2f", viewModel.posDiscountAmount)}", color = Color(0xFF10B981), fontSize = 13.sp)
                                }
                            }
                            if (viewModel.posTaxAmount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("GST / Tax", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    Text("+₹${String.format(Locale.US, "%.2f", viewModel.posTaxAmount)}", color = Color.White, fontSize = 13.sp)
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0x33FFFFFF))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Grand Total", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("₹${String.format(Locale.US, "%.2f", viewModel.posFinalTotal)}", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                    }
                }

                // Payment Mode Selection
                item {
                    Text("Select Payment Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val modes = listOf(
                        Triple("Cash", Icons.Default.Money, Color(0xFF10B981)),
                        Triple("UPI", Icons.Default.QrCode2, Color(0xFF38BDF8)),
                        Triple("Credit (Udhar)", Icons.Default.AccountBalanceWallet, Color(0xFFF59E0B)),
                        Triple("Card", Icons.Default.CreditCard, Color(0xFFA78BFA))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { (mode, icon, tint) ->
                            val isSelected = viewModel.posPaymentMode.equals(mode, ignoreCase = true) ||
                                    (mode == "Credit (Udhar)" && viewModel.posPaymentMode.contains("Credit", ignoreCase = true))
                            PaymentModeOptionRow(
                                name = mode,
                                icon = icon,
                                iconTint = tint,
                                isSelected = isSelected,
                                onSelect = { viewModel.posPaymentMode = mode }
                            )
                        }
                    }
                }

                // Udhar Details Breakdown
                if (isCredit) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x22F59E0B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Udhar Khata Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Customer Name", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Text(viewModel.posCustomerName.ifBlank { "Walk-in Customer" }, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Customer Mobile", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Text(viewModel.posCustomerMobile.ifBlank { "Not provided" }, color = Color.White, fontSize = 12.sp)
                                }
                                HorizontalDivider(color = Color(0x33FFFFFF))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Previous Udhar Balance", color = Color(0xFFFCD34D), fontSize = 12.sp)
                                    Text("₹${String.format(Locale.US, "%.2f", previousUdhar)}", color = Color(0xFFFCD34D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Current Bill Amount", color = Color.White, fontSize = 12.sp)
                                    Text("₹${String.format(Locale.US, "%.2f", viewModel.posFinalTotal)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("New Total Outstanding", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("₹${String.format(Locale.US, "%.2f", totalOutstanding)}", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // WhatsApp Dispatch Option
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Send Invoice via WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        if (cleanCustomerPhone.length == 10) "Will send to +91 $cleanCustomerPhone" else "Enter valid 10-digit mobile to enable",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = viewModel.autoSendWhatsAppInvoice && cleanCustomerPhone.length == 10,
                                onCheckedChange = { checked ->
                                    viewModel.toggleAutoSendWhatsAppInvoice(checked, context)
                                },
                                enabled = cleanCustomerPhone.length == 10,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF25D366)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = {
                    if (viewModel.isEditingBill) {
                        viewModel.updatePOSInvoice { invoice ->
                            onCheckoutSuccess(invoice)
                        }
                    } else {
                        viewModel.generatePOSInvoice { invoice ->
                            onCheckoutSuccess(invoice)
                        }
                    }
                },
                enabled = !viewModel.isGeneratingPOSInvoice && viewModel.posCartItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("complete_checkout_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                if (viewModel.isGeneratingPOSInvoice) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Complete Sale (₹${String.format(Locale.US, "%.2f", viewModel.posFinalTotal)})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentModeOptionRow(
    name: String,
    icon: ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0x3310B981) else Color(0x1F1E295D),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) EmeraldGreen else Color(0x22FFFFFF)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconTint.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(name, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = EmeraldGreen, modifier = Modifier.size(20.dp))
            }
        }
    }
}
