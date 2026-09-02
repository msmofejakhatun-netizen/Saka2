package com.example.ui.components

import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.db.UserEntity
import com.example.data.subscription.SubscriptionManager
import com.example.ui.screens.dashboard.BottomTab
import com.example.ui.theme.*
import com.example.util.WebUtils

/**
 * Reusable scrollable Navigation Drawer content with profile card,
 * navigation destinations, dynamic subscription status, and legal policy footer.
 */
@Composable
fun NavDrawerContent(
    currentUser: UserEntity?,
    currentTab: BottomTab,
    showProfileScreenOverlay: Boolean,
    showPrinterSettingsOverlay: Boolean,
    onTabSelected: (BottomTab) -> Unit,
    onProfileClick: () -> Unit,
    onPrinterSettingsClick: () -> Unit,
    onPaywallClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val subscriptionState by SubscriptionManager.subscriptionState.collectAsState()
    val isSubscribed = subscriptionState.isProUser ||
            subscriptionState.autoPayMandateStatus == "ACTIVE" ||
            subscriptionState.autoPayMandateStatus == "TRIAL_ACTIVE"

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color.White)
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp)
            .testTag("nav_drawer_scrollable_content")
    ) {
        // Red Header Profile Card Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VyaparRed)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (currentUser?.businessName?.take(1) ?: "K").uppercase(),
                            color = VyaparRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = currentUser?.businessName ?: "SmartPOS Business",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "👤 ${currentUser?.fullName ?: "Merchant"}",
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Category: ${currentUser?.category ?: "Retail Kirana"}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Navigation Items
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = if (currentTab == BottomTab.HOME && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextSecondary) },
            label = { Text("Home Dashboard", fontWeight = FontWeight.SemiBold, color = if (currentTab == BottomTab.HOME && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextPrimary) },
            selected = currentTab == BottomTab.HOME && !showProfileScreenOverlay && !showPrinterSettingsOverlay,
            onClick = { onTabSelected(BottomTab.HOME) },
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFFFEBEE)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_home")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.PointOfSale, contentDescription = "POS Bill", tint = if (currentTab == BottomTab.POS && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextSecondary) },
            label = { Text("POS Terminal & Billing", fontWeight = FontWeight.SemiBold, color = if (currentTab == BottomTab.POS && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextPrimary) },
            selected = currentTab == BottomTab.POS && !showProfileScreenOverlay && !showPrinterSettingsOverlay,
            onClick = { onTabSelected(BottomTab.POS) },
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFFFEBEE)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_pos")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Inventory", tint = if (currentTab == BottomTab.INVENTORY && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextSecondary) },
            label = { Text("Inventory & Stock Alert", fontWeight = FontWeight.SemiBold, color = if (currentTab == BottomTab.INVENTORY && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextPrimary) },
            selected = currentTab == BottomTab.INVENTORY && !showProfileScreenOverlay && !showPrinterSettingsOverlay,
            onClick = { onTabSelected(BottomTab.INVENTORY) },
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFFFEBEE)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_inventory")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.History, contentDescription = "History", tint = if (currentTab == BottomTab.HISTORY && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextSecondary) },
            label = { Text("Transaction History", fontWeight = FontWeight.SemiBold, color = if (currentTab == BottomTab.HISTORY && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextPrimary) },
            selected = currentTab == BottomTab.HISTORY && !showProfileScreenOverlay && !showPrinterSettingsOverlay,
            onClick = { onTabSelected(BottomTab.HISTORY) },
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFFFEBEE)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_history")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Udhar Khata", tint = if (currentTab == BottomTab.UDHAR && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextSecondary) },
            label = { Text("Udhar Khata (Credit Ledger)", fontWeight = FontWeight.SemiBold, color = if (currentTab == BottomTab.UDHAR && !showProfileScreenOverlay && !showPrinterSettingsOverlay) VyaparRed else VyaparTextPrimary) },
            selected = currentTab == BottomTab.UDHAR && !showProfileScreenOverlay && !showPrinterSettingsOverlay,
            onClick = { onTabSelected(BottomTab.UDHAR) },
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFFFEBEE)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_udhar")
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = VyaparBorder, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(10.dp))

        // Management & Profile Items
        Text(
            text = "MANAGEMENT & PROFILE",
            color = VyaparTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile", tint = VyaparDeepBlue) },
            label = { Text("Business Profile Settings", fontWeight = FontWeight.SemiBold, color = VyaparTextPrimary) },
            selected = showProfileScreenOverlay,
            onClick = onProfileClick,
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFE8EAF6)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_profile")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Print, contentDescription = "Thermal Printer", tint = VyaparDeepBlue) },
            label = { Text("Thermal Printer Setup", fontWeight = FontWeight.SemiBold, color = VyaparTextPrimary) },
            selected = showPrinterSettingsOverlay,
            onClick = onPrinterSettingsClick,
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFE8EAF6)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_printer_settings")
        )

        // Dynamic Pro Membership / Subscription Status Title
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = if (isSubscribed) Icons.Default.Verified else Icons.Default.WorkspacePremium,
                    contentDescription = "Subscription Status",
                    tint = if (isSubscribed) VyaparSuccess else VyaparGold
                )
            },
            label = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSubscribed) subscriptionState.displayBadgeTitle else "Pro Membership & ₹1 Trial",
                            fontWeight = FontWeight.Bold,
                            color = if (isSubscribed) VyaparSuccess else VyaparGold,
                            fontSize = 13.sp
                        )
                        if (isSubscribed && subscriptionState.effectiveExpiry > 0L) {
                            Text(
                                text = "${subscriptionState.daysLeft} days left",
                                color = VyaparTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Surface(
                        color = if (isSubscribed) VyaparSuccessLight else VyaparGoldLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "ACTIVE" else "PRO",
                            color = if (isSubscribed) VyaparSuccess else Color(0xFFB45309),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            selected = false,
            onClick = onPaywallClick,
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFF1F5F9)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_paywall")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = "Check for Updates", tint = VyaparDeepBlue) },
            label = { Text("Check App Updates", fontWeight = FontWeight.Bold, color = VyaparTextPrimary) },
            selected = false,
            onClick = onCheckUpdateClick,
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFF1F5F9)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_check_update")
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = VyaparBorder, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "LEGAL & POLICIES",
            color = VyaparTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Description, contentDescription = "Terms & Conditions", tint = VyaparTextSecondary) },
            label = { Text("Terms & Conditions", fontWeight = FontWeight.SemiBold, color = VyaparTextPrimary) },
            selected = false,
            onClick = onTermsClick,
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFF1F5F9)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_terms")
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Security, contentDescription = "Privacy Policy", tint = VyaparTextSecondary) },
            label = { Text("Privacy Policy", fontWeight = FontWeight.SemiBold, color = VyaparTextPrimary) },
            selected = false,
            onClick = onPrivacyClick,
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFFF1F5F9)),
            modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_privacy")
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = VyaparBorder, modifier = Modifier.padding(horizontal = 16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Logout, contentDescription = "Logout", tint = VyaparRed) },
            label = { Text("Logout", fontWeight = FontWeight.Bold, color = VyaparRed) },
            selected = false,
            onClick = onLogoutClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("drawer_item_logout")
        )

        // Footer Section with Terms, Privacy Policy & App Version
        HorizontalDivider(color = VyaparBorder, modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.labelMedium.copy(color = VyaparTextSecondary),
                modifier = Modifier
                    .clickable { onPrivacyClick() }
                    .padding(vertical = 4.dp)
                    .testTag("drawer_footer_privacy")
            )
            Text(
                text = "•",
                color = VyaparTextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.labelMedium.copy(color = VyaparTextSecondary),
                modifier = Modifier
                    .clickable { onTermsClick() }
                    .padding(vertical = 4.dp)
                    .testTag("drawer_footer_terms")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SmartPOS v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall.copy(color = VyaparTextSecondary),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("drawer_footer_version")
        )
    }
}

