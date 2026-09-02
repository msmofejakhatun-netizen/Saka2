package com.example.ui.screens.paywall

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firebase.FirebaseManager
import com.example.data.subscription.PaymentGatewayConfig
import com.example.data.subscription.PaymentGatewayHandler
import com.example.data.subscription.SubscriptionInfo
import com.example.data.subscription.SubscriptionManager
import com.example.ui.components.GlassmorphicCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.SubscriptionViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    billingViewModel: BillingViewModel? = null,
    subscriptionViewModel: SubscriptionViewModel? = null,
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit = onBack,
    isMandatory: Boolean = false,
    lockReason: String? = null
) {
    val subVm: SubscriptionViewModel = subscriptionViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel()
    val billVm: BillingViewModel? = billingViewModel

    val subscriptionState by subVm.subscriptionState.collectAsState()
    val isProUser = subscriptionState.isProUser ||
            subscriptionState.autoPayMandateStatus == "ACTIVE" ||
            subscriptionState.autoPayMandateStatus == "TRIAL_ACTIVE"

    BackHandler(enabled = true) {
        billVm?.closePaywall()
        if (isProUser) {
            onNavigateToDashboard()
        } else if (!isMandatory) {
            onBack()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color(0xFF0F172A)
    ) {
        SubscriptionScreenContent(
            subscriptionViewModel = subVm,
            billingViewModel = billVm,
            onClose = onBack,
            onNavigateToDashboard = onNavigateToDashboard,
            isMandatory = isMandatory,
            lockReason = lockReason
        )
    }
}

@Composable
fun SubscriptionScreenContent(
    subscriptionViewModel: SubscriptionViewModel,
    billingViewModel: BillingViewModel? = null,
    onClose: () -> Unit,
    onNavigateToDashboard: () -> Unit = onClose,
    isMandatory: Boolean = false,
    lockReason: String? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val subscriptionState by subscriptionViewModel.subscriptionState.collectAsState()
    val hasUsedTrial by subscriptionViewModel.hasUsedTrial.collectAsState()
    val isSuccessDialogVisible by subscriptionViewModel.isSuccessDialogVisible.collectAsState()
    val uiState by subscriptionViewModel.uiState.collectAsState()

    val currentAuthUser = FirebaseManager.auth?.currentUser
    val userMobile = currentAuthUser?.phoneNumber?.takeLast(10)
        ?: billingViewModel?.currentUser?.value?.mobileNumber
        ?: "9999999999"
    val userId = currentAuthUser?.uid ?: userMobile

    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            subscriptionViewModel.checkTrialEligibility(userId)
        }
    }

    // Active subscription flag: Pro user or active mandate strictly verified by AuthGuard
    val isSubscribed = com.example.util.AuthGuard.isSubscriptionValid(subscriptionState)
    val canShowTrial = uiState.showTrialPlan && !hasUsedTrial && !subscriptionState.hasUsedTrial

    // Default selected plan (Monthly if trial was used or expired, otherwise 3-Day trial)
    var selectedPlan by remember(hasUsedTrial, canShowTrial, isSubscribed) {
        mutableStateOf(
            if (!canShowTrial || !isSubscribed) PaymentGatewayHandler.SubscriptionPlan.MONTHLY_79_INR
            else PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR
        )
    }

    var showPaymentBottomSheet by remember { mutableStateOf(false) }
    var isProcessingPayment by remember { mutableStateOf(false) }
    var isRestoringSubscription by remember { mutableStateOf(false) }
    var showCancelMandateConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1333),
                        Color(0xFF0F172A),
                        Color(0xFF1E1035)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation & Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isMandatory || isSubscribed) {
                    IconButton(
                        onClick = {
                            billingViewModel?.closePaywall()
                            if (isSubscribed) {
                                onNavigateToDashboard()
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier
                            .background(Color(0x22FFFFFF), CircleShape)
                            .testTag("subscription_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0x33EF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFFEF4444))
                    }
                }

                Surface(
                    color = if (isSubscribed) Color(0x3310B981) else Color(0x33F59E0B),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSubscribed) EmeraldGreen else GoldYellow)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSubscribed) Icons.Default.CheckCircle else Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isSubscribed) EmeraldGreen else GoldYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSubscribed) {
                                if (subscriptionState.subscriptionTier == "TRIAL_1_INR" || subscriptionState.autoPayMandateStatus == "TRIAL_ACTIVE") {
                                    "3-DAY TRIAL ACTIVE"
                                } else {
                                    "VIP PRO ACTIVE"
                                }
                            } else if (isMandatory) {
                                "MANDATORY PRO ACCESS"
                            } else {
                                "PRO MEMBERSHIP"
                            },
                            color = if (isSubscribed) EmeraldGreen else GoldYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isMandatory && !isSubscribed) {
                Surface(
                    color = Color(0x33EF4444),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = lockReason ?: "Subscription Expired. Renew to continue using SmartPOS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // CONDITIONAL RENDERING: Active Subscriber View vs Inactive / Plan Selection View
            if (isSubscribed) {
                // =========================================================================
                // 1. ACTIVE SUBSCRIBER DETAILS DASHBOARD
                // =========================================================================
                ActiveSubscriptionDetailsCard(
                    info = subscriptionState,
                    onUpgradeToAnnual = {
                        selectedPlan = PaymentGatewayHandler.SubscriptionPlan.ANNUAL_799_INR
                        if (activity != null) {
                            PaymentGatewayHandler.launchRazorpayCheckout(
                                activity = activity,
                                plan = selectedPlan,
                                userEmail = "$userMobile@smartpos.com",
                                userPhone = userMobile
                            )
                        } else {
                            showPaymentBottomSheet = true
                        }
                    },
                    onRequestCancelMandate = {
                        showCancelMandateConfirmDialog = true
                    },
                    onContinueToBilling = {
                        billingViewModel?.closePaywall()
                        onNavigateToDashboard()
                    }
                )
            } else {
                // =========================================================================
                // 2. INACTIVE / EXPIRED / NEW SUBSCRIBER PLAN SELECTION VIEW
                // =========================================================================
                InactivePlanSelectionView(
                    hasUsedTrial = hasUsedTrial || !canShowTrial,
                    showTrialPlan = canShowTrial,
                    selectedPlan = selectedPlan,
                    isRestoring = isRestoringSubscription,
                    onSelectPlan = { selectedPlan = it },
                    onRestoreSubscription = {
                        isRestoringSubscription = true
                        subscriptionViewModel.restoreSubscription(
                            context = context,
                            mobileNumberOrUid = userId
                        ) { success, msg ->
                            isRestoringSubscription = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                billingViewModel?.closePaywall()
                                onNavigateToDashboard()
                            }
                        }
                    },
                    onStartSubscription = {
                        if (activity != null) {
                            PaymentGatewayHandler.launchRazorpayCheckout(
                                activity = activity,
                                plan = selectedPlan,
                                userEmail = "$userMobile@smartpos.com",
                                userPhone = userMobile
                            )
                        } else {
                            showPaymentBottomSheet = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // AutoPay Cancel Confirmation Dialog
        if (showCancelMandateConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCancelMandateConfirmDialog = false },
                containerColor = Color(0xFF1E293B),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCBD5E1),
                icon = {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = AccentPink,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text("Cancel AutoPay Mandate?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "Are you sure you want to stop automatic recurring payments on ${subscriptionState.gatewayProvider}?",
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Auto-debit will be cancelled immediately.\n• You will retain full Pro benefits until your current billing cycle expires.\n• You can re-enable anytime.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCancelMandateConfirmDialog = false
                            subscriptionViewModel.cancelSubscription(
                                context = context,
                                userUid = userId,
                                onComplete = { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                    ) {
                        Text("Confirm Cancellation", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showCancelMandateConfirmDialog = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Keep AutoPay")
                    }
                }
            )
        }

        // Payment / Mandate Bottom Sheet Modal
        if (showPaymentBottomSheet) {
            PaymentGatewayBottomSheet(
                plan = selectedPlan,
                isProcessing = isProcessingPayment,
                onConfirmMandate = { provider, selectedApp, userVpa ->
                    if (provider == PaymentGatewayConfig.GatewayProvider.RAZORPAY && activity != null) {
                        showPaymentBottomSheet = false
                        PaymentGatewayHandler.launchRazorpayCheckout(
                            activity = activity,
                            plan = selectedPlan,
                            userEmail = "$userMobile@smartpos.com",
                            userPhone = userMobile
                        )
                    } else {
                        isProcessingPayment = true
                        PaymentGatewayHandler.initiateSubscriptionMandate(
                            context = context,
                            plan = selectedPlan,
                            provider = provider,
                            selectedApp = selectedApp,
                            userVpa = userVpa,
                            userMobile = userMobile,
                            userUid = userId,
                            onResult = { result ->
                                isProcessingPayment = false
                                showPaymentBottomSheet = false
                                if (result.isSuccess) {
                                    val mandateRef = if (result.phonePeMandateId.isNotBlank()) result.phonePeMandateId else result.razorpaySubscriptionId
                                    Toast.makeText(
                                        context,
                                        "🎉 Mandate Approved! Pro Activated via ${result.provider}. Ref: $mandateRef",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    subscriptionViewModel.showSuccessDialog()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error: ${result.errorMessage ?: "Payment authorization failed"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                },
                onDismiss = { if (!isProcessingPayment) showPaymentBottomSheet = false }
            )
        }

        // Success Dialog Overlay (strictly triggered only on new active checkout completions)
        if (isSuccessDialogVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable {
                        subscriptionViewModel.dismissSuccessDialog()
                        billingViewModel?.closePaywall()
                        onNavigateToDashboard()
                    }
                    .testTag("subscription_success_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(EmeraldGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Subscription Activated! 🎉",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All Pro POS Features are unlocked!",
                        color = EmeraldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            subscriptionViewModel.dismissSuccessDialog()
                            billingViewModel?.closePaywall()
                            onNavigateToDashboard()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Billing", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modern "My Subscription & AutoPay Status" dashboard view for active subscribers and trial users.
 */
@Composable
fun ActiveSubscriptionDetailsCard(
    info: SubscriptionInfo,
    onUpgradeToAnnual: () -> Unit,
    onRequestCancelMandate: () -> Unit,
    onContinueToBilling: () -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val isTrial = info.planType.equals("TRIAL", ignoreCase = true) || info.subscriptionTier == "TRIAL_1_INR" || info.autoPayMandateStatus == "TRIAL_ACTIVE"
    val isAnnual = info.planType.equals("ANNUAL", ignoreCase = true) || info.subscriptionTier == "ANNUAL_799_INR"
    val isMonthly = info.planType.equals("MONTHLY", ignoreCase = true) || info.subscriptionTier == "MONTHLY_79_INR"

    // Plan Title
    val planTitle = when {
        isTrial -> "3-Day Free Trial (₹1 Mandate Active)"
        isAnnual -> "Annual Pro Pass (₹799 / Year)"
        isMonthly -> "Monthly Pro Plan (₹79 / Month)"
        info.planName.isNotBlank() && info.planName != "Free Plan" -> info.planName
        else -> "Monthly Pro Plan (₹79 / Month)"
    }

    // Expiry & Countdown calculations
    val expiryTimestamp = if (info.expiryDate > 0L) info.expiryDate else info.subscriptionExpiryDate
    val daysLeft = if (expiryTimestamp > 0L) ((expiryTimestamp - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(0L) else 0L
    val remainingMs = (expiryTimestamp - now).coerceAtLeast(0L)
    val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingMs) % 24

    val countdownText = when {
        expiryTimestamp <= 0L -> "Lifetime Active"
        remainingMs <= 0L -> "Cycle Expired (Pending Auto-Debit)"
        isTrial -> {
            if (daysLeft > 0) "$daysLeft Days Left in Free Trial"
            else "$remainingHours Hours Left in Free Trial"
        }
        else -> {
            if (daysLeft > 0) "$daysLeft Days Left (Active)"
            else "$remainingHours Hours Remaining in Cycle"
        }
    }

    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val nextBillingDateStr = if (expiryTimestamp > 0L) sdf.format(Date(expiryTimestamp)) else "Not Applicable"

    // AutoPay status description
    val autoDebitText = when (info.autoPayMandateStatus) {
        "TRIAL_ACTIVE" -> "Active (₹79/month auto-debit after trial on $nextBillingDateStr)"
        "ACTIVE" -> {
            if (isAnnual) "Active (₹799/year auto-debit on $nextBillingDateStr)"
            else "Active (₹79/month auto-debit on $nextBillingDateStr)"
        }
        "CANCELLED" -> "Cancelled (Auto-debit stopped. Access active until $nextBillingDateStr)"
        "FAILED" -> "Debit Failed (Please re-authorize mandate)"
        else -> "Not Configured"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF047857), Color(0xFF10B981), Color(0xFF065F46))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = GoldYellow,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SmartPOS Pro Active",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = planTitle,
                                color = Color(0xFFD1FAE5),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time remaining badge
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = GoldYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = countdownText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        if (isTrial) {
                            Surface(
                                color = GoldYellow,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "FREE TRIAL",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subscription & AutoPay Status Details Card
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "AutoPay & Settlement Details",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Current Plan Row
                DetailRow(
                    label = "Current Plan",
                    value = planTitle,
                    valueColor = EmeraldLight
                )

                Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 10.dp))

                // AutoPay Mandate Status Row
                DetailRow(
                    label = "AutoPay Status",
                    value = if (info.autoPayMandateStatus == "ACTIVE" || info.autoPayMandateStatus == "TRIAL_ACTIVE") "Active" else info.autoPayMandateStatus,
                    valueColor = if (info.autoPayMandateStatus == "CANCELLED") AccentPink else EmeraldGreen
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = autoDebitText,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 10.dp))

                // Next Billing Date Row
                DetailRow(
                    label = if (isTrial) "Trial Expiry / 1st Debit Date" else "Next Renewal / Expiry Date",
                    value = nextBillingDateStr,
                    valueColor = Color.White
                )

                Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 10.dp))

                // Gateway Provider
                DetailRow(
                    label = "Payment Gateway",
                    value = "${info.gatewayProvider} (UPI AutoPay)",
                    valueColor = Color.White
                )

                Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 10.dp))

                // Mandate Reference ID with Copy button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gateway Mandate Ref ID",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = info.autoPayMandateId.ifBlank { "MND-RZP-928472" },
                            color = GoldYellow,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            val clip = ClipData.newPlainText("Mandate ID", info.autoPayMandateId.ifBlank { "MND-RZP-928472" })
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
                            Toast.makeText(context, "Mandate Ref ID copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Mandate ID",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Divider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 10.dp))

                // Settlement Bank Account
                DetailRow(
                    label = "Direct Merchant Settlement",
                    value = info.settlementAccount.ifBlank { PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED },
                    valueColor = EmeraldLight
                )

                Spacer(modifier = Modifier.height(14.dp))

                // RBI e-Mandate Protection Note
                Surface(
                    color = Color(0x1A10B981),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RBI e-Mandate Compliant. Bank notification SMS is sent 24h before any auto-debit.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Upgrade to Annual button (if on Trial or Monthly)
            if (!isAnnual) {
                Button(
                    onClick = onUpgradeToAnnual,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("subscription_upgrade_annual_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Unspecified),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Upgrade to Annual @ ₹799 (Save 15%)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // Manage / Cancel AutoPay Mandate button
            if (info.autoPayMandateStatus == "ACTIVE" || info.autoPayMandateStatus == "TRIAL_ACTIVE") {
                OutlinedButton(
                    onClick = onRequestCancelMandate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("subscription_cancel_autopay_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPink),
                    border = BorderStroke(1.dp, AccentPink.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage / Cancel AutoPay", fontWeight = FontWeight.Bold)
                }
            }

            // Back to Dashboard / Start Billing
            Button(
                onClick = onContinueToBilling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("subscription_start_billing_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(Icons.Default.PointOfSale, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start POS Billing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Plan Selection view for inactive, new, or expired users.
 * Respects `hasUsedTrial` and `showTrialPlan`: completely hides 3-Day trial if `hasUsedTrial == true` or `showTrialPlan == false`.
 */
@Composable
fun InactivePlanSelectionView(
    hasUsedTrial: Boolean,
    showTrialPlan: Boolean = !hasUsedTrial,
    selectedPlan: PaymentGatewayHandler.SubscriptionPlan,
    isRestoring: Boolean = false,
    onSelectPlan: (PaymentGatewayHandler.SubscriptionPlan) -> Unit,
    onRestoreSubscription: (() -> Unit)? = null,
    onStartSubscription: () -> Unit
) {
    val canDisplayTrial = showTrialPlan && !hasUsedTrial

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pro Header Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart POS Pro",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Razorpay & PhonePe Auto-Pay Integration",
                            color = Color(0xFFF1F5F9),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (canDisplayTrial) {
                        "Start 3-Day Free Trial @ ₹1 Mandate. Direct settlement into Merchant Bank Account (${PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED}). Billed via Razorpay Subscriptions / PhonePe UPI Autopay."
                    } else {
                        "Upgrade to Pro for unlimited billing, thermal receipt printing, WhatsApp ledger reminders & instant merchant settlements."
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Plan Selection Header
        Text(
            text = "Choose Your Subscription Plan",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Option 1: 3-Day Trial @ ₹1 (Only visible if canDisplayTrial == true)
        if (canDisplayTrial) {
            PlanCardOption(
                title = "3-Day Trial @ ₹1",
                badgeText = "🔥 RAZORPAY / PHONEPE MANDATE",
                priceText = "₹1 Setup Fee",
                subtext = "₹1 Mandate Authorization → 3 Days Free Trial → Then ₹79/Month Auto-Debit",
                isSelected = selectedPlan == PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR,
                onClick = { onSelectPlan(PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR) }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Option 2: Monthly Pro Plan
        PlanCardOption(
            title = "Monthly Pro Plan",
            badgeText = if (!canDisplayTrial) "POPULAR" else "REGULAR RECURRING",
            priceText = "₹79 / month",
            subtext = "Instant Bank Settlement. Recurring monthly debit via Razorpay/PhonePe. Cancel anytime.",
            isSelected = selectedPlan == PaymentGatewayHandler.SubscriptionPlan.MONTHLY_79_INR,
            onClick = { onSelectPlan(PaymentGatewayHandler.SubscriptionPlan.MONTHLY_79_INR) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Option 3: Annual Pro Pass
        PlanCardOption(
            title = "Annual Pro Pass",
            badgeText = "BEST VALUE (SAVE 15%)",
            priceText = "₹799 / year",
            subtext = "Equivalent to ₹66/month. Direct settlement into linked merchant bank account.",
            isSelected = selectedPlan == PaymentGatewayHandler.SubscriptionPlan.ANNUAL_799_INR,
            onClick = { onSelectPlan(PaymentGatewayHandler.SubscriptionPlan.ANNUAL_799_INR) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features Checklist Grid
        Text(
            text = "What You Get with Pro Access",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProFeatureRow(
                icon = Icons.Default.ReceiptLong,
                title = "Unlimited POS Invoices & Bluetooth Printing",
                desc = "Print thermal receipts, invoices & bills without daily limits."
            )
            ProFeatureRow(
                icon = Icons.Default.AccountBalance,
                title = "Direct Merchant Settlement",
                desc = "Instant bank payouts straight to ${PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED}."
            )
            ProFeatureRow(
                icon = Icons.Default.CloudSync,
                title = "Cloud Backup & Multi-Device Udhar Khata",
                desc = "Real-time sync to Firebase Cloud, restore data on any device safely."
            )
            ProFeatureRow(
                icon = Icons.Default.Send,
                title = "Automated WhatsApp Udhar Reminders",
                desc = "Send instant payment links and debt receipts to customers in 1 click."
            )
            ProFeatureRow(
                icon = Icons.Default.Security,
                title = "Razorpay & PhonePe UPI Autopay Security",
                desc = "Protected by RBI e-mandate rules with 24h prior notification before debit."
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Subscription CTA Button
        Button(
            onClick = onStartSubscription,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("subscription_start_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Unspecified),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(EmeraldGreen, Color(0xFF059669))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (selectedPlan) {
                            PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR -> "Start 3-Day Trial @ ₹1 Mandate"
                            PaymentGatewayHandler.SubscriptionPlan.MONTHLY_79_INR -> "Subscribe Monthly @ ₹79"
                            PaymentGatewayHandler.SubscriptionPlan.ANNUAL_799_INR -> "Get Annual Pass @ ₹799"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Restore Purchase Button
        OutlinedButton(
            onClick = { onRestoreSubscription?.invoke() },
            enabled = !isRestoring,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("restore_subscription_button"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0x5510B981)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x1810B981),
                contentColor = EmeraldLight
            )
        ) {
            if (isRestoring) {
                CircularProgressIndicator(
                    color = EmeraldLight,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restoring Subscription from Cloud...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = "Restore Subscription",
                    tint = EmeraldLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Already Paid? Restore Subscription",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Compliance & RBI Guarantee
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Direct Razorpay / PhonePe Payment Gateway. RBI e-Mandate Compliant.",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PlanCardOption(
    title: String,
    badgeText: String,
    priceText: String,
    subtext: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0x3310B981) else Color(0x221E293B)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) EmeraldGreen else Color(0x33FFFFFF),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Surface(
                    color = if (isSelected) EmeraldGreen else Color(0xFF334155),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = priceText,
                    color = EmeraldLight,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtext,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ProFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x11FFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0x3310B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = desc,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewayBottomSheet(
    plan: PaymentGatewayHandler.SubscriptionPlan,
    isProcessing: Boolean,
    onConfirmMandate: (
        provider: PaymentGatewayConfig.GatewayProvider,
        selectedApp: String,
        userVpa: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(PaymentGatewayConfig.GatewayProvider.RAZORPAY) }
    var selectedApp by remember { mutableStateOf("PhonePe") }
    var userVpa by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header bar
            Surface(
                color = Color(0x3310B981),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RAZORPAY / PHONEPE RECURRING GATEWAY",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (plan == PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR) "Authorize ₹1 Mandate Setup" else "Confirm ${plan.title}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Middle Selection Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x221E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (plan == PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR) "Mandate Authorization Fee:" else "Price:",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                            Text(
                                plan.introductoryPrice,
                                color = EmeraldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Selected Plan:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            Text(plan.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recurring Auto-Debit:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            Text(
                                text = if (plan == PaymentGatewayHandler.SubscriptionPlan.TRIAL_3_DAYS_1_INR) "₹79/mo (After 3-Day Trial)" else "${plan.recurringPrice}/${plan.billingCycle}",
                                color = GoldYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Direct Merchant Payout:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(PaymentGatewayConfig.SETTLEMENT_ACCOUNT_MASKED, color = EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "1. Select Payment Gateway",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedProvider == PaymentGatewayConfig.GatewayProvider.RAZORPAY,
                        onClick = { selectedProvider = PaymentGatewayConfig.GatewayProvider.RAZORPAY },
                        label = { Text("Razorpay Subscriptions") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedProvider == PaymentGatewayConfig.GatewayProvider.PHONEPE,
                        onClick = { selectedProvider = PaymentGatewayConfig.GatewayProvider.PHONEPE },
                        label = { Text("PhonePe Autopay") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "2. Select UPI App / One-Tap App",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                val upiApps = listOf("PhonePe", "Google Pay", "Paytm", "BHIM", "Custom VPA Input")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upiApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedApp == app) Color(0x3310B981) else Color(0x11FFFFFF),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedApp = app }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedApp == app,
                                onClick = { selectedApp = app },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = app,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (selectedApp == "Custom VPA Input") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = userVpa,
                        onValueChange = { userVpa = it },
                        label = { Text("Enter UPI ID (e.g. merchant@okhdfcbank)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onConfirmMandate(selectedProvider, selectedApp, userVpa) },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authenticating Mandate...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize Mandate via $selectedApp", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
