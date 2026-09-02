package com.example.ui.screens.desktop

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

// Desktop Action & Event Bus Definition for Global Keyboard Shortcuts
sealed class DesktopAction {
    object FocusSearch : DesktopAction()
    object TriggerCheckout : DesktopAction()
    object OpenUdhar : DesktopAction()
    object PrintLastInvoice : DesktopAction()
}

object DesktopEventBus {
    private val listeners = mutableListOf<(DesktopAction) -> Unit>()

    fun subscribe(listener: (DesktopAction) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unsubscribe(listener: (DesktopAction) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(action: DesktopAction) {
        listeners.forEach { it(action) }
    }
}

// Data models for POS Billing
data class DesktopCartItem(
    val id: String,
    val name: String,
    var quantity: Double,
    val unit: String = "Pcs",
    val unitPrice: Double,
    val taxPercent: Double = 0.0
) {
    val subtotal: Double
        get() = quantity * unitPrice
}

data class DesktopProduct(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val stock: Double,
    val unit: String = "Pcs",
    val barcode: String = ""
)

@Composable
fun POSDualPaneScreen(
    onCheckout: (customerPhone: String, customerName: String, paymentMode: String, items: List<DesktopCartItem>, total: Double) -> Unit = { _, _, _, _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val cartItems = remember { mutableStateListOf<DesktopCartItem>() }
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("Walk-in Customer") }
    var selectedPaymentMode by remember { mutableStateOf("Cash") }

    val categories = listOf("All", "Kirana & Grocery", "Snacks & Drinks", "Personal Care", "Dairy & Bakery")

    // Mock/Catalog Items for Demo & Real-time Integration
    val allProducts = remember {
        listOf(
            DesktopProduct("1", "Fortune Sunflower Oil 1L", "Kirana & Grocery", 145.0, 30.0, "Packet", "8901234567890"),
            DesktopProduct("2", "Aashirvaad Shudh Chakki Atta 5kg", "Kirana & Grocery", 235.0, 18.0, "Bag", "8901234567891"),
            DesktopProduct("3", "Tata Salt Vacuum Evaporated 1kg", "Kirana & Grocery", 28.0, 50.0, "Packet", "8901234567892"),
            DesktopProduct("4", "Amul Butter Pasteurised 500g", "Dairy & Bakery", 275.0, 12.0, "Pcs", "8901234567893"),
            DesktopProduct("5", "Maggi 2-Minute Noodles 280g", "Snacks & Drinks", 48.0, 40.0, "Pack", "8901234567894"),
            DesktopProduct("6", "Coca-Cola 750ml Bottle", "Snacks & Drinks", 45.0, 25.0, "Bottle", "8901234567895"),
            DesktopProduct("7", "Dettol Original Soap 125g", "Personal Care", 65.0, 35.0, "Pcs", "8901234567896"),
            DesktopProduct("8", "Colgate Strong Teeth Toothpaste 200g", "Personal Care", 115.0, 22.0, "Tube", "8901234567897"),
            DesktopProduct("9", "Sugar / Chini (Loose)", "Kirana & Grocery", 44.0, 85.0, "Kg", "8901234567898"),
            DesktopProduct("10", "Toor Dal Premium Cleaned", "Kirana & Grocery", 160.0, 42.0, "Kg", "8901234567899")
        )
    }

    val filteredProducts = remember(searchQuery, selectedCategory) {
        allProducts.filter { product ->
            (selectedCategory == "All" || product.category == selectedCategory) &&
                    (searchQuery.isBlank() || product.name.contains(searchQuery, ignoreCase = true) || product.barcode.contains(searchQuery))
        }
    }

    fun addItemToCart(product: DesktopProduct) {
        val existingIndex = cartItems.indexOfFirst { it.id == product.id }
        if (existingIndex >= 0) {
            val item = cartItems[existingIndex]
            cartItems[existingIndex] = item.copy(quantity = item.quantity + 1.0)
        } else {
            cartItems.add(
                DesktopCartItem(
                    id = product.id,
                    name = product.name,
                    quantity = 1.0,
                    unit = product.unit,
                    unitPrice = product.price
                )
            )
        }
    }

    fun increaseQty(item: DesktopCartItem) {
        val idx = cartItems.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            cartItems[idx] = item.copy(quantity = item.quantity + 1.0)
        }
    }

    fun decreaseQty(item: DesktopCartItem) {
        val idx = cartItems.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            if (item.quantity > 1.0) {
                cartItems[idx] = item.copy(quantity = item.quantity - 1.0)
            } else {
                cartItems.removeAt(idx)
            }
        }
    }

    fun removeItem(item: DesktopCartItem) {
        cartItems.removeAll { it.id == item.id }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(VyaparBg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Pane: Catalog & Item Selection (60% width)
        Card(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = VyaparSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, VyaparBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header & Quick Search Bar with F1 hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ITEM CATALOG",
                        color = VyaparRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredProducts.size} Items Available",
                        color = VyaparTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search item by name, barcode or salt (Press F1)...", fontSize = 13.sp, color = VyaparTextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = VyaparRed) },
                    trailingIcon = {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, VyaparBorder)
                        ) {
                            Text(
                                text = "F1",
                                color = VyaparTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparRed,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) VyaparRed else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isSelected) VyaparRedDark else VyaparBorder)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else VyaparTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Product Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 175.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        Card(
                            onClick = { addItemToCart(product) },
                            colors = CardDefaults.cardColors(containerColor = VyaparBg),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, VyaparBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = product.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VyaparTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "₹${product.price}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VyaparRed
                                    )
                                    Surface(
                                        color = if (product.stock > 10) Color(0xFFE8F5E9) else Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Stock: ${product.stock.toInt()} ${product.unit}",
                                            color = if (product.stock > 10) VyaparSuccess else VyaparRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Pane: Active Bill, Cart Items & Checkout (40% width)
        DesktopCartRightPane(
            cartItems = cartItems,
            customerPhone = customerPhone,
            onCustomerPhoneChange = { customerPhone = it },
            customerName = customerName,
            onCustomerNameChange = { customerName = it },
            selectedPaymentMode = selectedPaymentMode,
            onPaymentModeChange = { selectedPaymentMode = it },
            onIncreaseQty = { increaseQty(it) },
            onDecreaseQty = { decreaseQty(it) },
            onRemoveItem = { removeItem(it) },
            onClearCart = { cartItems.clear() },
            onCheckout = {
                val total = cartItems.sumOf { it.subtotal }
                onCheckout(customerPhone, customerName, selectedPaymentMode, cartItems.toList(), total)
            },
            modifier = Modifier.weight(1.0f)
        )
    }
}

@Composable
fun DesktopCartRightPane(
    cartItems: List<DesktopCartItem>,
    customerPhone: String,
    onCustomerPhoneChange: (String) -> Unit,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    selectedPaymentMode: String,
    onPaymentModeChange: (String) -> Unit,
    onIncreaseQty: (DesktopCartItem) -> Unit,
    onDecreaseQty: (DesktopCartItem) -> Unit,
    onRemoveItem: (DesktopCartItem) -> Unit,
    onClearCart: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtotal = remember(cartItems) { cartItems.sumOf { it.subtotal } }

    // Subscribe to F2 Keyboard Shortcut Event from Global EventBus
    DisposableEffect(subtotal, customerPhone, selectedPaymentMode) {
        val listener: (DesktopAction) -> Unit = { action ->
            if (action is DesktopAction.TriggerCheckout && cartItems.isNotEmpty()) {
                onCheckout()
            }
        }
        DesktopEventBus.subscribe(listener)
        onDispose {
            DesktopEventBus.unsubscribe(listener)
        }
    }

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = VyaparSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VyaparBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Header & Clear Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(VyaparLightRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = VyaparRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CURRENT INVOICE",
                            color = VyaparTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (cartItems.isNotEmpty()) {
                        Text(
                            text = "Clear All",
                            color = VyaparRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onClearCart() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Customer Phone Input Field with Phone Icon
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = onCustomerPhoneChange,
                    placeholder = { Text("Customer Mobile Number (10 Digits)...", fontSize = 12.sp, color = VyaparTextSecondary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone",
                            tint = VyaparSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (customerPhone.length == 10) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = VyaparSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VyaparSecondary,
                        unfocusedBorderColor = VyaparBorder,
                        focusedContainerColor = VyaparBg,
                        unfocusedContainerColor = VyaparBg,
                        focusedTextColor = VyaparTextPrimary,
                        unfocusedTextColor = VyaparTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Cart Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyaparTextSecondary, modifier = Modifier.weight(2.0f))
                    Text("Qty", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyaparTextSecondary, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                    Text("Price", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyaparTextSecondary, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                    Text("Total (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VyaparTextSecondary, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.width(20.dp))
                }

                // Itemized Cart List
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cart is empty. Click items on the left to add.",
                                color = VyaparTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        items(cartItems, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(2.0f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VyaparTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "₹${item.unitPrice} / ${item.unit}",
                                        fontSize = 10.sp,
                                        color = VyaparTextSecondary
                                    )
                                }

                                // Quantity Controls
                                Row(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE2E8F0))
                                            .clickable { onDecreaseQty(item) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Minus", tint = VyaparTextPrimary, modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = "${item.quantity.toInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VyaparTextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(VyaparRed)
                                            .clickable { onIncreaseQty(item) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }

                                Text(
                                    text = "₹${item.unitPrice}",
                                    fontSize = 11.sp,
                                    color = VyaparTextSecondary,
                                    modifier = Modifier.weight(1.0f),
                                    textAlign = TextAlign.End
                                )

                                Text(
                                    text = "₹${item.subtotal}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VyaparTextPrimary,
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.End
                                )

                                IconButton(
                                    onClick = { onRemoveItem(item) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }

            // Bottom Section: Payment Modes, Live Subtotal & Checkout Button
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Payment Mode Selection
                Text("PAYMENT MODE", color = VyaparTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Cash", "UPI", "Credit (Udhar)").forEach { mode ->
                        val isSelected = selectedPaymentMode == mode
                        Surface(
                            onClick = { onPaymentModeChange(mode) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) {
                                when (mode) {
                                    "Credit (Udhar)" -> VyaparRed
                                    "UPI" -> VyaparSecondary
                                    else -> VyaparSuccess
                                }
                            } else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color.Transparent else VyaparBorder
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = mode,
                                color = if (isSelected) Color.White else VyaparTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Live Subtotal Calculation in Bold Green
                Surface(
                    color = VyaparLightGreen,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIVE SUBTOTAL (${cartItems.size} Items)",
                                color = VyaparSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Taxes & Discounts calculated",
                                color = Color(0xFF388E3C),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = "₹${String.format("%.2f", subtotal)}",
                            color = VyaparSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                }

                // Action Button: SAVE & PRINT / WHATSAPP (F2)
                Button(
                    onClick = onCheckout,
                    enabled = cartItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VyaparRed,
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print",
                        tint = if (cartItems.isNotEmpty()) Color.White else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAVE & PRINT / WHATSAPP (F2)",
                        color = if (cartItems.isNotEmpty()) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
