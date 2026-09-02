package com.example.ui.screens.dashboard

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.HomeViewModel

/**
 * Modern, high-craft In-App Welcome & 3-Day Free Trial Onboarding Dialog.
 * Displayed automatically on the user's first login after signup.
 */
@Composable
fun WelcomeTrialOnboardingDialog(
    onDismiss: () -> Unit,
    onStartBilling: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle glow animation for the trial badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val badgePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_pulse"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(24.dp)
                .testTag("welcome_dialog_backdrop"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, VyaparBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("welcome_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(VyaparBg)
                                .testTag("welcome_dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = VyaparTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Hero Rocket Icon Badge
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(VyaparWarningLight)
                            .border(1.5.dp, VyaparRed.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🚀",
                            fontSize = 32.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Header: "Welcome to SmartPOS! 🚀"
                    Text(
                        text = "Welcome to SmartPOS!",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = VyaparTextPrimary,
                            letterSpacing = (-0.5).sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("welcome_header")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Badge: "3-DAY FREE TRIAL ACTIVE"
                    Surface(
                        color = VyaparSuccessLight,
                        shape = RoundedCornerShape(50.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            VyaparSuccess.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("welcome_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(VyaparSuccess)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3-DAY FREE TRIAL ACTIVE",
                                color = VyaparSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = "You have full access to Pro features: Unlimited Invoices, Thermal Printing, Udhar Khata, and Cloud Sync.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = VyaparTextSecondary,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .testTag("welcome_description")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Key Features List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VyaparBg)
                            .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FeatureRowItem(
                            icon = Icons.Default.ReceiptLong,
                            title = "Unlimited Invoices",
                            subtitle = "Lightning-fast GST & non-GST billing",
                            iconColor = VyaparRed
                        )
                        HorizontalDivider(color = VyaparDivider, thickness = 0.5.dp)
                        FeatureRowItem(
                            icon = Icons.Default.Print,
                            title = "ESC/POS Thermal Printing",
                            subtitle = "58mm & 80mm Bluetooth receipts",
                            iconColor = VyaparDeepBlue
                        )
                        HorizontalDivider(color = VyaparDivider, thickness = 0.5.dp)
                        FeatureRowItem(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "Digital Udhar Khata",
                            subtitle = "Customer credit & WhatsApp reminders",
                            iconColor = VyaparGold
                        )
                        HorizontalDivider(color = VyaparDivider, thickness = 0.5.dp)
                        FeatureRowItem(
                            icon = Icons.Default.CloudSync,
                            title = "Real-time Cloud Sync",
                            subtitle = "Offline-first database with auto-sync",
                            iconColor = VyaparSuccess
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action Button: "Start Billing"
                    Button(
                        onClick = onStartBilling,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VyaparRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_billing_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Start Billing",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Start",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Text
                    Text(
                        text = "Auto-renewal at ₹79/mo after trial unless cancelled.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VyaparTextMuted,
                            fontSize = 11.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome_secondary_text")
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = VyaparTextPrimary,
                    fontSize = 13.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = VyaparTextSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * HomeDashboardScreen composable wrapper.
 * Integrates HomeViewModel to observe `has_seen_welcome_dialog` and trigger the Onboarding Dialog.
 */
@Composable
fun HomeDashboardScreen(
    billingViewModel: BillingViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    onNavigateToPOS: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showWelcomeDialog by homeViewModel.showWelcomeDialog.collectAsState()
    val subscriptionState by billingViewModel.subscriptionState.collectAsState()
    val isSubscriptionValid = com.example.util.AuthGuard.isSubscriptionValid(subscriptionState)

    // Trigger local preference check on launch
    LaunchedEffect(Unit) {
        homeViewModel.checkWelcomeStatus(context)
    }

    if (showWelcomeDialog) {
        WelcomeTrialOnboardingDialog(
            onDismiss = {
                homeViewModel.dismissWelcomeDialog(context)
            },
            onStartBilling = {
                homeViewModel.dismissWelcomeDialog(context) {
                    if (isSubscriptionValid) {
                        onNavigateToPOS()
                    } else {
                        billingViewModel.openPaywall()
                    }
                }
            }
        )
    }
}
