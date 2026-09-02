package com.example.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.PremiumGradientBackground
import com.example.ui.components.PremiumLoadingState
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.viewmodel.BillingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    viewModel: BillingViewModel,
    onSetupSuccess: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Immediately load existing user profile from Firestore / Local DB upon screen launch
    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
    }

    val activeCategories by viewModel.categories.collectAsStateWithLifecycle()
    val enabledCategories = remember(activeCategories) {
        activeCategories.filter { it.isEnabled }
    }

    val requiredBusinessCategories = remember {
        listOf(
            "Kirana / Grocery",
            "Pharmacy / Medical",
            "Garments / Clothing",
            "Restaurant / Cafe / Food",
            "General Store / Retail"
        )
    }

    val selectableCategories = remember(enabledCategories) {
        val extraCategories = enabledCategories.map { it.name }.filter { it.isNotBlank() }
        (requiredBusinessCategories + extraCategories).distinct()
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
                text = "Complete Profile",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.testTag("profile_setup_title")
            )

            Text(
                text = "Set up your business identity to start billing",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_setup_form_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Validation Errors
                    AnimatedVisibility(
                        visible = viewModel.profileError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        viewModel.profileError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .testTag("profile_setup_error")
                            )
                        }
                    }

                    // Full Name Field
                    OutlinedTextField(
                        value = viewModel.profileFullName,
                        onValueChange = { viewModel.profileFullName = it },
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

                    // Business Name Field
                    OutlinedTextField(
                        value = viewModel.profileBusinessName,
                        onValueChange = { viewModel.profileBusinessName = it },
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

                    // Category Dropdown Selection
                    ExposedDropdownMenuBox(
                        expanded = showCategoryMenu,
                        onExpandedChange = { showCategoryMenu = !showCategoryMenu },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = viewModel.profileCategory,
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
                                        viewModel.profileCategory = categoryName
                                        showCategoryMenu = false
                                    },
                                    modifier = Modifier.testTag("profile_category_option_${categoryName.lowercase().replace(" ", "_").replace("/", "")}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Merchant UPI ID Field
                    OutlinedTextField(
                        value = viewModel.profileUpiId,
                        onValueChange = { viewModel.profileUpiId = it },
                        label = { Text("Merchant UPI ID / VPA", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("e.g. 9876543210@paytm") },
                        leadingIcon = {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.AccountBalanceWallet,
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

                    Spacer(modifier = Modifier.height(32.dp))

                    // Submit Button
                    Button(
                        onClick = { viewModel.completeProfileSetup(onSetupSuccess) },
                        enabled = !viewModel.isSavingProfile,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("profile_setup_submit")
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
                            if (viewModel.isSavingProfile) {
                                PremiumLoadingState(text = "Saving...")
                            } else {
                                Text(
                                    text = "SAVE & CONTINUE",
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
