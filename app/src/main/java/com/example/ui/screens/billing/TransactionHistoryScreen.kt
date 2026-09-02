package com.example.ui.screens.billing

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.InvoiceEntity
import com.example.ui.theme.DarkGray
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GoldYellow
import com.example.ui.viewmodel.BillingViewModel
import com.example.util.InvoicePdfHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: BillingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPOS: () -> Unit = {}
) {
    val context = LocalContext.current
    val invoices by viewModel.invoices.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf("All Time") } // All Time, Today, This Week, This Month
    var selectedPaymentMode by remember { mutableStateOf("All") }

    var selectedInvoiceForDetail by remember { mutableStateOf<InvoiceEntity?>(null) }

    // Date range filter logic
    val filteredInvoices = remember(invoices, searchQuery, selectedDateRange, selectedPaymentMode) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val startOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = calendar.apply {
            set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        }.timeInMillis

        val startOfMonth = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        invoices.filter { inv ->
            val matchesQuery = searchQuery.isBlank() ||
                    inv.customerName.contains(searchQuery, ignoreCase = true) ||
                    inv.customerMobile.contains(searchQuery, ignoreCase = true) ||
                    inv.firestoreId.contains(searchQuery, ignoreCase = true) ||
                    inv.id.toString().contains(searchQuery)

            val matchesDate = when (selectedDateRange) {
                "Today" -> inv.timestamp >= startOfDay
                "This Week" -> inv.timestamp >= startOfWeek
                "This Month" -> inv.timestamp >= startOfMonth
                else -> true
            }

            val matchesPayment = selectedPaymentMode == "All" || inv.paymentMode.equals(selectedPaymentMode, ignoreCase = true)

            matchesQuery && matchesDate && matchesPayment
        }
    }

    val totalRevenue = remember(filteredInvoices) { filteredInvoices.sumOf { it.amount } }
    val avgTicketValue = remember(filteredInvoices) { if (filteredInvoices.isNotEmpty()) totalRevenue / filteredInvoices.size else 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Transaction History",
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
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0x99090D22)
                ),
                modifier = Modifier.testTag("history_top_bar")
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B0F28),
                            DarkGray,
                            Color(0xFF090C1E)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // 1. KPI Revenue Summary Section
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x2210B981), RoundedCornerShape(16.dp))
                            .testTag("history_kpi_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL REVENUE", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", totalRevenue)}",
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                                Text("${filteredInvoices.size} Invoices Billed", color = EmeraldLight, fontSize = 11.sp)
                            }

                            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color(0x22FFFFFF)))

                            Column(horizontalAlignment = Alignment.End) {
                                Text("AVG INVOICE", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", avgTicketValue)}",
                                    color = GoldYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text("Ticket Average", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 2. Search & Filters Bar
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x151E295D)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search Field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search customer, mobile or invoice #...", color = Color(0xFF64748B), fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = EmeraldGreen, modifier = Modifier.size(20.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_search_input")
                            )

                            // Date Range Chips
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Filter by Time:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("All Time", "Today", "This Week", "This Month")) { period ->
                                        val isSelected = selectedDateRange == period
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedDateRange = period },
                                            label = { Text(period, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = EmeraldGreen,
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0x22FFFFFF),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                }
                            }

                            // Payment Mode Chips
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Filter by Payment:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("All", "Cash", "UPI / QR", "Online", "Credit (Udhar)")) { mode ->
                                        val isSelected = selectedPaymentMode == mode
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedPaymentMode = mode },
                                            label = { Text(mode, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ElectricVioletLight,
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0x22FFFFFF),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Transactions List
                if (filteredInvoices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = Color(0x44FFFFFF),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No transactions match your search filter", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(filteredInvoices) { invoice ->
                        HistoryInvoiceCard(
                            invoice = invoice,
                            onCardClick = { selectedInvoiceForDetail = invoice },
                            onEditClick = {
                                viewModel.loadInvoiceForEditing(invoice)
                                onNavigateToPOS()
                            },
                            onPrintClick = {
                                val pdf = InvoicePdfHelper.generateInvoicePdf(
                                    context = context,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName,
                                    merchantMobile = currentUser?.mobileNumber
                                )
                                InvoicePdfHelper.printInvoicePdf(context, pdf)
                            },
                            onWhatsAppClick = {
                                val pdf = InvoicePdfHelper.generateInvoicePdf(
                                    context = context,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName,
                                    merchantMobile = currentUser?.mobileNumber
                                )
                                InvoicePdfHelper.shareInvoicePdfWhatsApp(
                                    context = context,
                                    pdfFile = pdf,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail & PDF Actions Modal
    selectedInvoiceForDetail?.let { invoice ->
        val formattedDate = remember(invoice.timestamp) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.timestamp))
        }

        AlertDialog(
            onDismissRequest = { selectedInvoiceForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = "Invoice", tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Invoice Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("ID: #${invoice.firestoreId.take(8).ifBlank { invoice.id }}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Merchant & Customer Box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(currentUser?.businessName ?: "Billing Store", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Customer: ${invoice.customerName} ${if (invoice.customerMobile.isNotBlank()) "(${invoice.customerMobile})" else ""}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text("Date: $formattedDate", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text("Payment Mode: ${invoice.paymentMode}", color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Items Summary
                    Text("Purchased Items:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = invoice.itemsSummary.ifBlank { "${invoice.itemsCount} items billed" },
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color(0x11FFFFFF), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .fillMaxWidth()
                    )

                    // Financial Summary
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("₹${String.format(Locale.US, "%.2f", invoice.subtotal)}", color = Color.White, fontSize = 12.sp)
                        }
                        if (invoice.discountAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text("-₹${String.format(Locale.US, "%.2f", invoice.discountAmount)}", color = Color(0xFFF87171), fontSize = 12.sp)
                            }
                        }
                        if (invoice.taxAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tax", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text("+₹${String.format(Locale.US, "%.2f", invoice.taxAmount)}", color = GoldYellow, fontSize = 12.sp)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Grand Total", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹${String.format(Locale.US, "%.2f", invoice.amount)}", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // PDF Action Buttons inside Modal
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                selectedInvoiceForDetail = null
                                viewModel.loadInvoiceForEditing(invoice)
                                onNavigateToPOS()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                                .testTag("modal_edit_bill_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✏️ Edit Bill Items & Quantities", color = GoldYellow, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val pdf = InvoicePdfHelper.generateInvoicePdf(
                                    context = context,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName,
                                    merchantMobile = currentUser?.mobileNumber
                                )
                                InvoicePdfHelper.printInvoicePdf(context, pdf)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("modal_print_pdf_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print Invoice PDF", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val pdf = InvoicePdfHelper.generateInvoicePdf(
                                    context = context,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName,
                                    merchantMobile = currentUser?.mobileNumber
                                )
                                InvoicePdfHelper.shareInvoicePdfWhatsApp(
                                    context = context,
                                    pdfFile = pdf,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), // WhatsApp Green
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("modal_share_whatsapp_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share via WhatsApp", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val pdf = InvoicePdfHelper.generateInvoicePdf(
                                    context = context,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName,
                                    merchantMobile = currentUser?.mobileNumber
                                )
                                InvoicePdfHelper.shareInvoicePdfGeneral(
                                    context = context,
                                    pdfFile = pdf,
                                    invoice = invoice,
                                    businessName = currentUser?.businessName
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("modal_share_general_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ElectricVioletLight, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share PDF File", color = ElectricVioletLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedInvoiceForDetail = null }) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, Color(0x3310B981), RoundedCornerShape(20.dp))
        )
    }
}

@Composable
private fun HistoryInvoiceCard(
    invoice: InvoiceEntity,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onPrintClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val dateString = remember(invoice.timestamp) {
        SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault()).format(Date(invoice.timestamp))
    }

    Card(
        onClick = onCardClick,
        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
            .testTag("history_invoice_item_${invoice.firestoreId.take(6).ifBlank { invoice.id }}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x2210B981), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = invoice.customerName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (invoice.isEdited) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x33F59E0B))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("(Edited)", color = GoldYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(dateString, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", invoice.amount)}",
                        color = EmeraldLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2210B981))
                            .wrapContentWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = invoice.paymentMode,
                            color = EmeraldGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Text Line
            if (invoice.itemsSummary.isNotBlank()) {
                Text(
                    text = "Items: ${invoice.itemsSummary}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldYellow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66F59E0B)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("item_edit_button_${invoice.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Bill", tint = GoldYellow, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("✏️ Edit Bill", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPrintClick,
                        modifier = Modifier.size(32.dp).testTag("item_print_button")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print PDF", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onWhatsAppClick,
                        modifier = Modifier.size(32.dp).testTag("item_whatsapp_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
