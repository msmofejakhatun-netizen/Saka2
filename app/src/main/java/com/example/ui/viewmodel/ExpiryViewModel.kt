package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.ProductEntity
import com.example.data.repository.BillingRepository
import com.example.util.PharmacyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExpiryRiskSeverity {
    CRITICAL, // < 15 days or expired
    WARNING   // 15 - 30 days
}

data class ExpiryRiskItem(
    val product: ProductEntity,
    val daysRemaining: Int,
    val severity: ExpiryRiskSeverity,
    val expiryDateFormatted: String
)

data class ReorderItem(
    val product: ProductEntity,
    val currentStock: Double,
    val minStockThreshold: Double,
    val suggestedReorderQty: Double,
    val estimatedCost: Double
)

class ExpiryViewModel(
    private val repository: BillingRepository,
    private val userUid: String = ""
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _expiryRiskItems = MutableStateFlow<List<ExpiryRiskItem>>(emptyList())
    val expiryRiskItems: StateFlow<List<ExpiryRiskItem>> = _expiryRiskItems.asStateFlow()

    private val _criticalExpiryItems = MutableStateFlow<List<ExpiryRiskItem>>(emptyList())
    val criticalExpiryItems: StateFlow<List<ExpiryRiskItem>> = _criticalExpiryItems.asStateFlow()

    private val _warningExpiryItems = MutableStateFlow<List<ExpiryRiskItem>>(emptyList())
    val warningExpiryItems: StateFlow<List<ExpiryRiskItem>> = _warningExpiryItems.asStateFlow()

    private val _lowStockItems = MutableStateFlow<List<ReorderItem>>(emptyList())
    val lowStockItems: StateFlow<List<ReorderItem>> = _lowStockItems.asStateFlow()

    private val _hasExpiryRisk = MutableStateFlow(false)
    val hasExpiryRisk: StateFlow<Boolean> = _hasExpiryRisk.asStateFlow()

    private val _totalReorderEstimatedCost = MutableStateFlow(0.0)
    val totalReorderEstimatedCost: StateFlow<Double> = _totalReorderEstimatedCost.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            repository.getProductsStream(userUid).collectLatest { productList ->
                _products.value = productList
                processExpiryAndStock(productList)
            }
        }
    }

    private fun processExpiryAndStock(productList: List<ProductEntity>) {
        val now = System.currentTimeMillis()
        val expiryList = mutableListOf<ExpiryRiskItem>()
        val criticalList = mutableListOf<ExpiryRiskItem>()
        val warningList = mutableListOf<ExpiryRiskItem>()
        val reorderList = mutableListOf<ReorderItem>()

        for (product in productList) {
            // 1. Check Expiry Date Risk
            val expiryTime = PharmacyUtils.parseExpiryDate(product.expiryDate)
            if (expiryTime != null) {
                val diffMillis = expiryTime - now
                val daysRemaining = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

                if (daysRemaining <= 30) {
                    val severity = if (daysRemaining < 15) ExpiryRiskSeverity.CRITICAL else ExpiryRiskSeverity.WARNING
                    val riskItem = ExpiryRiskItem(
                        product = product,
                        daysRemaining = daysRemaining,
                        severity = severity,
                        expiryDateFormatted = product.expiryDate.ifEmpty { "Near Expiry" }
                    )
                    expiryList.add(riskItem)
                    if (severity == ExpiryRiskSeverity.CRITICAL) {
                        criticalList.add(riskItem)
                    } else {
                        warningList.add(riskItem)
                    }
                }
            }

            // 2. Check Low Stock & Smart Reorder Threshold
            val minThreshold = if (product.minStockThreshold > 0.0) product.minStockThreshold else 5.0
            if (product.stockQuantity < minThreshold) {
                val targetStock = maxOf(minThreshold * 2, 10.0)
                val suggestedQty = maxOf(1.0, targetStock - product.stockQuantity)
                val unitPrice = if (product.purchasePrice > 0.0) product.purchasePrice else product.salePrice
                val estimatedCost = suggestedQty * unitPrice

                reorderList.add(
                    ReorderItem(
                        product = product,
                        currentStock = product.stockQuantity,
                        minStockThreshold = minThreshold,
                        suggestedReorderQty = suggestedQty,
                        estimatedCost = estimatedCost
                    )
                )
            }
        }

        _expiryRiskItems.value = expiryList.sortedBy { it.daysRemaining }
        _criticalExpiryItems.value = criticalList.sortedBy { it.daysRemaining }
        _warningExpiryItems.value = warningList.sortedBy { it.daysRemaining }
        _hasExpiryRisk.value = expiryList.isNotEmpty()

        val sortedReorder = reorderList.sortedBy { it.currentStock }
        _lowStockItems.value = sortedReorder
        _totalReorderEstimatedCost.value = sortedReorder.sumOf { it.estimatedCost }
    }

    /**
     * Updates minStockThreshold for a specific product entity.
     */
    fun updateMinStockThreshold(product: ProductEntity, newThreshold: Double, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val updated = product.copy(minStockThreshold = newThreshold)
            repository.saveProduct(userUid, updated)
            onComplete?.invoke()
        }
    }

    /**
     * Quickly replenishes inventory stock for a product from the Smart Reorder screen.
     */
    fun quickReplenishStock(product: ProductEntity, addQuantity: Double, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val updated = product.copy(stockQuantity = product.stockQuantity + addQuantity)
            repository.saveProduct(userUid, updated)
            onComplete?.invoke()
        }
    }

    /**
     * Auto-generates a formatted Purchase Order text message for WhatsApp or Export.
     */
    fun generateReorderSummaryMessage(distributorName: String = "", storeName: String = ""): String {
        val reorders = _lowStockItems.value
        val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        val bizName = storeName.ifEmpty { "Kirana & Retail Store" }

        val sb = StringBuilder()
        sb.append("📦 SMART REORDER PURCHASE ORDER\n")
        if (distributorName.isNotBlank()) sb.append("To Distributor: $distributorName\n")
        sb.append("Store: $bizName\n")
        sb.append("Date: $dateStr\n\n")

        if (reorders.isEmpty()) {
            sb.append("All inventory items are currently well-stocked above minimum thresholds!")
            return sb.toString()
        }

        sb.append("--- ITEMS TO REORDER (${reorders.size} Items) ---\n")
        reorders.forEachIndexed { index, item ->
            val p = item.product
            val formattedStock = if (item.currentStock % 1.0 == 0.0) item.currentStock.toInt().toString() else String.format(Locale.US, "%.1f", item.currentStock)
            val formattedQty = if (item.suggestedReorderQty % 1.0 == 0.0) item.suggestedReorderQty.toInt().toString() else String.format(Locale.US, "%.1f", item.suggestedReorderQty)
            val costStr = String.format(Locale.US, "%.2f", item.estimatedCost)

            sb.append("${index + 1}. ${p.name}\n")
            if (p.batchNumber.isNotBlank()) sb.append("   Batch: ${p.batchNumber} | Category: ${p.category}\n")
            sb.append("   Reorder Qty: $formattedQty ${p.unit} (Current Stock: $formattedStock, Min: ${item.minStockThreshold.toInt()})\n")
            sb.append("   Est. Purchase Cost: ₹$costStr\n\n")
        }

        val totalCostStr = String.format(Locale.US, "%.2f", _totalReorderEstimatedCost.value)
        sb.append("Total Estimated PO Amount: ₹$totalCostStr\n")
        sb.append("Please confirm item availability and delivery ETA.\n")
        sb.append("Thank you!")

        return sb.toString()
    }

    /**
     * Shares the Smart Reorder Purchase Order via WhatsApp or Chooser.
     */
    fun shareReorderListWhatsApp(context: Context, distributorMobile: String = "", storeName: String = "") {
        val message = generateReorderSummaryMessage("", storeName)
        com.example.util.WhatsAppReminderUtils.sendWhatsAppReminder(context, distributorMobile, message)
    }

    /**
     * Share formatted text via system chooser.
     */
    fun exportReorderListText(context: Context, storeName: String = "") {
        val message = generateReorderSummaryMessage("", storeName)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Purchase Order")
        context.startActivity(shareIntent)
    }
}

class ExpiryViewModelFactory(
    private val repository: BillingRepository,
    private val userUid: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpiryViewModel::class.java)) {
            return ExpiryViewModel(repository, userUid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
