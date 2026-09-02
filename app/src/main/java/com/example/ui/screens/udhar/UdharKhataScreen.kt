package com.example.ui.screens.udhar

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CustomerEntity
import com.example.data.db.CustomerTransactionEntity
import com.example.ui.components.GlassmorphicCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.BillingViewModel
import com.example.util.ReminderType
import com.example.util.WhatsAppReminderUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UdharKhataScreen(
    viewModel: BillingViewModel,
    onNavigateBack: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null
) {
    val customers by viewModel.filteredCustomers.collectAsState()
    val allCustomers by viewModel.customers.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()

    var selectedCustomerForLedger by remember { mutableStateOf<CustomerEntity?>(null) }
    var showJamaDialog by remember { mutableStateOf(false) }
    var showAddUdharDialog by remember { mutableStateOf(false) }

    // Voice Entry States
    var showVoiceUdharDialog by remember { mutableStateOf(false) }
    var voiceSpeechInputText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                voiceSpeechInputText = spokenText
                showVoiceUdharDialog = true
            }
        }
    }

    val triggerSpeechToText: () -> Unit = {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak Udhar Entry (e.g. 'Ramesh ko 2 kilo chini 100 rupaye ka udhar diya')")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            voiceSpeechInputText = ""
            showVoiceUdharDialog = true
        }
    }

    // WhatsApp Reminder Modal States
    var showWhatsAppReminderModal by remember { mutableStateOf(false) }
    var reminderTargetCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedReminderTypeForModal by remember { mutableStateOf(ReminderType.POLITE) }

    val totalPendingAmount = remember(allCustomers) {
        allCustomers.sumOf { it.totalPendingBalance }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Udhar Khata (Credit Ledger)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("udhar_khata_drawer_button")
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu Drawer", tint = Color.White)
                        }
                    } else {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("udhar_khata_back_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = triggerSpeechToText,
                        modifier = Modifier.testTag("udhar_khata_voice_action")
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Entry", tint = GoldYellow)
                    }

                    IconButton(
                        onClick = { showAddUdharDialog = true },
                        modifier = Modifier.testTag("udhar_khata_add_customer_action")
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = EmeraldGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VyaparRed),
                modifier = Modifier.testTag("udhar_khata_top_bar")
            )
        },
        containerColor = VyaparBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedCustomerForLedger != null) {
                CustomerLedgerDetailView(
                    customer = selectedCustomerForLedger!!,
                    viewModel = viewModel,
                    onBack = {
                        selectedCustomerForLedger = null
                        viewModel.activeCustomerMobileForLedger.value = ""
                    },
                    onOpenJamaDialog = { showJamaDialog = true },
                    onOpenAddUdharDialog = { showAddUdharDialog = true },
                    onOpenWhatsAppReminder = { cust, type ->
                        reminderTargetCustomer = cust
                        selectedReminderTypeForModal = type
                        showWhatsAppReminderModal = true
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Summary KPI Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                            .testTag("udhar_khata_summary_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL PENDING UDHARI",
                                    color = VyaparRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", totalPendingAmount)}",
                                    color = VyaparRed,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("udhar_khata_total_amount")
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CREDIT CUSTOMERS",
                                    color = VyaparTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${allCustomers.size}",
                                    color = VyaparDeepBlue,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("udhar_khata_customers_count")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.customerSearchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("udhar_khata_search_input"),
                        placeholder = { Text("Search by Customer Name or Mobile...", color = VyaparTextSecondary, fontSize = 13.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = VyaparTextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.customerSearchQuery.value = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = VyaparTextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VyaparRed,
                            unfocusedBorderColor = VyaparBorder,
                            focusedContainerColor = VyaparSurface,
                            unfocusedContainerColor = VyaparSurface,
                            focusedTextColor = VyaparTextPrimary,
                            unfocusedTextColor = VyaparTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Customer List
                    if (customers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Empty",
                                    tint = VyaparTextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isBlank()) "No customer Udhar records found" else "No matching customers found",
                                    color = VyaparTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tap below to add your first customer and record credit",
                                    color = VyaparTextSecondary,
                                    fontSize = 12.sp
                                )
                                if (searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { showAddUdharDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("empty_add_first_customer_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = "Add Customer",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "+ Add Customer to Udhar Khata",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("udhar_khata_customer_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(customers) { customer ->
                                CustomerBalanceCard(
                                    customer = customer,
                                    onSelect = {
                                        selectedCustomerForLedger = customer
                                        viewModel.activeCustomerMobileForLedger.value = customer.mobileNumber
                                    },
                                    onOpenReminder = { cust ->
                                        reminderTargetCustomer = cust
                                        selectedReminderTypeForModal = ReminderType.POLITE
                                        showWhatsAppReminderModal = true
                                    },
                                    onCancelAutoReminder = { cust ->
                                        val ctx = context
                                        viewModel.cancelCustomerAutoReminder(ctx, cust)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Jama Payment Received Dialog Modal
            if (showJamaDialog) {
                val activeCustomer = selectedCustomerForLedger
                JamaPaymentDialog(
                    initialName = activeCustomer?.name ?: "",
                    initialMobile = activeCustomer?.mobileNumber ?: "",
                    onDismiss = { showJamaDialog = false },
                    onSave = { name, mobile, amount, mode, note ->
                        viewModel.recordJamaPayment(name, mobile, amount, mode, note) {
                            showJamaDialog = false
                            // Refresh selected customer state
                            val updatedCustomer = allCustomers.find { it.mobileNumber == mobile }
                            if (updatedCustomer != null) {
                                selectedCustomerForLedger = updatedCustomer
                            }
                        }
                    }
                )
            }

            // New Udhar Entry Dialog Modal
            if (showAddUdharDialog) {
                val activeCustomer = selectedCustomerForLedger
                AddUdharEntryDialog(
                    initialName = activeCustomer?.name ?: "",
                    initialMobile = activeCustomer?.mobileNumber ?: "",
                    onDismiss = { showAddUdharDialog = false },
                    onSave = { name, mobile, amount, note ->
                        viewModel.recordUdharEntry(name, mobile, amount, note) {
                            showAddUdharDialog = false
                            val updatedCustomer = allCustomers.find { it.mobileNumber == mobile }
                            if (updatedCustomer != null) {
                                selectedCustomerForLedger = updatedCustomer
                            }
                        }
                    }
                )
            }

            // WhatsApp Reminder Engine Modal Dialog
            if (showWhatsAppReminderModal && reminderTargetCustomer != null) {
                val currentUser by viewModel.currentUser.collectAsState()
                val transactions by viewModel.selectedCustomerTransactions.collectAsState()
                val context = LocalContext.current
                val dynamicStoreName = currentUser?.businessName?.takeIf { it.isNotBlank() }
                    ?: currentUser?.merchantName?.takeIf { it.isNotBlank() }
                    ?: "SmartPOS Store"
                val dynamicStorePhone = currentUser?.mobileNumber?.takeIf { it.isNotBlank() }
                    ?: com.example.data.firebase.FirebaseManager.auth?.currentUser?.phoneNumber.orEmpty()
                val dynamicUpiId = currentUser?.upiId?.takeIf { it.isNotBlank() } ?: "merchant@upi"

                WhatsAppReminderModalDialog(
                    customer = reminderTargetCustomer!!,
                    businessName = dynamicStoreName,
                    merchantPhone = dynamicStorePhone,
                    upiId = dynamicUpiId,
                    initialType = selectedReminderTypeForModal,
                    transactions = transactions,
                    onScheduleAuto = { scheduledEpoch, customMsg ->
                        viewModel.scheduleCustomerAutoReminder(
                            context = context,
                            customer = reminderTargetCustomer!!,
                            scheduledEpochMillis = scheduledEpoch,
                            reminderType = selectedReminderTypeForModal,
                            customMessage = customMsg
                        )
                        showWhatsAppReminderModal = false
                        reminderTargetCustomer = null
                    },
                    onCancelAuto = {
                        viewModel.cancelCustomerAutoReminder(
                            context = context,
                            customer = reminderTargetCustomer!!
                        )
                    },
                    onDismiss = {
                        showWhatsAppReminderModal = false
                        reminderTargetCustomer = null
                    }
                )
            }

            // Voice-Controlled Udhar Khata Entry Modal Dialog
            if (showVoiceUdharDialog) {
                VoiceUdharEntryDialog(
                    allCustomers = allCustomers,
                    initialSpeechText = voiceSpeechInputText,
                    onDismiss = { showVoiceUdharDialog = false },
                    onTriggerMic = triggerSpeechToText,
                    onSaveUdhar = { name, mobile, amount, note ->
                        viewModel.recordUdharEntry(name, mobile, amount, note) {
                            showVoiceUdharDialog = false
                            val updatedCustomer = allCustomers.find { it.mobileNumber == mobile }
                            if (updatedCustomer != null) {
                                selectedCustomerForLedger = updatedCustomer
                            }
                        }
                    },
                    onSaveJama = { name, mobile, amount, mode, note ->
                        viewModel.recordJamaPayment(name, mobile, amount, mode, note) {
                            showVoiceUdharDialog = false
                            val updatedCustomer = allCustomers.find { it.mobileNumber == mobile }
                            if (updatedCustomer != null) {
                                selectedCustomerForLedger = updatedCustomer
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CustomerBalanceCard(
    customer: CustomerEntity,
    onSelect: () -> Unit,
    onOpenReminder: (CustomerEntity) -> Unit,
    onCancelAutoReminder: ((CustomerEntity) -> Unit)? = null
) {
    val formattedDate = remember(customer.lastTransactionTimestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(customer.lastTransactionTimestamp))
    }

    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
            .testTag("udhar_customer_item_${customer.mobileNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFFEBEE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.take(1).uppercase(),
                            color = VyaparRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = customer.name,
                            color = VyaparTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "📱 ${customer.mobileNumber}",
                            color = VyaparTextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Last: $formattedDate",
                            color = VyaparTextSecondary.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", customer.totalPendingBalance)}",
                        color = if (customer.totalPendingBalance > 0) VyaparRed else VyaparSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                if (customer.totalPendingBalance > 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (customer.totalPendingBalance > 0) "Pending Udhar" else "Settled",
                            color = if (customer.totalPendingBalance > 0) VyaparRed else VyaparSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Scheduled Auto Reminder Badge
            if (customer.reminderScheduledDate > 0 && customer.reminderStatus == "SCHEDULED") {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Scheduled",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val scheduleStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(customer.reminderScheduledDate))
                            Text(
                                text = "Auto Reminder: $scheduleStr",
                                color = Color(0xFFB45309),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (onCancelAutoReminder != null) {
                            IconButton(
                                onClick = { onCancelAutoReminder(customer) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Auto Reminder",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // WhatsApp Payment Reminder Action Bar
            if (customer.totalPendingBalance > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = VyaparBorder)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onOpenReminder(customer) },
                        border = BorderStroke(1.dp, VyaparBorder),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("udhar_auto_reminder_button_${customer.mobileNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Schedule Auto",
                            tint = VyaparDeepBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (customer.reminderScheduledDate > 0 && customer.reminderStatus == "SCHEDULED") "Reschedule" else "Auto Schedule",
                            color = VyaparDeepBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onOpenReminder(customer) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("udhar_reminder_button_${customer.mobileNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "WhatsApp Reminder",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WhatsApp Reminder",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private data class UdharItemRow(
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val lineTotal: Double
)

private fun parseTransactionItems(tx: CustomerTransactionEntity): List<UdharItemRow> {
    if (tx.itemsJson.isNotBlank()) {
        try {
            val list = mutableListOf<UdharItemRow>()
            val jsonArray = org.json.JSONArray(tx.itemsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "Item")
                val quantity = obj.optDouble("quantity", 1.0)
                val unit = obj.optString("unit", "Pcs")
                val unitPrice = obj.optDouble("unitPrice", 0.0)
                val lineTotal = obj.optDouble("lineTotal", quantity * unitPrice)
                list.add(UdharItemRow(name, quantity, unit, unitPrice, lineTotal))
            }
            if (list.isNotEmpty()) return list
        } catch (e: Exception) {
            // Fallback to note parsing below
        }
    }

    val noteClean = tx.note.removePrefix("POS Bill ").removePrefix("POS Bill Udhari ").trim()
    if (noteClean.isNotBlank()) {
        val parts = noteClean.split(",")
        val list = mutableListOf<UdharItemRow>()
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isNotBlank()) {
                val match = Regex("""^(.+?)\s*x\s*(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
                if (match != null) {
                    val qtyUnitStr = match.groupValues[1].trim()
                    val itemName = match.groupValues[2].trim()
                    val qtyParts = qtyUnitStr.split(" ")
                    val qtyVal = qtyParts.firstOrNull()?.toDoubleOrNull() ?: 1.0
                    val unitStr = if (qtyParts.size > 1) qtyParts.subList(1, qtyParts.size).joinToString(" ") else "Pcs"
                    list.add(UdharItemRow(itemName, qtyVal, unitStr, 0.0, 0.0))
                } else {
                    list.add(UdharItemRow(trimmed, 1.0, "Pcs", tx.amount, tx.amount))
                }
            }
        }
        if (list.isNotEmpty()) return list
    }

    return listOf(UdharItemRow("Udhar Sale Item", 1.0, "Pcs", tx.amount, tx.amount))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerLedgerDetailView(
    customer: CustomerEntity,
    viewModel: BillingViewModel,
    onBack: () -> Unit,
    onOpenJamaDialog: () -> Unit,
    onOpenAddUdharDialog: () -> Unit,
    onOpenWhatsAppReminder: (CustomerEntity, ReminderType) -> Unit
) {
    val transactions by viewModel.selectedCustomerTransactions.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var selectedTxForReceipt by remember { mutableStateOf<CustomerTransactionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Customer Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = VyaparSurface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                .testTag("customer_ledger_header")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VyaparTextPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = customer.name,
                                color = VyaparTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "📱 ${customer.mobileNumber}",
                                color = VyaparTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Outstanding", color = VyaparTextSecondary, fontSize = 11.sp)
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", customer.totalPendingBalance)}",
                            color = VyaparRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.testTag("customer_ledger_balance")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row: Jama Karein vs Add Udhar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenJamaDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparSuccess),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_ledger_jama_button")
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Jama Karein", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jama Karein (+)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onOpenAddUdharDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_ledger_add_udhar_button")
                    ) {
                        Icon(imageVector = Icons.Default.RemoveCircle, contentDescription = "Add Udhar", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Udhar (+)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Scheduled Reminder Alert Banner
                if (customer.reminderScheduledDate > 0 && customer.reminderStatus == "SCHEDULED") {
                    Spacer(modifier = Modifier.height(10.dp))
                    val scheduleStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(customer.reminderScheduledDate))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = "Scheduled", tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("⏰ Auto Reminder scheduled for $scheduleStr", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // WhatsApp Reminder Action Button inside Ledger Header
                Button(
                    onClick = { onOpenWhatsAppReminder(customer, ReminderType.POLITE) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_ledger_whatsapp_reminder_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "WhatsApp Reminder", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send WhatsApp Payment Reminder", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = VyaparBorder)
                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Reminder Chips
                Text("QUICK REMINDER TEMPLATES", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { onOpenWhatsAppReminder(customer, ReminderType.POLITE) },
                        label = { Text("💬 Polite", fontSize = 11.sp, color = VyaparSuccess) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFE8F5E9)),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color(0xFFA5D6A7)),
                        modifier = Modifier.testTag("quick_chip_polite")
                    )

                    FilterChip(
                        selected = false,
                        onClick = { onOpenWhatsAppReminder(customer, ReminderType.URGENT) },
                        label = { Text("⚠️ Urgent", fontSize = 11.sp, color = VyaparRed) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFFFEBEE)),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color(0xFFEF9A9A)),
                        modifier = Modifier.testTag("quick_chip_urgent")
                    )

                    FilterChip(
                        selected = false,
                        onClick = { onOpenWhatsAppReminder(customer, ReminderType.STATEMENT) },
                        label = { Text("📄 Statement", fontSize = 11.sp, color = VyaparDeepBlue) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFE8EAF6)),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color(0xFF9FA8DA)),
                        modifier = Modifier.testTag("quick_chip_statement")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ledger Transaction History",
            color = VyaparTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No ledger history found for this customer", color = VyaparTextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("customer_ledger_transactions_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(transactions) { tx ->
                    LedgerTransactionRow(
                        tx = tx,
                        currentUser = currentUser,
                        onViewReceipt = { selectedTxForReceipt = it }
                    )
                }
            }
        }
    }

    selectedTxForReceipt?.let { tx ->
        UdharBillReceiptModalDialog(
            transaction = tx,
            customer = customer,
            currentUser = currentUser,
            onDismiss = { selectedTxForReceipt = null }
        )
    }
}

@Composable
private fun LedgerTransactionRow(
    tx: CustomerTransactionEntity,
    currentUser: com.example.data.db.UserEntity?,
    onViewReceipt: (CustomerTransactionEntity) -> Unit
) {
    val isJama = tx.type == "CREDIT"
    var isExpanded by remember { mutableStateOf(false) }
    val parsedItems = remember(tx) { parseTransactionItems(tx) }

    val formattedDate = remember(tx.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        sdf.format(Date(tx.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isExpanded) 1.5.dp else 1.dp,
                color = if (isExpanded) (if (isJama) VyaparSuccess else VyaparRed) else VyaparBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .testTag("ledger_tx_card_${tx.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isJama) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isJama) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = tx.type,
                            tint = if (isJama) VyaparSuccess else VyaparRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isJama) "Jama (Payment Received)" else "Udhar Given",
                                color = VyaparTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = VyaparBg,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(
                                    text = tx.paymentMode,
                                    color = VyaparTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (tx.isEdited) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = Color(0xFFFFFBEB),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    Text(
                                        text = "EDITED",
                                        color = Color(0xFFD97706),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Timestamp visibility requirement
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Time",
                                tint = VyaparTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formattedDate,
                                color = VyaparTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (isJama) "- ₹" else "+ ₹"}${String.format(Locale.US, "%.2f", tx.amount)}",
                            color = if (isJama) VyaparSuccess else VyaparRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (!isJama) {
                            Text(
                                text = "${parsedItems.size} ${if (parsedItems.size == 1) "Item" else "Items"}",
                                color = VyaparTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = VyaparTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Expanded Itemized Purchase Breakdown & Quick Actions
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = VyaparBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "ITEMIZED PURCHASE BREAKDOWN",
                        color = VyaparDeepBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Item Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ITEM NAME", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                        Text("QTY & UNIT", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                        Text("PRICE", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("TOTAL", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Item Table Rows
                    parsedItems.forEachIndexed { idx, item ->
                        val formattedQty = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(item.quantity, item.unit)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (idx % 2 == 0) Color(0xFFF8FAFC) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                color = VyaparTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(2f)
                            )
                            Text(
                                text = formattedQty,
                                color = VyaparDeepBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                text = if (item.unitPrice > 0) "₹${String.format(Locale.US, "%.2f", item.unitPrice)}" else "—",
                                color = VyaparTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (item.lineTotal > 0) "₹${String.format(Locale.US, "%.2f", item.lineTotal)}" else "₹${String.format(Locale.US, "%.2f", tx.amount)}",
                                color = VyaparTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = VyaparBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Action: View / Print Bill Button
                    Button(
                        onClick = { onViewReceipt(tx) },
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("view_print_receipt_btn_${tx.id}")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Receipt", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Full Receipt / Print Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UdharBillReceiptModalDialog(
    transaction: CustomerTransactionEntity,
    customer: CustomerEntity,
    currentUser: com.example.data.db.UserEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val parsedItems = remember(transaction) { parseTransactionItems(transaction) }
    val formattedDate = remember(transaction.timestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(transaction.timestamp))
    }

    val storeName = currentUser?.businessName?.ifBlank { "Kirana Store" } ?: "Kirana Store"
    val merchantMobile = currentUser?.mobileNumber ?: ""

    val invoiceForPdf = remember(transaction, customer, parsedItems, storeName) {
        com.example.data.db.InvoiceEntity(
            id = transaction.id,
            firestoreId = transaction.invoiceId.ifBlank { "UDHAR-${transaction.id}" },
            customerName = customer.name,
            customerMobile = customer.mobileNumber,
            amount = transaction.amount,
            itemsCount = parsedItems.size,
            subtotal = transaction.amount,
            discountAmount = 0.0,
            taxAmount = 0.0,
            paymentMode = transaction.paymentMode,
            itemsSummary = parsedItems.joinToString(", ") { "${com.example.util.KiranaUnitUtils.formatQuantityWithUnit(it.quantity, it.unit)} x ${it.name}" },
            timestamp = transaction.timestamp,
            status = "Credit Udhar"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFFEBEE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = VyaparRed, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Udhar Tax Invoice",
                            color = VyaparTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Bill #${invoiceForPdf.firestoreId.take(10)}",
                            color = VyaparTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = VyaparTextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VyaparBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VyaparBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = storeName.uppercase(Locale.getDefault()),
                            color = VyaparRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (merchantMobile.isNotBlank()) {
                            Text(
                                text = "Contact: $merchantMobile",
                                color = VyaparTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = VyaparBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("CUSTOMER", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(customer.name, color = VyaparTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("📱 ${customer.mobileNumber}", color = VyaparTextSecondary, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("DATE & TIME", color = VyaparTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(formattedDate, color = VyaparTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Surface(
                                    color = Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 2.dp).wrapContentWidth()
                                ) {
                                    Text(
                                        text = transaction.paymentMode,
                                        color = VyaparRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = VyaparBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("PURCHASED ITEMS", color = VyaparDeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        parsedItems.forEachIndexed { idx, item ->
                            val formattedQty = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(item.quantity, item.unit)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(item.name, color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (item.unitPrice > 0) "@ ₹${String.format(Locale.US, "%.2f", item.unitPrice)} / ${item.unit}" else "",
                                        color = VyaparTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(formattedQty, color = VyaparDeepBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (item.lineTotal > 0) "₹${String.format(Locale.US, "%.2f", item.lineTotal)}" else "₹${String.format(Locale.US, "%.2f", transaction.amount)}",
                                    color = VyaparTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(70.dp)
                                )
                            }
                            if (idx < parsedItems.size - 1) {
                                HorizontalDivider(color = VyaparBorder)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = VyaparBorder)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL UDHAR BILLED", color = VyaparTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", transaction.amount)}",
                                color = VyaparRed,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val pdf = com.example.util.InvoicePdfHelper.generateInvoicePdf(
                                context = context,
                                invoice = invoiceForPdf,
                                businessName = storeName,
                                merchantMobile = merchantMobile
                            )
                            com.example.util.InvoicePdfHelper.printInvoicePdf(context, pdf)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VyaparDeepBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("udhar_modal_print_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val pdf = com.example.util.InvoicePdfHelper.generateInvoicePdf(
                                context = context,
                                invoice = invoiceForPdf,
                                businessName = storeName,
                                merchantMobile = merchantMobile
                            )
                            com.example.util.InvoicePdfHelper.shareInvoicePdfWhatsApp(
                                context = context,
                                pdfFile = pdf,
                                invoice = invoiceForPdf,
                                businessName = storeName
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("udhar_modal_whatsapp_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            val pdf = com.example.util.InvoicePdfHelper.generateInvoicePdf(
                                context = context,
                                invoice = invoiceForPdf,
                                businessName = storeName,
                                merchantMobile = merchantMobile
                            )
                            com.example.util.InvoicePdfHelper.shareInvoicePdfGeneral(
                                context = context,
                                pdfFile = pdf,
                                invoice = invoiceForPdf,
                                businessName = storeName
                            )
                        },
                        modifier = Modifier
                            .background(VyaparBg, RoundedCornerShape(8.dp))
                            .border(1.dp, VyaparBorder, RoundedCornerShape(8.dp))
                            .testTag("udhar_modal_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = VyaparTextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = VyaparRed, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = VyaparSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, VyaparBorder, RoundedCornerShape(16.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsAppReminderModalDialog(
    customer: CustomerEntity,
    businessName: String,
    merchantPhone: String = "",
    upiId: String = "merchant@upi",
    initialType: ReminderType,
    transactions: List<CustomerTransactionEntity>,
    onScheduleAuto: ((Long, String) -> Unit)? = null,
    onCancelAuto: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(initialType) }

    var selectedScheduledEpoch by remember {
        mutableStateOf(customer.reminderScheduledDate.takeIf { it > System.currentTimeMillis() } ?: 0L)
    }

    var messageText by remember(selectedType, customer, businessName, merchantPhone, upiId) {
        mutableStateOf(
            WhatsAppReminderUtils.buildReminderMessage(
                customerName = customer.name,
                businessName = businessName,
                pendingAmount = customer.totalPendingBalance,
                lastTransactionTimestamp = customer.lastTransactionTimestamp,
                reminderType = selectedType,
                transactions = transactions,
                upiId = upiId,
                merchantPhone = merchantPhone
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF25D366), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "WhatsApp Payment Reminder",
                        color = VyaparTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "To: ${customer.name} (${customer.mobileNumber})",
                        color = VyaparTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Outstanding Summary Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFEF9A9A), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Outstanding Udhar Balance:", color = VyaparTextPrimary, fontSize = 12.sp)
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", customer.totalPendingBalance)}",
                            color = VyaparRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Dynamic Merchant Profile Verification Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Store, contentDescription = "Store", tint = Color(0xFF0284C7), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Sender: $businessName", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (merchantPhone.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "📞 Contact: $merchantPhone", color = VyaparTextSecondary, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "💳 UPI ID: $upiId", color = Color(0xFF0284C7), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Interactive UPI Link Banner Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.QrCode, contentDescription = "UPI", tint = VyaparSuccess, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dynamic UPI Payment Link", color = VyaparSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = upiId, color = VyaparTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Customers can tap the interactive UPI link in WhatsApp to pay ₹${String.format(Locale.US, "%.2f", customer.totalPendingBalance)} directly.",
                            color = VyaparTextSecondary,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                com.example.util.WhatsAppReminderHelper.launchUpiPaymentIntent(
                                    context = context,
                                    upiId = upiId,
                                    merchantName = businessName,
                                    amount = customer.totalPendingBalance,
                                    note = "Udhar Clearance (${customer.name})"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VyaparSuccess),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("whatsapp_modal_test_pay_upi_button")
                        ) {
                            Icon(imageVector = Icons.Default.Payment, contentDescription = "Test Pay", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Pay via UPI App", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Automatic Scheduled Reminder Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = "Auto Reminder", tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scheduled Automatic Reminder", color = Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pick a date & time for SmartPOS to automatically dispatch this WhatsApp reminder in the background.",
                            color = VyaparTextSecondary,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedScheduledEpoch > System.currentTimeMillis()) {
                            val scheduledDateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(selectedScheduledEpoch))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⏰ $scheduledDateFormatted",
                                    color = Color(0xFF92400E),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        showDatePickerAndSchedule(context, selectedScheduledEpoch) { newEpoch ->
                                            selectedScheduledEpoch = newEpoch
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Change", color = VyaparDeepBlue, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onScheduleAuto?.invoke(selectedScheduledEpoch, messageText)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .testTag("confirm_schedule_auto_reminder_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Confirm", modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Schedule", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (customer.reminderScheduledDate > 0 && customer.reminderStatus == "SCHEDULED") {
                                    OutlinedButton(
                                        onClick = {
                                            onCancelAuto?.invoke()
                                            selectedScheduledEpoch = 0L
                                        },
                                        border = BorderStroke(1.dp, VyaparRed),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("cancel_schedule_auto_reminder_button")
                                    ) {
                                        Text("Cancel Auto", color = VyaparRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    showDatePickerAndSchedule(context, System.currentTimeMillis() + 86400000L) { newEpoch ->
                                        selectedScheduledEpoch = newEpoch
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFFD97706)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .testTag("pick_auto_reminder_datetime_button")
                            ) {
                                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📅 Pick Auto-Send Date & Time", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Reminder Type Quick Selection Chips
                Text("Select Reminder Template", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReminderType.values().forEach { type ->
                        val isSelected = selectedType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VyaparRed,
                                selectedLabelColor = Color.White,
                                containerColor = VyaparBg,
                                labelColor = VyaparTextPrimary
                            )
                        )
                    }
                }

                // Message Preview / Custom Editor
                Text("Message Preview (Editable)", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("whatsapp_reminder_message_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = VyaparTextPrimary, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg
                    )
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        WhatsAppReminderUtils.sendWhatsAppReminder(context, customer.mobileNumber, messageText)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_reminder_send_whatsapp_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send WhatsApp", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Now via WhatsApp", fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        WhatsAppReminderUtils.shareTextViaStandardChooser(context, messageText)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, VyaparBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_reminder_share_fallback_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share SMS", tint = VyaparTextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SMS / Other Share (Fallback)", color = VyaparTextPrimary, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("whatsapp_reminder_cancel_button")
            ) {
                Text("Cancel", color = VyaparTextSecondary)
            }
        },
        containerColor = VyaparSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, VyaparBorder, RoundedCornerShape(16.dp))
    )
}

/**
 * Helper to show DatePickerDialog and TimePickerDialog sequentially.
 */
private fun showDatePickerAndSchedule(
    context: android.content.Context,
    initialEpoch: Long,
    onDateTimeSelected: (Long) -> Unit
) {
    val cal = Calendar.getInstance()
    if (initialEpoch > System.currentTimeMillis()) {
        cal.timeInMillis = initialEpoch
    } else {
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
    }

    val datePicker = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timePicker = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)

                    val target = cal.timeInMillis
                    if (target <= System.currentTimeMillis()) {
                        android.widget.Toast.makeText(context, "Please select a future time", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onDateTimeSelected(target)
                    }
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            )
            timePicker.show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    datePicker.datePicker.minDate = System.currentTimeMillis()
    datePicker.show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JamaPaymentDialog(
    initialName: String,
    initialMobile: String,
    onDismiss: () -> Unit,
    onSave: (name: String, mobile: String, amount: Double, mode: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var mobile by remember { mutableStateOf(initialMobile) }
    var amountInput by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var noteInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE8F5E9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Jama",
                        tint = VyaparSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Jama Karein (Receive Payment)",
                        color = VyaparTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Clear customer's credit balance",
                        color = VyaparTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparSuccess,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jama_dialog_name_input")
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparSuccess,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jama_dialog_mobile_input")
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount Received (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparSuccess,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jama_dialog_amount_input")
                )

                Text("Payment Method", color = VyaparTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "UPI", "Online").forEach { mode ->
                        FilterChip(
                            selected = paymentMode == mode,
                            onClick = { paymentMode = mode },
                            label = { Text(mode, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VyaparSuccess,
                                selectedLabelColor = Color.White,
                                containerColor = VyaparBg,
                                labelColor = VyaparTextPrimary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note / Remarks (e.g. Monthly Settlement)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparSuccess,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jama_dialog_note_input")
                )

                errorMsg?.let {
                    Text(text = it, color = VyaparRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    if (mobile.isBlank() || amt <= 0.0) {
                        errorMsg = "Please enter valid mobile and positive payment amount"
                    } else {
                        onSave(name.ifBlank { "Customer" }, mobile, amt, paymentMode, noteInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VyaparSuccess),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("jama_dialog_save_button")
            ) {
                Text("Save Jama", fontWeight = FontWeight.Bold, color = Color.White)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUdharEntryDialog(
    initialName: String,
    initialMobile: String,
    onDismiss: () -> Unit,
    onSave: (name: String, mobile: String, amount: Double, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var mobile by remember { mutableStateOf(initialMobile) }
    var amountInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFFFEBEE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircle,
                        contentDescription = "Udhar",
                        tint = VyaparRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Record New Udhar (Credit)",
                        color = VyaparTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Add pending credit to customer ledger",
                        color = VyaparTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_udhar_dialog_name_input")
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_udhar_dialog_mobile_input")
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Udhar Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_udhar_dialog_amount_input")
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Items / Note (e.g. Kirana Ration)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_udhar_dialog_note_input")
                )

                errorMsg?.let {
                    Text(text = it, color = VyaparRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    if (mobile.isBlank() || amt <= 0.0) {
                        errorMsg = "Please enter valid mobile and Udhar amount"
                    } else {
                        onSave(name.ifBlank { "Customer" }, mobile, amt, noteInput)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VyaparRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_udhar_dialog_save_button")
            ) {
                Text("Save Udhar", fontWeight = FontWeight.Bold, color = Color.White)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceUdharEntryDialog(
    allCustomers: List<CustomerEntity>,
    initialSpeechText: String,
    onDismiss: () -> Unit,
    onTriggerMic: () -> Unit,
    onSaveUdhar: (name: String, mobile: String, amount: Double, note: String) -> Unit,
    onSaveJama: (name: String, mobile: String, amount: Double, mode: String, note: String) -> Unit
) {
    var rawSpeechText by remember { mutableStateOf(initialSpeechText.ifBlank { "Ramesh ko 2 kilo chini 100 rupaye ka udhar diya" }) }

    val parsedResult = remember(rawSpeechText, allCustomers) {
        com.example.util.VoiceHelper.parseVoiceUdharEntry(rawSpeechText, allCustomers)
    }

    var customerNameInput by remember(parsedResult) {
        mutableStateOf(parsedResult.customerName ?: "")
    }
    var customerMobileInput by remember(parsedResult) {
        mutableStateOf(parsedResult.matchedCustomer?.mobileNumber ?: "")
    }
    var amountInput by remember(parsedResult) {
        mutableStateOf(parsedResult.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    var noteInput by remember(parsedResult) {
        mutableStateOf(parsedResult.itemsOrNote ?: "")
    }
    var isJama by remember(parsedResult) {
        mutableStateOf(parsedResult.isJama)
    }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEF3C7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice", tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Voice Udhar Entry", color = VyaparTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Speak in Hindi / English", color = VyaparTextSecondary, fontSize = 11.sp)
                    }
                }

                IconButton(
                    onClick = onTriggerMic,
                    modifier = Modifier
                        .background(Color(0xFFFEF3C7), CircleShape)
                        .size(36.dp)
                        .testTag("voice_dialog_mic_button")
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Tap to speak", tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Spoken Transcript Input Field
                OutlinedTextField(
                    value = rawSpeechText,
                    onValueChange = { rawSpeechText = it },
                    label = { Text("Spoken Phrase (or type below)") },
                    trailingIcon = {
                        IconButton(onClick = onTriggerMic) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic", tint = Color(0xFFD97706))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_dialog_transcript_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = VyaparTextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Entry Type Toggle: Udhar (+) vs Jama (-)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isJama,
                        onClick = { isJama = false },
                        label = { Text("🔴 Udhar (Credit)", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VyaparRed,
                            selectedLabelColor = Color.White,
                            containerColor = VyaparBg,
                            labelColor = VyaparTextPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("voice_dialog_type_udhar")
                    )

                    FilterChip(
                        selected = isJama,
                        onClick = { isJama = true },
                        label = { Text("🟢 Jama (Received)", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VyaparSuccess,
                            selectedLabelColor = Color.White,
                            containerColor = VyaparBg,
                            labelColor = VyaparTextPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("voice_dialog_type_jama")
                    )
                }

                // Parsed Summary Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("✨ SMART EXTRACTED DETAILS", color = VyaparSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (parsedResult.matchedCustomer != null)
                                "Matched Customer: ${parsedResult.matchedCustomer.name} (${parsedResult.matchedCustomer.mobileNumber})"
                            else "Extracted Customer Name: ${parsedResult.customerName ?: "Not detected"}",
                            color = VyaparTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Extracted Amount: ₹${parsedResult.amount ?: 0.0} | Items: ${parsedResult.itemsOrNote}",
                            color = VyaparTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Customer Name Input
                OutlinedTextField(
                    value = customerNameInput,
                    onValueChange = { name ->
                        customerNameInput = name
                        val matched = allCustomers.find { it.name.equals(name, ignoreCase = true) || it.mobileNumber == name }
                        if (matched != null) {
                            customerMobileInput = matched.mobileNumber
                        }
                    },
                    label = { Text("Customer Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("voice_dialog_name_input")
                )

                // Mobile Number Input
                OutlinedTextField(
                    value = customerMobileInput,
                    onValueChange = { customerMobileInput = it },
                    label = { Text("Mobile Number (10 digits)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("voice_dialog_mobile_input")
                )

                // Amount Input
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("voice_dialog_amount_input")
                )

                // Note / Items Input
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Items / Description") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("voice_dialog_note_input")
                )

                errorMsg?.let {
                    Text(text = it, color = VyaparRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    val cleanMobile = customerMobileInput.trim()
                    val cleanName = customerNameInput.ifBlank { "Customer" }

                    if (cleanMobile.isBlank() || amt <= 0.0) {
                        errorMsg = "Please enter a valid mobile number and positive amount"
                    } else {
                        if (isJama) {
                            onSaveJama(cleanName, cleanMobile, amt, "Cash", noteInput)
                        } else {
                            onSaveUdhar(cleanName, cleanMobile, amt, noteInput)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isJama) VyaparSuccess else VyaparRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("voice_dialog_save_button")
            ) {
                Icon(
                    imageVector = if (isJama) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isJama) "Save Jama (₹${amountInput.ifBlank { "0" }})" else "Save Udhar (₹${amountInput.ifBlank { "0" }})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("voice_dialog_cancel_button")
            ) {
                Text("Cancel", color = VyaparTextSecondary)
            }
        },
        containerColor = VyaparSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.border(1.dp, VyaparBorder, RoundedCornerShape(18.dp))
    )
}
