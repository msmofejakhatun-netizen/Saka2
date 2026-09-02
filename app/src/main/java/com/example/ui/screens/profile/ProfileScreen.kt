package com.example.ui.screens.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.PremiumGradientBackground
import com.example.ui.components.PremiumLoadingState
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Immediately load user profile from Firestore / Room upon screen launch
    LaunchedEffect(Unit) {
        viewModel.loadUserProfile(context = context)
    }

    val selectableCategories = remember {
        listOf(
            "Kirana / Grocery",
            "Pharmacy / Medical",
            "Garments / Clothing",
            "Restaurant / Cafe / Food",
            "General Store / Retail",
            "Electronics & Mobile",
            "Hardware & Sanitary",
            "Automobile & Spares"
        )
    }

    var showCategoryMenu by remember { mutableStateOf(false) }

    PremiumGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Business Profile Settings",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.testTag("profile_screen_title")
            )

            Text(
                text = "Manage your merchant details & billing identity",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Dynamic Subscription Membership Badge Card
            val subscriptionState by com.example.data.subscription.SubscriptionManager.subscriptionState.collectAsState()
            val isSubValid = com.example.util.AuthGuard.isSubscriptionValid(subscriptionState)
            val subBadgeTitle = when (subscriptionState.planType.uppercase()) {
                "MONTHLY", "MONTHLY_79_INR" -> "Monthly Pro (Active)"
                "ANNUAL", "ANNUAL_799_INR" -> "Annual Pro (Active)"
                "TRIAL", "TRIAL_1_INR" -> "Free Trial (3 Days)"
                else -> if (subscriptionState.isProUser) "Pro Plan (Active)" else "Free Plan"
            }
            val subDaysText = "${subscriptionState.daysLeft} Days Left"

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSubValid) Color(0x2210B981) else Color(0x22EF4444)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .border(
                        1.dp,
                        if (isSubValid) Color(0x4410B981) else Color(0x44EF4444),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("profile_subscription_status_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isSubValid) EmeraldGreen.copy(alpha = 0.2f) else Color(0x33EF4444),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSubValid) Icons.Default.WorkspacePremium else Icons.Default.Cancel,
                                contentDescription = "Plan Badge",
                                tint = if (isSubValid) EmeraldGreen else Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = subBadgeTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isSubValid) subDaysText else "Plan Expired - Renew to unlock",
                                color = if (isSubValid) EmeraldLight else Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        color = if (isSubValid) EmeraldGreen else Color(0xFFEF4444),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isSubValid) "ACTIVE" else "RENEW",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_form_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Validation Errors
                    AnimatedVisibility(
                        visible = uiState.errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        uiState.errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .testTag("profile_error_text")
                            )
                        }
                    }

                    // Full Name Field (Two-way UI State binding)
                    OutlinedTextField(
                        value = viewModel.fullName.ifBlank { uiState.fullName },
                        onValueChange = { viewModel.updateFullName(it) },
                        label = { Text("Full Name", color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Icon",
                                tint = EmeraldGreen
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedLabelColor = EmeraldGreen,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0x0AFFFFFF),
                            unfocusedContainerColor = Color(0x05FFFFFF)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_full_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Business Name Field (Two-way UI State binding)
                    OutlinedTextField(
                        value = viewModel.businessName.ifBlank { uiState.businessName },
                        onValueChange = { viewModel.updateBusinessName(it) },
                        label = { Text("Business Name", color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = "Business Icon",
                                tint = EmeraldGreen
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedLabelColor = EmeraldGreen,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0x0AFFFFFF),
                            unfocusedContainerColor = Color(0x05FFFFFF)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_business_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Business Category Dropdown Selection
                    ExposedDropdownMenuBox(
                        expanded = showCategoryMenu,
                        onExpandedChange = { showCategoryMenu = !showCategoryMenu },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = viewModel.businessCategory.ifBlank { uiState.businessCategory },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Business Category", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = "Category Icon",
                                    tint = EmeraldGreen
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Arrow Drop Down",
                                    tint = EmeraldLight,
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedLabelColor = EmeraldGreen,
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0x0AFFFFFF),
                                unfocusedContainerColor = Color(0x05FFFFFF)
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("profile_category_select")
                        )

                        ExposedDropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        ) {
                            selectableCategories.forEach { categoryName ->
                                DropdownMenuItem(
                                    text = { Text(categoryName, color = Color.White) },
                                    onClick = {
                                        viewModel.updateBusinessCategory(categoryName)
                                        showCategoryMenu = false
                                    },
                                    modifier = Modifier.testTag("profile_category_option_${categoryName.lowercase().replace(" ", "_").replace("/", "")}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Merchant UPI ID Field (Two-way UI State binding)
                    OutlinedTextField(
                        value = viewModel.upiId.ifBlank { uiState.upiId },
                        onValueChange = { viewModel.updateUpiId(it) },
                        label = { Text("Merchant UPI ID / VPA", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. 9876543210@paytm") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "UPI Icon",
                                tint = EmeraldGreen
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedLabelColor = EmeraldGreen,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0x0AFFFFFF),
                            unfocusedContainerColor = Color(0x05FFFFFF)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_upi_id_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-send WhatsApp Invoice Toggle Setting
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
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0x2225D366), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "WhatsApp",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Auto-send WhatsApp Bill",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Automatically open WhatsApp with receipt upon completing sale",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = viewModel.autoSendWhatsAppInvoice,
                                onCheckedChange = { checked ->
                                    viewModel.updateAutoSendWhatsAppInvoice(checked, context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = EmeraldGreen,
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0x33FFFFFF)
                                ),
                                modifier = Modifier.testTag("profile_auto_whatsapp_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit / Update Button
                    Button(
                        onClick = {
                            viewModel.saveUserProfile(context = context) {
                                onSaveSuccess()
                            }
                        },
                        enabled = !uiState.isSaving && !viewModel.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("profile_update_submit")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF8B5CF6), // Electric Violet
                                            Color(0xFF10B981)  // Emerald Green
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isSaving || viewModel.isSaving) {
                                PremiumLoadingState(text = "Updating Profile...")
                            } else {
                                Text(
                                    text = "UPDATE PROFILE",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
