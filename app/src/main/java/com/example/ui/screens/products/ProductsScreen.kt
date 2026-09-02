package com.example.ui.screens.products

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.ProductEntity
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.OcrLabelScannerDialog
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.PremiumGradientBackground
import com.example.ui.components.PremiumLoadingState
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.util.BarcodeLookupHelper
import com.example.util.OcrTextParser
import com.example.util.OcrParsedProduct
import kotlinx.coroutines.launch
import com.example.ui.theme.*
import com.example.ui.viewmodel.BillingViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: BillingViewModel,
    onNavigateBack: () -> Unit
) {
    val searchQuery by viewModel.productSearchQuery.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val activeCategories by viewModel.categories.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Expiry Guardian, 2: Smart Reorder List

    val now = remember { System.currentTimeMillis() }

    val expiredProducts = remember(filteredProducts) {
        filteredProducts.filter {
            com.example.util.PharmacyUtils.getExpiryStatus(it.expiryDate) is com.example.util.ExpiryStatus.Expired
        }
    }

    val criticalExpiryProducts = remember(filteredProducts, now) {
        filteredProducts.filter { product ->
            val status = com.example.util.PharmacyUtils.getExpiryStatus(product.expiryDate)
            if (status is com.example.util.ExpiryStatus.Expired) return@filter true
            val time = com.example.util.PharmacyUtils.parseExpiryDate(product.expiryDate)
            if (time != null) {
                val days = ((time - now) / (1000 * 60 * 60 * 24)).toInt()
                days < 15
            } else false
        }
    }

    val warningExpiryProducts = remember(filteredProducts, now) {
        filteredProducts.filter { product ->
            val time = com.example.util.PharmacyUtils.parseExpiryDate(product.expiryDate)
            if (time != null) {
                val days = ((time - now) / (1000 * 60 * 60 * 24)).toInt()
                days in 15..30
            } else false
        }
    }

    val allExpiryRiskProducts = remember(criticalExpiryProducts, warningExpiryProducts) {
        (criticalExpiryProducts + warningExpiryProducts).distinctBy { it.id }
    }

    val lowStockProducts = remember(filteredProducts) {
        filteredProducts.filter { product ->
            val threshold = if (product.minStockThreshold > 0.0) product.minStockThreshold else 5.0
            product.stockQuantity < threshold
        }
    }

    val activeDisplayList = remember(selectedTab, filteredProducts, allExpiryRiskProducts, lowStockProducts) {
        when (selectedTab) {
            1 -> allExpiryRiskProducts
            2 -> lowStockProducts
            else -> filteredProducts
        }
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val activeBusinessType = remember(currentUser) { com.example.util.BusinessCategoryUtils.getBusinessType(currentUser) }

    // Form inputs state
    var itemNameInput by remember { mutableStateOf("") }
    var salePriceInput by remember { mutableStateOf("") }
    var purchasePriceInput by remember { mutableStateOf("") }
    var stockInput by remember { mutableStateOf("") }
    var minStockInput by remember { mutableStateOf("5") }
    var barcodeInput by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("Pcs") }
    var selectedCategory by remember { mutableStateOf("General") }

    // Industry-specific form inputs
    var batchNumberInput by remember { mutableStateOf("") }
    var expiryDateInput by remember { mutableStateOf("") }
    var manufacturerInput by remember { mutableStateOf("") }
    var saltCompositionInput by remember { mutableStateOf("") }
    var packConfigInput by remember { mutableStateOf("") }
    var isRxRequiredInput by remember { mutableStateOf(false) }
    var sizeInput by remember { mutableStateOf("") }
    var colorInput by remember { mutableStateOf("") }

    var showScannerInDialog by remember { mutableStateOf(false) }
    var showOcrScannerInDialog by remember { mutableStateOf(false) }
    var autoFillSourceInfo by remember { mutableStateOf<String?>(null) }
    var isPerformingBarcodeLookup by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun lookupAndAutoFillBarcode(code: String) {
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) return
        isPerformingBarcodeLookup = true
        coroutineScope.launch {
            val result = BarcodeLookupHelper.lookupBarcode(cleanCode)
            isPerformingBarcodeLookup = false
            if (result != null) {
                if (itemNameInput.isBlank() || itemNameInput.startsWith("Item")) itemNameInput = result.name
                if (manufacturerInput.isBlank() && result.brandOrManufacturer.isNotBlank()) manufacturerInput = result.brandOrManufacturer
                if (result.category.isNotBlank() && (selectedCategory == "General" || selectedCategory == "General Store / Retail")) selectedCategory = result.category
                if (salePriceInput.isBlank() && result.mrpOrPrice != null) salePriceInput = result.mrpOrPrice.toString()
                if (saltCompositionInput.isBlank() && result.saltComposition.isNotBlank()) saltCompositionInput = result.saltComposition
                if (result.unit.isNotBlank()) selectedUnit = result.unit
                if (result.packUnitConfig.isNotBlank()) packConfigInput = result.packUnitConfig
                if (result.isRxRequired) isRxRequiredInput = true
                autoFillSourceInfo = "⚡ Auto-filled from Barcode Database (${result.source})"
            } else {
                autoFillSourceInfo = "⚠️ No entry found for Barcode $cleanCode in Master Catalog"
            }
        }
    }

    fun applyOcrParsedProduct(ocr: OcrParsedProduct) {
        if (ocr.name.isNotBlank()) itemNameInput = ocr.name
        if (ocr.batchNumber.isNotBlank()) batchNumberInput = ocr.batchNumber
        if (ocr.expiryDate.isNotBlank()) expiryDateInput = ocr.expiryDate
        if (ocr.mrp != null) salePriceInput = ocr.mrp.toString()
        if (ocr.manufacturer.isNotBlank()) manufacturerInput = ocr.manufacturer
        if (ocr.saltComposition.isNotBlank()) saltCompositionInput = ocr.saltComposition
        if (ocr.packConfig.isNotBlank()) packConfigInput = ocr.packConfig
        autoFillSourceInfo = "📷 Auto-filled from OCR Box Label Reader (Editable)"
    }

    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val units = remember { listOf("Pcs", "Strip", "Bottle", "Box", "Tablet", "Capsule", "Kg", "Gm", "Ltr", "Ml", "Pack", "Plate", "Portion") }

    fun openAddDialog() {
        selectedProductForEdit = null
        autoFillSourceInfo = null
        itemNameInput = ""
        salePriceInput = ""
        purchasePriceInput = ""
        stockInput = "10"
        minStockInput = "5"
        barcodeInput = ""
        batchNumberInput = ""
        expiryDateInput = ""
        manufacturerInput = ""
        saltCompositionInput = ""
        sizeInput = ""
        colorInput = ""

        when (activeBusinessType) {
            com.example.util.BusinessType.PHARMACY -> {
                selectedUnit = "Strip"
                selectedCategory = "Pharmacy / Medical"
                packConfigInput = "1 Strip = 10 Tablets"
                isRxRequiredInput = false
            }
            com.example.util.BusinessType.KIRANA -> {
                selectedUnit = "Kg"
                selectedCategory = "Kirana / Grocery"
                packConfigInput = ""
                isRxRequiredInput = false
            }
            com.example.util.BusinessType.GARMENTS -> {
                selectedUnit = "Pcs"
                selectedCategory = "Garments / Clothing"
                sizeInput = "M"
                colorInput = "Black"
                packConfigInput = ""
                isRxRequiredInput = false
            }
            com.example.util.BusinessType.RESTAURANT -> {
                selectedUnit = "Plate"
                selectedCategory = "Restaurant / Cafe / Food"
                packConfigInput = ""
                isRxRequiredInput = false
            }
            else -> {
                selectedUnit = "Pcs"
                selectedCategory = "General Store / Retail"
                packConfigInput = ""
                isRxRequiredInput = false
            }
        }
        viewModel.productFormError = null
        showAddEditDialog = true
    }

    fun openEditDialog(product: ProductEntity) {
        selectedProductForEdit = product
        autoFillSourceInfo = null
        itemNameInput = product.name
        salePriceInput = product.salePrice.toString()
        purchasePriceInput = if (product.purchasePrice > 0) product.purchasePrice.toString() else ""
        stockInput = product.stockQuantity.toString()
        minStockInput = if (product.minStockThreshold > 0.0) product.minStockThreshold.toInt().toString() else "5"
        barcodeInput = product.barcode
        selectedUnit = product.unit
        selectedCategory = product.category
        batchNumberInput = product.batchNumber
        expiryDateInput = product.expiryDate
        manufacturerInput = product.manufacturer
        saltCompositionInput = product.saltComposition
        packConfigInput = product.packUnitConfig
        isRxRequiredInput = product.isRxRequired
        sizeInput = product.size
        colorInput = product.color
        viewModel.productFormError = null
        showAddEditDialog = true
    }

    PremiumGradientBackground {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Inventory & Products",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                modifier = Modifier.testTag("products_title")
                            )
                            Text(
                                text = "${filteredProducts.size} Items Available",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("products_back_button")
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
                            onClick = { openAddDialog() },
                            modifier = Modifier.testTag("products_add_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Product",
                                tint = EmeraldGreen
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0x99090D22)
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { openAddDialog() },
                    containerColor = EmeraldGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .testTag("products_add_fab")
                        .padding(bottom = 16.dp, end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add New Product",
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.productSearchQuery.value = it },
                    placeholder = { Text("Search products or categories...", color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = EmeraldGreen
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.productSearchQuery.value = "" },
                                modifier = Modifier.testTag("products_clear_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x0DFFFFFF),
                        unfocusedContainerColor = Color(0x05FFFFFF)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("products_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category & Filter Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = EmeraldLight,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_all_products")
                    ) {
                        Surface(
                            color = if (selectedTab == 0) EmeraldGreen else Color(0x1F1E295D),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "All Products (${filteredProducts.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_expiry_tracker")
                    ) {
                        Surface(
                            color = if (selectedTab == 1) Color(0xFFEF4444) else Color(0x1F1E295D),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "⚡ Expiry Guardian (${allExpiryRiskProducts.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("tab_low_stock")
                    ) {
                        Surface(
                            color = if (selectedTab == 2) GoldYellow else Color(0x1F1E295D),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "📦 Smart Reorder List (${lowStockProducts.size})",
                                color = if (selectedTab == 2) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Summary Stats Banner for Expiry Guardian Tab
                if (selectedTab == 1) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x22131B3E)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x33EF4444), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🚨 Critical (<15 Days)", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${criticalExpiryProducts.size}", color = Color(0xFFEF4444), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.height(28.dp).width(1.dp).background(Color(0x22FFFFFF)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚠️ Warning (15-30 Days)", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${warningExpiryProducts.size}", color = Color(0xFFF59E0B), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.height(28.dp).width(1.dp).background(Color(0x22FFFFFF)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Risk Items", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${allExpiryRiskProducts.size}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Summary Stats & Actions Banner for Smart Reorder List Tab
                if (selectedTab == 2) {
                    val totalReorderCost = remember(lowStockProducts) {
                        lowStockProducts.sumOf { p ->
                            val th = if (p.minStockThreshold > 0.0) p.minStockThreshold else 5.0
                            val target = maxOf(th * 2, 10.0)
                            val suggestedQty = maxOf(1.0, target - p.stockQuantity)
                            val unitP = if (p.purchasePrice > 0.0) p.purchasePrice else p.salePrice
                            suggestedQty * unitP
                        }
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x22131B3E)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("📦 AUTOMATED PURCHASE ORDER", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Text("${lowStockProducts.size} Items Need Stock Replenishment", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    color = Color(0x22F59E0B),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Est. PO: ₹${String.format(Locale.US, "%.2f", totalReorderCost)}",
                                        color = GoldYellow,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val sb = StringBuilder()
                                        sb.append("📦 SMART POS REORDER PURCHASE ORDER\n")
                                        sb.append("Store: ${currentUser?.businessName ?: "Kirana & Retail Store"}\n\n")
                                        lowStockProducts.forEachIndexed { idx, p ->
                                            val th = if (p.minStockThreshold > 0.0) p.minStockThreshold else 5.0
                                            val target = maxOf(th * 2, 10.0)
                                            val suggestedQty = maxOf(1.0, target - p.stockQuantity)
                                            val cost = suggestedQty * (if (p.purchasePrice > 0.0) p.purchasePrice else p.salePrice)
                                            sb.append("${idx + 1}. ${p.name} - Order Qty: ${suggestedQty.toInt()} ${p.unit} (Stock: ${p.stockQuantity.toInt()}, Min: ${th.toInt()}) [Est: ₹${String.format(Locale.US, "%.2f", cost)}]\n")
                                        }
                                        sb.append("\nTotal Estimated Purchase Order Value: ₹${String.format(Locale.US, "%.2f", totalReorderCost)}\n")
                                        sb.append("Please confirm delivery timeline. Thank you!")

                                        com.example.util.WhatsAppReminderUtils.sendWhatsAppReminder(context, "", sb.toString())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("reorder_share_whatsapp")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WhatsApp PO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val sb = StringBuilder()
                                        sb.append("📦 SMART POS REORDER PURCHASE ORDER\n")
                                        sb.append("Store: ${currentUser?.businessName ?: "Retail Store"}\n\n")
                                        lowStockProducts.forEachIndexed { idx, p ->
                                            val th = if (p.minStockThreshold > 0.0) p.minStockThreshold else 5.0
                                            val target = maxOf(th * 2, 10.0)
                                            val suggestedQty = maxOf(1.0, target - p.stockQuantity)
                                            sb.append("${idx + 1}. ${p.name} - Qty: ${suggestedQty.toInt()} ${p.unit} (Stock: ${p.stockQuantity.toInt()})\n")
                                        }
                                        sb.append("\nTotal Estimated PO: ₹${String.format(Locale.US, "%.2f", totalReorderCost)}")

                                        com.example.util.WhatsAppReminderUtils.shareTextViaStandardChooser(context, sb.toString())
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("reorder_export_text")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Export", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export PO Text", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Product List
                if (activeDisplayList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Empty Inventory",
                                tint = Color(0x44FFFFFF),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (selectedTab) {
                                    1 -> "Great! No expired or near-expiry medicines found."
                                    2 -> "All products are well stocked!"
                                    else -> if (searchQuery.isEmpty()) "No products in inventory yet" else "No matching products found"
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add Product' below or click to start",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                            if (searchQuery.isEmpty() && selectedTab == 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { openAddDialog() },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("empty_add_first_product_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+ Add Your First Product",
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
                            .testTag("products_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = activeDisplayList,
                            key = { it.firestoreId.ifEmpty { it.id.toString() } }
                        ) { product ->
                            ProductItemCard(
                                product = product,
                                onEdit = { openEditDialog(product) },
                                onDelete = { productToDelete = product },
                                onQuickReplenishStock = { prod, qty ->
                                    viewModel.saveProduct(
                                        id = prod.id,
                                        firestoreId = prod.firestoreId,
                                        name = prod.name,
                                        salePrice = prod.salePrice,
                                        purchasePrice = prod.purchasePrice,
                                        stockQuantity = prod.stockQuantity + qty,
                                        minStockThreshold = prod.minStockThreshold,
                                        unit = prod.unit,
                                        category = prod.category,
                                        barcode = prod.barcode,
                                        batchNumber = prod.batchNumber,
                                        expiryDate = prod.expiryDate,
                                        manufacturer = prod.manufacturer,
                                        saltComposition = prod.saltComposition,
                                        packUnitConfig = prod.packUnitConfig,
                                        isRxRequired = prod.isRxRequired,
                                        size = prod.size,
                                        color = prod.color
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Add / Edit Product Dialog ---
        if (showAddEditDialog) {
            AlertDialog(
                onDismissRequest = { showAddEditDialog = false },
                title = {
                    Text(
                        text = if (selectedProductForEdit == null) "Add New Product" else "Edit Product",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("product_dialog_title")
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Error message
                        AnimatedVisibility(
                            visible = viewModel.productFormError != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            viewModel.productFormError?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("product_form_error")
                                )
                            }
                        }

                        // --- Smart Auto-Fill Assistant Bar ---
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x3310B981), RoundedCornerShape(12.dp))
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
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = EmeraldLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SMART AUTO-FILL ASSISTANT",
                                            color = EmeraldLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    if (isPerformingBarcodeLookup) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = EmeraldLight,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showOcrScannerInDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("scan_box_label_ocr_button")
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("📷 Scan Box Label (OCR)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showScannerInDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLight),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("scan_barcode_autofill_button")
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Barcode DB Lookup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Notification Banner if Auto-Filled
                                autoFillSourceInfo?.let { info ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0x333B82F6), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = info,
                                            color = Color(0xFF93C5FD),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { autoFillSourceInfo = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Product Name Field
                        OutlinedTextField(
                            value = itemNameInput,
                            onValueChange = { itemNameInput = it },
                            label = { Text("Item / Product Name *", color = Color(0xFF94A3B8)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showOcrScannerInDialog = true },
                                    modifier = Modifier.testTag("product_name_ocr_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraEnhance,
                                        contentDescription = "Scan Label OCR",
                                        tint = EmeraldGreen
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x22FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_name_input")
                        )

                        // Sale & Purchase Price
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = salePriceInput,
                                onValueChange = { salePriceInput = it },
                                label = { Text("Sale Price (₹) *", color = Color(0xFF94A3B8)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_sale_price_input")
                            )

                            OutlinedTextField(
                                value = purchasePriceInput,
                                onValueChange = { purchasePriceInput = it },
                                label = { Text("Purchase Price (₹)", color = Color(0xFF94A3B8)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_purchase_price_input")
                            )
                        }

                        // Stock Quantity & Reorder Alert Threshold
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = stockInput,
                                onValueChange = { stockInput = it },
                                label = { Text("Stock Quantity *", color = Color(0xFF94A3B8)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_stock_input")
                            )

                            OutlinedTextField(
                                value = minStockInput,
                                onValueChange = { minStockInput = it },
                                label = { Text("Min Reorder Alert *", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g. 5") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldYellow,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_min_stock_input")
                            )
                        }

                        // Unit Dropdown Box
                        ExposedDropdownMenuBox(
                            expanded = unitDropdownExpanded,
                            onExpandedChange = { unitDropdownExpanded = !unitDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedUnit,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unit", color = Color(0xFF94A3B8)) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = EmeraldLight,
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("product_unit_select")
                            )

                            ExposedDropdownMenu(
                                expanded = unitDropdownExpanded,
                                onDismissRequest = { unitDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF0F172A))
                            ) {
                                units.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit, color = Color.White) },
                                        onClick = {
                                            selectedUnit = unit
                                            unitDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Category Dropdown Box
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category", color = Color(0xFF94A3B8)) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Category Dropdown",
                                        tint = EmeraldLight,
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("product_category_select")
                            )

                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF0F172A))
                            ) {
                                if (activeCategories.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("General", color = Color.White) },
                                        onClick = {
                                            selectedCategory = "General"
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                } else {
                                    activeCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name, color = Color.White) },
                                            onClick = {
                                                selectedCategory = cat.name
                                                categoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Barcode Field with Scan & Lookup Buttons
                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = { newBarcode ->
                                barcodeInput = newBarcode
                                if (newBarcode.length >= 8) {
                                    lookupAndAutoFillBarcode(newBarcode)
                                }
                            },
                            label = { Text("Barcode / SKU (Optional)", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("e.g. 8901234567890") },
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (barcodeInput.isNotBlank()) {
                                        IconButton(onClick = { lookupAndAutoFillBarcode(barcodeInput) }) {
                                            Icon(Icons.Default.Search, contentDescription = "Lookup Barcode", tint = GoldYellow)
                                        }
                                    }
                                    IconButton(onClick = { showScannerInDialog = true }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = EmeraldGreen)
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_barcode_input")
                        )

                        // --- Category Specific Form Fields ---
                        if (activeBusinessType == com.example.util.BusinessType.PHARMACY) {
                            HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Pharmacy & Medicine Details",
                                color = EmeraldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = batchNumberInput,
                                    onValueChange = { batchNumberInput = it },
                                    label = { Text("Batch No.", color = Color(0xFF94A3B8)) },
                                    placeholder = { Text("e.g. BATCH-1049") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("product_batch_input")
                                )

                                OutlinedTextField(
                                    value = expiryDateInput,
                                    onValueChange = { expiryDateInput = it },
                                    label = { Text("Expiry (MM/YYYY)", color = Color(0xFF94A3B8)) },
                                    placeholder = { Text("e.g. 11/2027") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("product_expiry_input")
                                )
                            }

                            OutlinedTextField(
                                value = manufacturerInput,
                                onValueChange = { manufacturerInput = it },
                                label = { Text("Manufacturer / Brand", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g. Micro Labs / Sun Pharma") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("product_manufacturer_input")
                            )

                            OutlinedTextField(
                                value = saltCompositionInput,
                                onValueChange = { saltCompositionInput = it },
                                label = { Text("Salt / Composition Name", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g. Paracetamol 650mg") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("product_salt_input")
                            )

                            OutlinedTextField(
                                value = packConfigInput,
                                onValueChange = { packConfigInput = it },
                                label = { Text("Pack Size / Tablets Per Strip *", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g. 10 tablets/strip or 1 Strip = 10 Tablets") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("product_pack_input")
                            )

                            // Live Per-Tablet Price Calculation Card
                            val tempSalePrice = salePriceInput.toDoubleOrNull() ?: 0.0
                            val tempProd = ProductEntity(name = itemNameInput, salePrice = tempSalePrice, packUnitConfig = packConfigInput, unit = selectedUnit, category = selectedCategory, stockQuantity = 100.0)
                            val calcPackSize = com.example.util.PharmacyUtils.getPackSize(tempProd)
                            val calcPerTabPrice = com.example.util.PharmacyUtils.getPerTabletUnitPrice(tempProd)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("⚡ Loose Tablet Billing Config:", color = EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Per-Tablet Price: ₹${String.format(Locale.US, "%.2f", calcPerTabPrice)} / Tablet", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    Text("Formula: (Strip Price ₹${String.format(Locale.US, "%.2f", tempSalePrice)} ÷ $calcPackSize Tablets)", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { isRxRequiredInput = !isRxRequiredInput }.padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isRxRequiredInput,
                                    onCheckedChange = { isRxRequiredInput = it },
                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen, uncheckedColor = Color(0xFF94A3B8))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rx Prescription Required for Sale", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        } else if (activeBusinessType == com.example.util.BusinessType.GARMENTS) {
                            HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Garments & Apparel Details",
                                color = EmeraldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Size Selection Chips
                            Text("Size / Fitting Variant:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            val commonSizes = listOf("S", "M", "L", "XL", "XXL", "28", "30", "32", "34", "36")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                commonSizes.take(5).forEach { sz ->
                                    FilterChip(
                                        selected = sizeInput == sz,
                                        onClick = { sizeInput = sz },
                                        label = { Text(sz, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldGreen,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0x22FFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                commonSizes.drop(5).take(5).forEach { sz ->
                                    FilterChip(
                                        selected = sizeInput == sz,
                                        onClick = { sizeInput = sz },
                                        label = { Text(sz, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldGreen,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0x22FFFFFF),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = sizeInput,
                                    onValueChange = { sizeInput = it },
                                    label = { Text("Size (Custom / Numeric)", color = Color(0xFF94A3B8)) },
                                    placeholder = { Text("e.g. M, L, 32") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("product_size_input")
                                )

                                OutlinedTextField(
                                    value = colorInput,
                                    onValueChange = { colorInput = it },
                                    label = { Text("Color / Pattern", color = Color(0xFF94A3B8)) },
                                    placeholder = { Text("e.g. Navy Blue, Black") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("product_color_input")
                                )
                            }
                        } else if (activeBusinessType == com.example.util.BusinessType.KIRANA) {
                            HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Grocery & Loose Weight Selling",
                                color = EmeraldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = manufacturerInput,
                                onValueChange = { manufacturerInput = it },
                                label = { Text("Brand / Manufacturer (Opt)", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g. Fortune / Tata / Aashirvaad") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("product_brand_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val saleP = salePriceInput.toDoubleOrNull() ?: -1.0
                            val purP = purchasePriceInput.toDoubleOrNull() ?: 0.0
                            val stk = stockInput.toDoubleOrNull() ?: -1.0
                            val minTh = minStockInput.toDoubleOrNull() ?: 5.0

                            viewModel.saveProduct(
                                id = selectedProductForEdit?.id ?: 0,
                                firestoreId = selectedProductForEdit?.firestoreId ?: "",
                                name = itemNameInput,
                                salePrice = saleP,
                                purchasePrice = purP,
                                stockQuantity = stk,
                                minStockThreshold = minTh,
                                unit = selectedUnit,
                                category = selectedCategory,
                                barcode = barcodeInput,
                                batchNumber = batchNumberInput,
                                expiryDate = expiryDateInput,
                                manufacturer = manufacturerInput,
                                saltComposition = saltCompositionInput,
                                packUnitConfig = packConfigInput,
                                isRxRequired = isRxRequiredInput,
                                size = sizeInput,
                                color = colorInput,
                                onSuccess = {
                                    showAddEditDialog = false
                                }
                            )
                        },
                        enabled = !viewModel.isSavingProduct,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("product_dialog_save")
                    ) {
                        if (viewModel.isSavingProduct) {
                            PremiumLoadingState(text = "Saving...")
                        } else {
                            Text(
                                text = if (selectedProductForEdit == null) "Add Product" else "Update",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddEditDialog = false },
                        modifier = Modifier.testTag("product_dialog_cancel")
                    ) {
                        Text("Cancel", color = AccentPink)
                    }
                },
                containerColor = Color(0xFF131B3E),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                    .navigationBarsPadding()
            )
        }

        // --- Confirm Delete Dialog ---
        productToDelete?.let { product ->
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                title = { Text("Delete Product", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to remove '${product.name}' from your inventory?",
                        color = Color(0xFF94A3B8)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteProduct(product)
                            productToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("product_delete_confirm")
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToDelete = null }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF131B3E),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            )
        }

        // --- Barcode Scanner Dialog inside Add/Edit Product form ---
        if (showScannerInDialog) {
            BarcodeScannerDialog(
                onBarcodeScanned = { scannedCode ->
                    barcodeInput = scannedCode
                    showScannerInDialog = false
                    lookupAndAutoFillBarcode(scannedCode)
                },
                onDismiss = { showScannerInDialog = false }
            )
        }

        // --- OCR Packaging Label Scanner Dialog ---
        if (showOcrScannerInDialog) {
            OcrLabelScannerDialog(
                onOcrResultExtracted = { parsed ->
                    applyOcrParsedProduct(parsed)
                    showOcrScannerInDialog = false
                },
                onDismiss = { showOcrScannerInDialog = false }
            )
        }
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickReplenishStock: ((ProductEntity, Double) -> Unit)? = null
) {
    val minThreshold = if (product.minStockThreshold > 0.0) product.minStockThreshold else 5.0
    val isLowStock = product.stockQuantity < minThreshold

    val now = remember { System.currentTimeMillis() }
    val time = remember(product.expiryDate) { com.example.util.PharmacyUtils.parseExpiryDate(product.expiryDate) }
    val daysRemaining = remember(time, now) {
        if (time != null) ((time - now) / (1000 * 60 * 60 * 24)).toInt() else null
    }

    val expiryStatus = com.example.util.PharmacyUtils.getExpiryStatus(product.expiryDate)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when {
                    expiryStatus is com.example.util.ExpiryStatus.Expired || (daysRemaining != null && daysRemaining < 15) -> Color(0x66EF4444)
                    daysRemaining != null && daysRemaining in 15..30 -> Color(0x66F59E0B)
                    isLowStock -> Color(0x66F59E0B)
                    else -> Color(0x18FFFFFF)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name and Category Badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (product.saltComposition.isNotBlank()) {
                        Text(
                            text = "Salt: ${product.saltComposition}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (product.manufacturer.isNotBlank()) {
                        Text(
                            text = "Mfg: ${product.manufacturer}",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x228B5CF6), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = product.category,
                                color = ElectricVioletLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (product.size.isNotBlank() || product.color.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x3310B981), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val garmentTag = listOfNotNull(
                                    product.size.takeIf { it.isNotBlank() }?.let { "Size: $it" },
                                    product.color.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                Text(
                                    text = garmentTag,
                                    color = EmeraldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (product.batchNumber.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x223B82F6), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Batch: ${product.batchNumber}",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isLowStock) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LOW STOCK (<${minThreshold.toInt()})",
                                    color = Color(0xFFF87171),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Expiry status indicator badge with explicit Critical (<15d) & Warning (15-30d) categorization
                    if (product.expiryDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val (bgColor, textColor, label) = when {
                            expiryStatus is com.example.util.ExpiryStatus.Expired -> Triple(Color(0x33EF4444), Color(0xFFEF4444), "🚨 EXPIRED (${product.expiryDate})")
                            daysRemaining != null && daysRemaining < 15 -> Triple(Color(0x33EF4444), Color(0xFFEF4444), "🚨 CRITICAL EXPIRY (${daysRemaining}d left)")
                            daysRemaining != null && daysRemaining in 15..30 -> Triple(Color(0x33F59E0B), Color(0xFFFBBF24), "⚠️ WARNING NEAR EXPIRY (${daysRemaining}d left)")
                            else -> Triple(Color(0x2210B981), Color(0xFF34D399), "Exp: ${product.expiryDate}")
                        }
                        Box(
                            modifier = Modifier
                                .background(bgColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Edit & Delete Action Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("product_edit_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Product",
                            tint = EmeraldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("product_delete_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Product",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0x11FFFFFF))
            Spacer(modifier = Modifier.height(12.dp))

            // Pricing & Stock Info Row with Quick Replenish Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sale Price
                Column {
                    Text("Sale Price", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", product.salePrice)} / ${product.unit}",
                        color = EmeraldLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (com.example.util.PharmacyUtils.isPharmacyProduct(product) || product.unit.equals("Strip", ignoreCase = true) || product.packUnitConfig.isNotBlank()) {
                        val packSz = com.example.util.PharmacyUtils.getPackSize(product)
                        val perTabP = com.example.util.PharmacyUtils.getPerTabletUnitPrice(product)
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", perTabP)}/Tab ($packSz Tabs)",
                            color = GoldYellow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Purchase Price (if exists)
                if (product.purchasePrice > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Cost Price", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", product.purchasePrice)}",
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                // Stock Quantity & Quick Replenish Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("In Stock (Min: ${minThreshold.toInt()})", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        Text(
                            text = com.example.util.KiranaUnitUtils.formatQuantityWithUnit(product.stockQuantity, product.unit),
                            color = if (isLowStock) Color(0xFFF87171) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (isLowStock && onQuickReplenishStock != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = { onQuickReplenishStock(product, 10.0) },
                            color = Color(0x3310B981),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen),
                            modifier = Modifier.testTag("quick_replenish_${product.id}")
                        ) {
                            Text(
                                text = "+10 Stock",
                                color = EmeraldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
