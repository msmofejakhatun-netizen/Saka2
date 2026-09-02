package com.example.ui.screens.billing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.InvoiceEntity
import com.example.data.db.ProductEntity
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.LooseQuantityDialog
import com.example.ui.components.MerchantUpiSettingsDialog
import com.example.ui.components.UpiPaymentDialog
import androidx.compose.material.icons.filled.QrCodeScanner
import android.widget.Toast
import com.example.ui.theme.VyaparRed
import com.example.ui.theme.VyaparRedDark
import com.example.ui.theme.VyaparBg
import com.example.ui.theme.VyaparSurface
import com.example.ui.theme.VyaparTextPrimary
import com.example.ui.theme.VyaparTextSecondary
import com.example.ui.theme.VyaparSuccess
import com.example.ui.theme.VyaparBorder
import com.example.ui.theme.VyaparBlue
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.POSCartItem
import com.example.util.KiranaUnitUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBillScreen(
    viewModel: BillingViewModel,
    onNavigateBack: () -> Unit
) {
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val subscriptionState by viewModel.subscriptionState.collectAsState()
    val isSubscriptionValid = com.example.util.AuthGuard.isSubscriptionValid(subscriptionState)
    var showExpiredBillingDialog by remember { mutableStateOf(false) }
    val activeBusinessType = remember(currentUser) { com.example.util.BusinessCategoryUtils.getBusinessType(currentUser) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.syncSettingsFromPrefs(context)
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showSuccessReceiptDialog by remember { mutableStateOf(false) }
    var generatedInvoiceForReceipt by remember { mutableStateOf<InvoiceEntity?>(null) }

    var selectedProductForLooseQty by remember { mutableStateOf<ProductEntity?>(null) }
    var editingCartItemQuantity by remember { mutableStateOf<POSCartItem?>(null) }

    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showUpiQrPaymentDialog by remember { mutableStateOf(false) }
    var showMerchantUpiSettingsDialog by remember { mutableStateOf(false) }
    var showCustomerPickerModal by remember { mutableStateOf(false) }
    var showAddNewCustomerModal by remember { mutableStateOf(false) }
    var isCustomerFieldsExpanded by remember { mutableStateOf(false) }

    var showCartReviewModal by remember { mutableStateOf(false) }
    var showPaymentModal by remember { mutableStateOf(false) }
    var showBluetoothPrinterDialog by remember { mutableStateOf(false) }

    val categoriesList = remember(products) {
        val set = products.map { it.category }.filter { it.isNotBlank() }.toSet()
        listOf("All") + set.toList()
    }

    val filteredProducts = remember(products, searchQuery, selectedCategoryFilter) {
        products.filter { prod ->
            val matchesCategory = selectedCategoryFilter == "All" || prod.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    prod.name.contains(searchQuery, ignoreCase = true) ||
                    prod.category.contains(searchQuery, ignoreCase = true) ||
                    (prod.barcode.isNotBlank() && prod.barcode.contains(searchQuery, ignoreCase = true))
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "POS Billing & Invoice",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("pos_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBluetoothPrinterDialog = true },
                        modifier = Modifier.testTag("pos_bluetooth_printer_topbar_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Thermal Printer", tint = Color.White)
                    }
                    if (viewModel.posCartItems.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearPOSCart() },
                            modifier = Modifier.testTag("pos_clear_cart_button")
                        ) {
                            Text("Clear Cart", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VyaparRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier.testTag("pos_top_bar")
            )
        },
        containerColor = VyaparBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VyaparBg)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Subscription Expired Warning Banner
                if (!isSubscriptionValid) {
                    item {
                        Card(
                            onClick = { viewModel.openPaywall() },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
                                .padding(top = 4.dp)
                                .testTag("pos_subscription_expired_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Expired",
                                        tint = VyaparRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Subscription Expired",
                                            color = VyaparTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Complete payment of ₹79 to unlock all features.",
                                            color = VyaparRed,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.openPaywall() },
                                    colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp).testTag("pos_renew_subscription_btn")
                                ) {
                                    Text("Pay ₹79", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Editing Bill Banner
                if (viewModel.isEditingBill) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                                .padding(top = 4.dp)
                                .testTag("editing_bill_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editing Bill",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Editing Bill #${viewModel.editingInvoice?.id ?: ""}",
                                            color = VyaparTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Modify items/customer and tap Update Bill",
                                            color = Color(0xFF92400E),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { viewModel.cancelEditingBill() },
                                    modifier = Modifier.testTag("cancel_edit_bill_button")
                                ) {
                                    Text("Cancel Edit", color = VyaparRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 1. Customer Details Section
                item {
                    val showCustomerFormFields = isCustomerFieldsExpanded ||
                            viewModel.posPaymentMode.contains("Credit", ignoreCase = true) ||
                            viewModel.posPaymentMode.contains("Udhar", ignoreCase = true) ||
                            (viewModel.posCustomerName.isNotBlank() && viewModel.posCustomerName != "Walk-in Customer") ||
                            viewModel.posCustomerMobile.isNotBlank()

                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                            .testTag("pos_customer_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Customer",
                                        tint = VyaparRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (viewModel.posCustomerName.isBlank() || viewModel.posCustomerName == "Walk-in Customer") "Walk-in Cash Sale" else viewModel.posCustomerName,
                                            color = VyaparTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (viewModel.posCustomerMobile.isNotBlank()) {
                                            Text(
                                                text = "Mob: ${viewModel.posCustomerMobile}",
                                                color = VyaparTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        } else if (!showCustomerFormFields) {
                                            Text(
                                                text = "Default POS Mode • Fast Checkout",
                                                color = VyaparTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (showCustomerFormFields && viewModel.posPaymentMode != "Credit (Udhar)") {
                                        TextButton(
                                            onClick = {
                                                viewModel.posCustomerName = "Walk-in Customer"
                                                viewModel.posCustomerMobile = ""
                                                isCustomerFieldsExpanded = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Reset Walk-in", color = VyaparRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else if (!showCustomerFormFields) {
                                        Surface(
                                            onClick = {
                                                showCustomerPickerModal = true
                                                isCustomerFieldsExpanded = true
                                            },
                                            color = Color(0xFFFEE2E2),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("pos_open_customer_search_btn")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = VyaparRed, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Customer (+)", color = VyaparRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Restaurant / Cafe Specific Layout: Table No. & Order Type clean and visible
                            if (activeBusinessType == com.example.util.BusinessType.RESTAURANT) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = viewModel.posTableNumber,
                                        onValueChange = { viewModel.posTableNumber = it },
                                        label = { Text("Table No. / Counter", color = VyaparTextSecondary, fontSize = 11.sp) },
                                        placeholder = { Text("Table 4", color = VyaparTextSecondary) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = VyaparRed,
                                            unfocusedBorderColor = VyaparBorder,
                                            focusedTextColor = VyaparTextPrimary,
                                            unfocusedTextColor = VyaparTextPrimary,
                                            focusedContainerColor = VyaparSurface,
                                            unfocusedContainerColor = VyaparSurface
                                        ),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pos_table_number_input")
                                    )

                                    val orderTypes = listOf("Dine-in", "Takeaway", "Delivery")
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text("Order Type:", color = VyaparTextSecondary, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            orderTypes.forEach { type ->
                                                val isSel = viewModel.posOrderType.equals(type, ignoreCase = true) || (viewModel.posOrderType.isEmpty() && type == "Dine-in")
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSel) VyaparRed else Color(0xFFF1F5F9))
                                                        .clickable { viewModel.posOrderType = type }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = type,
                                                        color = if (isSel) Color.White else VyaparTextPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Customer Form Input Fields (Expanded Mode or Udhar Mode)
                            if (showCustomerFormFields) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = viewModel.posCustomerName,
                                        onValueChange = { viewModel.posCustomerName = it },
                                        label = { Text("Customer Name *", color = VyaparTextSecondary, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(18.dp)) },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                showCustomerPickerModal = true
                                                isCustomerFieldsExpanded = true
                                            }) {
                                                Icon(Icons.Default.Search, contentDescription = "Search Customer", tint = VyaparRed, modifier = Modifier.size(20.dp))
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = VyaparRed,
                                            unfocusedBorderColor = VyaparBorder,
                                            focusedTextColor = VyaparTextPrimary,
                                            unfocusedTextColor = VyaparTextPrimary,
                                            focusedContainerColor = VyaparSurface,
                                            unfocusedContainerColor = VyaparSurface
                                        ),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("pos_customer_name_input")
                                    )

                                    OutlinedTextField(
                                        value = viewModel.posCustomerMobile,
                                        onValueChange = { viewModel.posCustomerMobile = it },
                                        label = { Text("Mobile (Opt)", color = VyaparTextSecondary, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(18.dp)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = VyaparRed,
                                            unfocusedBorderColor = VyaparBorder,
                                            focusedTextColor = VyaparTextPrimary,
                                            unfocusedTextColor = VyaparTextPrimary,
                                            focusedContainerColor = VyaparSurface,
                                            unfocusedContainerColor = VyaparSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pos_customer_mobile_input")
                                    )
                                }

                                if (activeBusinessType == com.example.util.BusinessType.PHARMACY) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = viewModel.posDoctorName,
                                            onValueChange = { viewModel.posDoctorName = it },
                                            label = { Text("Doctor Name (Opt)", color = VyaparTextSecondary, fontSize = 11.sp) },
                                            placeholder = { Text("Dr. Sharma") },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = VyaparRed,
                                                unfocusedBorderColor = VyaparBorder,
                                                focusedTextColor = VyaparTextPrimary,
                                                unfocusedTextColor = VyaparTextPrimary,
                                                focusedContainerColor = VyaparSurface,
                                                unfocusedContainerColor = VyaparSurface
                                            ),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("pos_doctor_name_input")
                                        )

                                        OutlinedTextField(
                                            value = viewModel.posPatientInfo,
                                            onValueChange = { viewModel.posPatientInfo = it },
                                            label = { Text("Patient Name/Age (Opt)", color = VyaparTextSecondary, fontSize = 11.sp) },
                                            placeholder = { Text("Rahul / 32Y") },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = VyaparRed,
                                                unfocusedBorderColor = VyaparBorder,
                                                focusedTextColor = VyaparTextPrimary,
                                                unfocusedTextColor = VyaparTextPrimary,
                                                focusedContainerColor = VyaparSurface,
                                                unfocusedContainerColor = VyaparSurface
                                            ),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("pos_patient_info_input")
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = {
                                        showCustomerPickerModal = true
                                        isCustomerFieldsExpanded = true
                                    },
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pos_customer_picker_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Search Saved Customers or Register New (+)", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Icon(Icons.Default.Search, contentDescription = null, tint = VyaparTextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Product Picker & Inventory Search Engine
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = "Products",
                                    tint = VyaparBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select Products from Inventory",
                                    color = VyaparTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${filteredProducts.size} items available",
                                    color = VyaparTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Compact Modern Search Bar & Barcode Scanner Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            "Search products...",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = VyaparTextSecondary),
                                            maxLines = 1
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search Icon",
                                            tint = VyaparRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear Search",
                                                    tint = VyaparTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VyaparRed,
                                        unfocusedBorderColor = VyaparBorder,
                                        focusedContainerColor = VyaparSurface,
                                        unfocusedContainerColor = VyaparSurface,
                                        focusedTextColor = VyaparTextPrimary,
                                        unfocusedTextColor = VyaparTextPrimary,
                                        cursorColor = VyaparRed
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("pos_product_search_input")
                                )

                                Surface(
                                    onClick = { showBarcodeScanner = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = VyaparRed,
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("pos_scan_barcode_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = "Scan Barcode",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Scan",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Category Filter Chips
                            if (categoriesList.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(categoriesList) { cat ->
                                        val isSelected = selectedCategoryFilter == cat
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategoryFilter = cat },
                                            label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = VyaparRed,
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0xFFF1F5F9),
                                                labelColor = VyaparTextPrimary
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                }
                            }

                            // Product Cards List
                            if (filteredProducts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No products found matching '$searchQuery'", color = VyaparTextSecondary, fontSize = 12.sp)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredProducts.take(15).forEach { product ->
                                        ProductPOSRow(
                                            product = product,
                                            onAddToCart = {
                                                if (KiranaUnitUtils.isLooseUnit(product.unit)) {
                                                    selectedProductForLooseQty = product
                                                } else {
                                                    viewModel.addToPOSCart(product)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }

            // Step 1 Bottom Sticky Bar: View Cart Summary Action
            Surface(
                color = VyaparSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .border(1.dp, VyaparBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (viewModel.posCartItems.isEmpty()) "Cart is Empty" else "${viewModel.posCartItems.sumOf { it.quantity.toInt() }} Items in Cart",
                            color = VyaparTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", viewModel.posSubtotal)}",
                            color = VyaparRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSubscriptionValid) {
                                showExpiredBillingDialog = true
                            } else {
                                showCartReviewModal = true
                            }
                        },
                        enabled = viewModel.posCartItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VyaparRed,
                            disabledContainerColor = Color(0xFFE2E8F0),
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("pos_view_cart_button")
                    ) {
                        Icon(imageVector = Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Cart (${viewModel.posCartItems.size}) ->",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Subscription Expired Alert Dialog
    if (showExpiredBillingDialog) {
        AlertDialog(
            onDismissRequest = { showExpiredBillingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = VyaparRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Subscription Expired",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = VyaparTextPrimary
                        )
                    )
                }
            },
            text = {
                Text(
                    text = "Your trial has expired. Please complete payment of ₹79 to continue billing and unlock all SmartPOS features.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = VyaparTextSecondary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExpiredBillingDialog = false
                        viewModel.openPaywall()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Complete Payment (₹79)", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpiredBillingDialog = false }) {
                    Text("Cancel", color = VyaparTextSecondary)
                }
            },
            containerColor = VyaparSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Success Digital Receipt Dialog Modal
    if (showSuccessReceiptDialog && generatedInvoiceForReceipt != null) {
        val invoice = generatedInvoiceForReceipt!!
        val formattedDate = remember(invoice.timestamp) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.timestamp))
        }

        AlertDialog(
            onDismissRequest = {
                showSuccessReceiptDialog = false
                onNavigateBack()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = VyaparSuccess, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Invoice Generated!", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Receipt #${invoice.firestoreId.take(8).ifBlank { invoice.id }}", color = VyaparTextSecondary, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Details
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(currentUser?.businessName ?: "Billing Store", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Customer: ${invoice.customerName} ${if (invoice.customerMobile.isNotBlank()) "(${invoice.customerMobile})" else ""}", color = VyaparTextSecondary, fontSize = 11.sp)
                            Text("Date: $formattedDate", color = VyaparTextSecondary, fontSize = 11.sp)
                            Text("Payment Mode: ${invoice.paymentMode}", color = VyaparSuccess, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Purchased Items Summary
                    Text("Purchased Items Summary:", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = invoice.itemsSummary.ifBlank { "${invoice.itemsCount} items billed" },
                        color = VyaparTextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(1.dp, VyaparBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .fillMaxWidth()
                    )

                    // Financial Summary Breakdown
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SummaryLineItem("Subtotal", "₹${String.format(Locale.US, "%.2f", invoice.subtotal)}")
                        if (invoice.discountAmount > 0) {
                            SummaryLineItem("Discount", "-₹${String.format(Locale.US, "%.2f", invoice.discountAmount)}", isNegative = true)
                        }
                        if (invoice.taxAmount > 0) {
                            SummaryLineItem("Tax", "+₹${String.format(Locale.US, "%.2f", invoice.taxAmount)}")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paid Amount", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "₹${String.format(Locale.US, "%.2f", invoice.amount)}",
                                color = VyaparSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // PDF & Thermal Printer Action Engine
                    val localContext = androidx.compose.ui.platform.LocalContext.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showBluetoothPrinterDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("receipt_print_thermal_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print Bluetooth Thermal Receipt", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = {
                                val pdf = com.example.util.InvoicePdfHelper.generateInvoicePdf(
                                    context = localContext,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName,
                                    merchantMobile = currentUser?.mobileNumber
                                )
                                com.example.util.InvoicePdfHelper.printInvoicePdf(localContext, pdf)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp).border(1.dp, VyaparBorder, RoundedCornerShape(8.dp)).testTag("receipt_print_pdf_button")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = VyaparTextPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print Invoice PDF (A4)", color = VyaparTextPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val custPhone = invoice.customerMobile.ifBlank { "" }
                                if (custPhone.isNotBlank()) {
                                    val sent = com.example.util.WhatsAppInvoiceHelper.sendWhatsAppInvoice(
                                        context = localContext,
                                        customerPhone = custPhone,
                                        invoice = invoice,
                                        businessName = currentUser?.businessName ?: "SmartPOS Store"
                                    )
                                    if (!sent) {
                                        val pdf = com.example.util.InvoicePdfHelper.generateInvoicePdf(
                                            context = localContext,
                                            invoice = invoice,
                                            businessName = currentUser?.businessName,
                                            merchantMobile = currentUser?.mobileNumber
                                        )
                                        com.example.util.InvoicePdfHelper.shareInvoicePdfWhatsApp(
                                            context = localContext,
                                            pdfFile = pdf,
                                            invoice = invoice,
                                            businessName = currentUser?.businessName
                                        )
                                    }
                                } else {
                                    val pdf = com.example.util.InvoicePdfHelper.generateInvoicePdf(
                                        context = localContext,
                                        invoice = invoice,
                                        businessName = currentUser?.businessName,
                                        merchantMobile = currentUser?.mobileNumber
                                    )
                                    com.example.util.InvoicePdfHelper.shareInvoicePdfWhatsApp(
                                        context = localContext,
                                        pdfFile = pdf,
                                        invoice = invoice,
                                        businessName = currentUser?.businessName
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("receipt_whatsapp_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share via WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessReceiptDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("receipt_start_new_sale_button")
                ) {
                    Text("Start New Sale", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessReceiptDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Dashboard", color = VyaparTextSecondary)
                }
            },
            containerColor = VyaparSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, VyaparBorder, RoundedCornerShape(16.dp))
        )
    }

    // Modal Loose Quantity Dialog when adding a loose item (+ Add clicked on Kg, Gm, Ltr, Ml)
    selectedProductForLooseQty?.let { product ->
        LooseQuantityDialog(
            product = product,
            initialQuantity = 1.0,
            onDismiss = { selectedProductForLooseQty = null },
            onConfirm = { qty ->
                viewModel.addToPOSCart(product, qty)
                selectedProductForLooseQty = null
            }
        )
    }

    // Modal Loose Quantity Dialog when editing quantity of an existing item in cart
    editingCartItemQuantity?.let { cartItem ->
        LooseQuantityDialog(
            product = cartItem.product,
            initialQuantity = cartItem.quantity,
            onDismiss = { editingCartItemQuantity = null },
            onConfirm = { qty ->
                viewModel.updatePOSCartQuantity(cartItem.product, qty)
                editingCartItemQuantity = null
            }
        )
    }

    // Modal Barcode Scanner Dialog for fast barcode lookup
    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            onBarcodeScanned = { scannedCode ->
                showBarcodeScanner = false
                val matched = viewModel.findProductByBarcode(scannedCode)
                if (matched != null) {
                    if (KiranaUnitUtils.isLooseUnit(matched.unit)) {
                        selectedProductForLooseQty = matched
                    } else {
                        viewModel.addToPOSCart(matched, 1.0)
                        Toast.makeText(context, "Added '${matched.name}' to cart!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    searchQuery = scannedCode
                    Toast.makeText(context, "Product not found. Filtered search for barcode: $scannedCode", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    // Modal Dynamic UPI QR Code Payment Dialog
    if (showUpiQrPaymentDialog) {
        UpiPaymentDialog(
            amount = viewModel.posFinalTotal,
            merchantUpiId = currentUser?.upiId?.ifBlank { "store@upi" } ?: "store@upi",
            merchantName = currentUser?.merchantName?.ifBlank { currentUser?.businessName } ?: "Kirana Store",
            onPaymentConfirmed = {
                showUpiQrPaymentDialog = false
                if (viewModel.isEditingBill) {
                    viewModel.updatePOSInvoice { updated ->
                        generatedInvoiceForReceipt = updated
                        showSuccessReceiptDialog = true
                    }
                } else {
                    viewModel.generatePOSInvoice { generatedInvoice ->
                        generatedInvoiceForReceipt = generatedInvoice
                        showSuccessReceiptDialog = true
                    }
                }
            },
            onConfigureUpiClicked = {
                showUpiQrPaymentDialog = false
                showMerchantUpiSettingsDialog = true
            },
            onDismiss = { showUpiQrPaymentDialog = false }
        )
    }

    // Modal Merchant UPI Settings Dialog
    if (showMerchantUpiSettingsDialog) {
        MerchantUpiSettingsDialog(
            initialUpiId = currentUser?.upiId ?: "",
            initialMerchantName = currentUser?.merchantName ?: currentUser?.businessName ?: "",
            onSave = { upiId, merchantName ->
                viewModel.updateMerchantUpiSettings(upiId, merchantName)
                showMerchantUpiSettingsDialog = false
            },
            onDismiss = { showMerchantUpiSettingsDialog = false }
        )
    }

    // Modal Customer Picker Dialog
    if (showCustomerPickerModal) {
        POSCustomerPickerModalDialog(
            customers = customers,
            onSelectCustomer = { cust ->
                viewModel.posCustomerName = cust.name
                viewModel.posCustomerMobile = cust.mobileNumber
                isCustomerFieldsExpanded = true
                showCustomerPickerModal = false
                Toast.makeText(context, "Selected customer: ${cust.name}", Toast.LENGTH_SHORT).show()
            },
            onSelectWalkIn = {
                viewModel.posCustomerName = "Walk-in Customer"
                viewModel.posCustomerMobile = ""
                isCustomerFieldsExpanded = false
                showCustomerPickerModal = false
                Toast.makeText(context, "Selected Walk-in Customer", Toast.LENGTH_SHORT).show()
            },
            onAddNewCustomerClick = {
                showCustomerPickerModal = false
                showAddNewCustomerModal = true
            },
            onDismiss = { showCustomerPickerModal = false }
        )
    }

    // Modal Add New Customer Dialog
    if (showAddNewCustomerModal) {
        AddNewCustomerModalDialog(
            onSaveCustomer = { name, mobile ->
                viewModel.addQuickCustomer(name, mobile) { savedCust ->
                    viewModel.posCustomerName = savedCust.name
                    viewModel.posCustomerMobile = savedCust.mobileNumber
                    isCustomerFieldsExpanded = true
                    showAddNewCustomerModal = false
                }
            },
            onDismiss = { showAddNewCustomerModal = false }
        )
    }

    if (showCartReviewModal) {
        CartReviewModalDialog(
            viewModel = viewModel,
            onProceedToPayment = {
                if (!isSubscriptionValid) {
                    showCartReviewModal = false
                    showExpiredBillingDialog = true
                } else {
                    showCartReviewModal = false
                    showPaymentModal = true
                }
            },
            onChangeCustomerClick = {
                showCustomerPickerModal = true
            },
            onDismiss = { showCartReviewModal = false },
            onEditQuantity = { cartItem -> editingCartItemQuantity = cartItem }
        )
    }

    if (showPaymentModal) {
        PaymentAndCheckoutModalDialog(
            viewModel = viewModel,
            currentUser = currentUser,
            onShowUpiQr = { showUpiQrPaymentDialog = true },
            onCompleteSale = {
                if (!isSubscriptionValid) {
                    showPaymentModal = false
                    showExpiredBillingDialog = true
                    return@PaymentAndCheckoutModalDialog
                }
                if (viewModel.isEditingBill) {
                    viewModel.updatePOSInvoice { updated ->
                        showPaymentModal = false
                        showCartReviewModal = false
                        generatedInvoiceForReceipt = updated
                        showSuccessReceiptDialog = true
                    }
                } else {
                    viewModel.generatePOSInvoice { generatedInvoice ->
                        showPaymentModal = false
                        showCartReviewModal = false
                        generatedInvoiceForReceipt = generatedInvoice
                        showSuccessReceiptDialog = true
                    }
                }
            },
            onBackToCart = {
                showPaymentModal = false
                showCartReviewModal = true
            },
            onDismiss = { showPaymentModal = false }
        )
    }

    if (showBluetoothPrinterDialog) {
        com.example.ui.components.BluetoothPrinterDialog(
            invoice = generatedInvoiceForReceipt ?: viewModel.lastGeneratedInvoice,
            businessName = currentUser?.businessName ?: "Kirana Store",
            upiId = currentUser?.upiId ?: "merchant@upi",
            isGstModeInitial = viewModel.isGstInvoiceMode,
            onGstModeToggle = { viewModel.isGstInvoiceMode = it },
            onDismiss = { showBluetoothPrinterDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartReviewModalDialog(
    viewModel: BillingViewModel,
    onProceedToPayment: () -> Unit,
    onChangeCustomerClick: () -> Unit,
    onDismiss: () -> Unit,
    onEditQuantity: (POSCartItem) -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            color = VyaparSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFEE2E2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Step 2/3: Review Cart & Discounts", color = VyaparTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${viewModel.posCartItems.size} products added to bill", color = VyaparTextSecondary, fontSize = 11.sp)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("cart_review_close_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = VyaparTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Customer Summary Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = viewModel.posCustomerName.ifBlank { "Walk-in Customer" },
                                            color = VyaparTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        if (viewModel.posCustomerMobile.isNotBlank()) {
                                            Text(viewModel.posCustomerMobile, color = VyaparTextSecondary, fontSize = 11.sp)
                                        }
                                        if (viewModel.posDoctorName.isNotBlank() || viewModel.posPatientInfo.isNotBlank()) {
                                            Text(
                                                text = listOfNotNull(
                                                    viewModel.posDoctorName.takeIf { it.isNotBlank() }?.let { "Dr. $it" },
                                                    viewModel.posPatientInfo.takeIf { it.isNotBlank() }?.let { "Patient: $it" }
                                                ).joinToString(" · "),
                                                color = VyaparTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = onChangeCustomerClick,
                                    modifier = Modifier.testTag("cart_review_change_customer_btn")
                                ) {
                                    Text("Change", color = VyaparRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Itemized Product List Section
                    item {
                        Text(
                            text = "ITEMIZED PRODUCTS",
                            color = VyaparTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (viewModel.posCartItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = VyaparTextSecondary, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Your cart is empty", color = VyaparTextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        items(viewModel.posCartItems, key = { it.product.id }) { cartItem ->
                            CartItemRow(
                                cartItem = cartItem,
                                onEditQuantity = { onEditQuantity(cartItem) },
                                onIncrease = {
                                    val step = if (KiranaUnitUtils.isLooseUnit(cartItem.product.unit)) 0.25 else 1.0
                                    viewModel.updatePOSCartQuantity(cartItem.product, cartItem.quantity + step)
                                },
                                onDecrease = {
                                    val step = if (KiranaUnitUtils.isLooseUnit(cartItem.product.unit)) 0.25 else 1.0
                                    viewModel.updatePOSCartQuantity(cartItem.product, cartItem.quantity - step)
                                },
                                onRemove = {
                                    viewModel.removeFromPOSCart(cartItem.product)
                                }
                            )
                        }
                    }

                    // Adjustments Section: Discount & Tax
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Bill Adjustments (Discount & GST)", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                // Discount Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Discount, contentDescription = "Discount", tint = VyaparRed, modifier = Modifier.size(18.dp))
                                    Text("Discount", color = VyaparTextSecondary, fontSize = 12.sp, modifier = Modifier.width(60.dp))

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF1F5F9))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(if (viewModel.posDiscountType == "Fixed") VyaparRed else Color.Transparent)
                                                .clickable { viewModel.posDiscountType = "Fixed" }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("₹", color = if (viewModel.posDiscountType == "Fixed") Color.White else VyaparTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(if (viewModel.posDiscountType == "Percentage") VyaparRed else Color.Transparent)
                                                .clickable { viewModel.posDiscountType = "Percentage" }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("%", color = if (viewModel.posDiscountType == "Percentage") Color.White else VyaparTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = viewModel.posDiscountInput,
                                        onValueChange = { viewModel.posDiscountInput = it },
                                        placeholder = { Text("0", color = VyaparTextSecondary, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = VyaparRed,
                                            unfocusedBorderColor = VyaparBorder,
                                            focusedTextColor = VyaparTextPrimary,
                                            unfocusedTextColor = VyaparTextPrimary,
                                            focusedContainerColor = Color(0xFFF8FAFC),
                                            unfocusedContainerColor = Color(0xFFF8FAFC)
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pos_discount_input")
                                    )

                                    Text(
                                        text = "-₹${String.format(Locale.US, "%.2f", viewModel.posDiscountAmount)}",
                                        color = VyaparRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Tax / GST & Invoice Mode Settings Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, VyaparBorder, RoundedCornerShape(8.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = VyaparBlue, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (viewModel.isGstInvoiceMode) "GST Invoice Mode" else "Simple Estimate (Non-GST)",
                                                    color = VyaparTextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            androidx.compose.material3.Switch(
                                                checked = viewModel.isGstInvoiceMode,
                                                onCheckedChange = {
                                                    viewModel.isGstInvoiceMode = it
                                                    if (!it) viewModel.posTaxPercentageInput = "0"
                                                },
                                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                                    checkedThumbColor = VyaparRed,
                                                    checkedTrackColor = Color(0xFFFCA5A5)
                                                ),
                                                modifier = Modifier.testTag("pos_gst_toggle_switch")
                                            )
                                        }

                                        if (viewModel.isGstInvoiceMode) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("GST Rate:", color = VyaparTextSecondary, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    listOf("0", "5", "12", "18").forEach { taxRate ->
                                                        val isSelected = viewModel.posTaxPercentageInput == taxRate
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSelected) VyaparRed else Color(0xFFE2E8F0))
                                                                .clickable { viewModel.posTaxPercentageInput = taxRate }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = "$taxRate%",
                                                                color = if (isSelected) Color.White else VyaparTextPrimary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "+₹${String.format(Locale.US, "%.2f", viewModel.posTaxAmount)}",
                                                    color = VyaparBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            if (viewModel.posTaxAmount > 0) {
                                                val halfTax = viewModel.posTaxAmount / 2.0
                                                Text(
                                                    text = "CGST (Intra-state): ₹${String.format(Locale.US, "%.2f", halfTax)} | SGST: ₹${String.format(Locale.US, "%.2f", halfTax)}",
                                                    color = VyaparSuccess,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "⚡ Simple Estimate Mode active — Tax lines & GSTIN omitted on receipt.",
                                                color = VyaparTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Calculation Summary Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SummaryLineItem("Cart Subtotal", "₹${String.format(Locale.US, "%.2f", viewModel.posSubtotal)}")
                                if (viewModel.posDiscountAmount > 0) {
                                    SummaryLineItem("Discount Deducted", "-₹${String.format(Locale.US, "%.2f", viewModel.posDiscountAmount)}", isNegative = true)
                                }
                                if (viewModel.posTaxAmount > 0) {
                                    SummaryLineItem("Tax (${viewModel.posTaxPercentageInput}%)", "+₹${String.format(Locale.US, "%.2f", viewModel.posTaxAmount)}")
                                }
                                HorizontalDivider(color = Color(0xFFDCFCE7), modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Verified Total Amount", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", viewModel.posFinalTotal)}",
                                        color = VyaparRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
                    ) {
                        Text("Add More Items", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onProceedToPayment,
                        enabled = viewModel.posCartItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("pos_proceed_to_payment_button")
                    ) {
                        Text("Proceed to Payment ->", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentAndCheckoutModalDialog(
    viewModel: BillingViewModel,
    currentUser: com.example.data.db.UserEntity?,
    onShowUpiQr: () -> Unit,
    onCompleteSale: () -> Unit,
    onBackToCart: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            color = VyaparSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFEE2E2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Step 3/3: Payment & Complete Sale", color = VyaparTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Final verification & payment collection", color = VyaparTextSecondary, fontSize = 11.sp)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("payment_modal_close_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = VyaparTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Final Breakdown Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "FINAL INVOICE BREAKDOWN",
                                    color = VyaparRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SummaryLineItem("Subtotal (${viewModel.posCartItems.size} items)", "₹${String.format(Locale.US, "%.2f", viewModel.posSubtotal)}")
                                    if (viewModel.posDiscountAmount > 0) {
                                        SummaryLineItem("Discount Deducted", "-₹${String.format(Locale.US, "%.2f", viewModel.posDiscountAmount)}", isNegative = true)
                                    }
                                    if (viewModel.posTaxAmount > 0) {
                                        SummaryLineItem("Tax / GST (${viewModel.posTaxPercentageInput}%)", "+₹${String.format(Locale.US, "%.2f", viewModel.posTaxAmount)}")
                                    }
                                }

                                HorizontalDivider(color = VyaparBorder, modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("GRAND TOTAL", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Customer: ${viewModel.posCustomerName.ifBlank { "Walk-in Customer" }}", color = VyaparTextSecondary, fontSize = 11.sp)
                                    }
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", viewModel.posFinalTotal)}",
                                        color = VyaparRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        modifier = Modifier.testTag("pos_final_total_value")
                                    )
                                }
                            }
                        }
                    }

                    // Payment Mode Selection
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Select Payment Mode", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("Cash", "UPI / QR", "Card", "Credit (Udhar)").forEach { mode ->
                                        val isSelected = viewModel.posPaymentMode == mode
                                        Card(
                                            onClick = { viewModel.posPaymentMode = mode },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) VyaparRed else Color(0xFFF1F5F9)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) VyaparRedDark else VyaparBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .testTag("pos_payment_mode_${mode.lowercase().replace(" ", "_").replace("/", "")}")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = mode,
                                                    color = if (isSelected) Color.White else VyaparTextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                if (viewModel.posPaymentMode == "UPI / QR") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        onClick = onShowUpiQr,
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                                            .testTag("pos_show_upi_qr_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.QrCode, contentDescription = null, tint = VyaparBlue, modifier = Modifier.size(22.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text("Dynamic UPI QR Scanner Ready", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    val upiText = currentUser?.upiId?.takeIf { it.isNotBlank() } ?: "store@upi"
                                                    Text("Pay to: $upiText", color = VyaparBlue, fontSize = 11.sp)
                                                }
                                            }
                                            Button(
                                                onClick = onShowUpiQr,
                                                colors = ButtonDefaults.buttonColors(containerColor = VyaparBlue),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Show QR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (viewModel.posPaymentMode.contains("Credit", ignoreCase = true) || viewModel.posPaymentMode.contains("Udhar", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Udhar Sale Customer Details Required", color = VyaparRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = if (viewModel.posCustomerName == "Walk-in Customer") "" else viewModel.posCustomerName,
                                                onValueChange = { viewModel.posCustomerName = it },
                                                label = { Text("Customer Name *", color = VyaparTextSecondary, fontSize = 11.sp) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = VyaparRed,
                                                    unfocusedBorderColor = VyaparBorder,
                                                    focusedTextColor = VyaparTextPrimary,
                                                    unfocusedTextColor = VyaparTextPrimary,
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .testTag("udhar_customer_name_input")
                                            )

                                            OutlinedTextField(
                                                value = viewModel.posCustomerMobile,
                                                onValueChange = { viewModel.posCustomerMobile = it },
                                                label = { Text("Mobile No.", color = VyaparTextSecondary, fontSize = 11.sp) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = VyaparRed,
                                                    unfocusedBorderColor = VyaparBorder,
                                                    focusedTextColor = VyaparTextPrimary,
                                                    unfocusedTextColor = VyaparTextPrimary,
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("udhar_customer_mobile_input")
                                            )
                                        }

                                        val cleanUdharPhone = viewModel.posCustomerMobile.replace("[^0-9]".toRegex(), "").takeLast(10)
                                        val matchingCustomer = viewModel.customers.value.find {
                                            cleanUdharPhone.isNotEmpty() && it.mobileNumber.replace("[^0-9]".toRegex(), "").takeLast(10) == cleanUdharPhone
                                        }
                                        val prevDue = matchingCustomer?.totalPendingBalance ?: 0.0
                                        val totalOutstanding = prevDue + viewModel.posFinalTotal

                                        if (prevDue > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = Color(0xFFFFFBEB),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("Previous Udhar: ₹${String.format(Locale.US, "%.2f", prevDue)}", color = Color(0xFFB45309), fontSize = 11.sp)
                                                        Text("Total Outstanding: ₹${String.format(Locale.US, "%.2f", totalOutstanding)}", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                    Text("Udhar Khata", color = VyaparTextSecondary, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Auto-send WhatsApp Bill Switch Option
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "WhatsApp",
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Auto-send WhatsApp Bill",
                                            color = VyaparTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (viewModel.posCustomerMobile.isNotBlank()) "Send invoice to ${viewModel.posCustomerMobile}" else "Opens WhatsApp when mobile number is provided",
                                            color = VyaparTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = viewModel.autoSendWhatsAppInvoice,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleAutoSendWhatsAppInvoice(checked, context)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = WhatsAppGreen,
                                        uncheckedThumbColor = Color(0xFF94A3B8),
                                        uncheckedTrackColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.testTag("pos_auto_whatsapp_checkout_switch")
                                )
                            }
                        }
                    }

                    // Error Message Banner if any
                    item {
                        AnimatedVisibility(
                            visible = viewModel.posInvoiceError != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            viewModel.posInvoiceError?.let { err ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        text = err,
                                        color = VyaparRed,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onBackToCart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
                    ) {
                        Text("<- Back to Cart", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onCompleteSale,
                        enabled = viewModel.posCartItems.isNotEmpty() && !viewModel.isGeneratingPOSInvoice,
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("pos_complete_sale_button")
                    ) {
                        if (viewModel.isGeneratingPOSInvoice) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processing...", color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (viewModel.isEditingBill) "Update Bill & Save" else "Complete Sale & Print",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductPOSRow(
    product: ProductEntity,
    onAddToCart: () -> Unit
) {
    val expiryStatus = com.example.util.PharmacyUtils.getExpiryStatus(product.expiryDate)
    val isExpired = expiryStatus is com.example.util.ExpiryStatus.Expired
    val isOutOfStock = product.stockQuantity <= 0
    val isDisabled = isOutOfStock || isExpired

    val cardBorderColor = when {
        isExpired -> Color(0xFFEF4444)
        expiryStatus is com.example.util.ExpiryStatus.NearExpiry -> Color(0xFFF59E0B)
        isOutOfStock -> Color(0xFFCBD5E1)
        else -> VyaparBorder
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isExpired -> Color(0xFFFEF2F2)
                expiryStatus is com.example.util.ExpiryStatus.NearExpiry -> Color(0xFFFFFBEB)
                isOutOfStock -> Color(0xFFF8FAFC)
                else -> VyaparSurface
            }
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
            .then(
                if (!isDisabled) {
                    Modifier.clickable { onAddToCart() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    color = VyaparTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (product.saltComposition.isNotBlank()) {
                    Text(
                        text = "Salt: ${product.saltComposition}",
                        color = VyaparTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", product.salePrice)} / ${product.unit}",
                        color = VyaparRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text("·", color = VyaparTextSecondary, fontSize = 11.sp)
                    Text(
                        text = if (isOutOfStock) "Out of stock" else "Stock: ${KiranaUnitUtils.formatQuantityWithUnit(product.stockQuantity, product.unit)}",
                        color = if (isOutOfStock) Color(0xFFEF4444) else VyaparTextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Batch & Expiry Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (product.batchNumber.isNotBlank()) {
                        Text(
                            text = "B:${product.batchNumber}",
                            color = VyaparBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (product.expiryDate.isNotBlank()) {
                        when (expiryStatus) {
                            is com.example.util.ExpiryStatus.Expired -> {
                                Text(
                                    text = "⚠️ EXPIRED (${product.expiryDate})",
                                    color = Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            is com.example.util.ExpiryStatus.NearExpiry -> {
                                Text(
                                    text = "⚡ EXPIRING SOON (${product.expiryDate})",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> {
                                Text(
                                    text = "Exp: ${product.expiryDate}",
                                    color = VyaparSuccess,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    if (product.isRxRequired) {
                        Text(
                            text = "Rx Required",
                            color = Color(0xFFDB2777),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = onAddToCart,
                enabled = !isDisabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VyaparRed,
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("pos_add_product_${product.name.lowercase().replace(" ", "_")}")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = if (isDisabled) Color.Gray else Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = when {
                        isExpired -> "Expired"
                        isOutOfStock -> "Empty"
                        else -> "Add"
                    },
                    color = if (isDisabled) Color.Gray else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CartItemRow(
    cartItem: POSCartItem,
    onEditQuantity: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    val maxStock = cartItem.product.stockQuantity
    val isLoose = KiranaUnitUtils.isLooseUnit(cartItem.product.unit)

    Card(
        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .clickable { onEditQuantity() }
            ) {
                Text(
                    text = cartItem.product.name,
                    color = VyaparTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cartItem.product.batchNumber.isNotBlank() || cartItem.product.expiryDate.isNotBlank()) {
                    Text(
                        text = listOfNotNull(
                            cartItem.product.batchNumber.takeIf { it.isNotBlank() }?.let { "Batch: $it" },
                            cartItem.product.expiryDate.takeIf { it.isNotBlank() }?.let { "Exp: $it" }
                        ).joinToString(" · "),
                        color = VyaparTextSecondary,
                        fontSize = 10.sp
                    )
                }
                val isPharm = com.example.util.PharmacyUtils.isPharmacyProduct(cartItem.product) || cartItem.product.unit.equals("Strip", ignoreCase = true) || cartItem.product.packUnitConfig.isNotBlank()
                val packSize = com.example.util.PharmacyUtils.getPackSize(cartItem.product)
                val totalTabs = Math.round(cartItem.quantity * packSize).toInt()
                val isLooseTablet = isPharm && (totalTabs % packSize != 0)

                Text(
                    text = if (isLooseTablet) {
                        val perTab = com.example.util.PharmacyUtils.getPerTabletUnitPrice(cartItem.product)
                        "₹${String.format(Locale.US, "%.2f", perTab)} / Tab  =  ₹${String.format(Locale.US, "%.2f", cartItem.totalAmount)}"
                    } else {
                        "₹${String.format(Locale.US, "%.2f", cartItem.customPrice)} / ${cartItem.product.unit}  =  ₹${String.format(Locale.US, "%.2f", cartItem.totalAmount)}"
                    },
                    color = VyaparRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Quantity selector with Loose Qty formatting: - [ Qty ] +
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                        .clickable { onDecrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = VyaparTextPrimary, modifier = Modifier.size(14.dp))
                }

                Surface(
                    onClick = onEditQuantity,
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = KiranaUnitUtils.formatQuantityWithUnit(cartItem.quantity, cartItem.product.unit, cartItem.product),
                        color = VyaparRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (cartItem.quantity < maxStock) VyaparRed else Color(0xFFF1F5F9))
                        .clickable(enabled = cartItem.quantity < maxStock) { onIncrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = if (cartItem.quantity < maxStock) Color.White else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = VyaparRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryLineItem(label: String, value: String, isNegative: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VyaparTextSecondary, fontSize = 12.sp)
        Text(
            value,
            color = if (isNegative) VyaparRed else VyaparTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun POSCustomerPickerModalDialog(
    customers: List<com.example.data.db.CustomerEntity>,
    onSelectCustomer: (com.example.data.db.CustomerEntity) -> Unit,
    onSelectWalkIn: () -> Unit,
    onAddNewCustomerClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredList = remember(customers, query) {
        if (query.isBlank()) {
            customers
        } else {
            customers.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.mobileNumber.contains(query)
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            color = VyaparSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFEE2E2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Select Customer", color = VyaparTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Search existing or register new customer", color = VyaparTextSecondary, fontSize = 11.sp)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("customer_picker_close_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = VyaparTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Input Box
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by Name or Mobile No...", color = VyaparTextSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = VyaparRed) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = VyaparTextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary,
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_picker_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSelectWalkIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, VyaparBorder, RoundedCornerShape(8.dp))
                            .testTag("select_walk_in_customer_btn")
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = VyaparTextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Walk-in", color = VyaparTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAddNewCustomerClick,
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("add_new_customer_modal_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add Customer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SAVED CUSTOMERS (${filteredList.size})",
                    color = VyaparTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                            Icon(Icons.Default.PersonOff, contentDescription = null, tint = VyaparTextSecondary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No matching customers found", color = VyaparTextPrimary, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap '+ Add Customer' above to save details.", color = VyaparTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filteredList) { cust ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, VyaparBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSelectCustomer(cust) }
                                    .testTag("customer_item_${cust.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color(0xFFFEE2E2), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cust.name.take(1).uppercase(Locale.getDefault()),
                                                color = VyaparRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = cust.name,
                                                color = VyaparTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Phone, contentDescription = null, tint = VyaparTextSecondary, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(cust.mobileNumber, color = VyaparTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        if (cust.totalPendingBalance > 0) {
                                            Text(
                                                text = "Udhar: ₹${String.format(Locale.US, "%.2f", cust.totalPendingBalance)}",
                                                color = VyaparRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            Surface(
                                                color = Color(0xFFF0FDF4),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "No Pending",
                                                    color = VyaparSuccess,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { onSelectCustomer(cust) },
                                            colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Select", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddNewCustomerModalDialog(
    onSaveCustomer: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFFEE2E2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Register New Customer", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enter details to save this customer to your database and auto-select them for billing.",
                    color = VyaparTextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *", color = VyaparTextSecondary, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = VyaparRed) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary,
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_name_input")
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number *", color = VyaparTextSecondary, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = VyaparRed) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary,
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_mobile_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && mobile.isNotBlank()) {
                        onSaveCustomer(name, mobile)
                    }
                },
                enabled = name.isNotBlank() && mobile.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_new_customer_btn")
            ) {
                Text("Save & Select", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VyaparTextSecondary)
            }
        },
        containerColor = VyaparSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, VyaparBorder, RoundedCornerShape(16.dp))
    )
}
