package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.InvoiceEntity
import com.example.data.db.ProductEntity
import com.example.data.repository.BillingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ProfitAnalytics(
    val totalRevenue: Double = 0.0,
    val totalCostOfGoods: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitMarginPercentage: Double = 0.0,
    val todayRevenue: Double = 0.0,
    val todayCostOfGoods: Double = 0.0,
    val todayNetProfit: Double = 0.0,
    val todayProfitMarginPercentage: Double = 0.0
)

data class PeakHourSlot(
    val slotLabel: String,         // e.g. "6 AM - 9 AM"
    val startHour: Int,           // 6
    val endHour: Int,             // 9
    val totalSales: Double,
    val transactionCount: Int,
    val percentageOfTotal: Double
)

data class PeakHoursAnalytics(
    val peakWindowFormatted: String = "Peak Sales Time: N/A",
    val peakSlotName: String = "No Data",
    val peakSlotSales: Double = 0.0,
    val peakSlotTransactions: Int = 0,
    val peakPercentage: Double = 0.0,
    val hourlySlots: List<PeakHourSlot> = emptyList()
)

data class DailySalesTrend(
    val dayLabel: String,          // e.g. "Mon", "Tue"
    val dateMillis: Long,
    val totalSales: Double,
    val totalProfit: Double,
    val transactionCount: Int
)

class AnalyticsViewModel(
    private val repository: BillingRepository,
    private val userUid: String = ""
) : ViewModel() {

    private val _invoices = repository.getInvoicesStream(userUid)
    private val _products = repository.getProductsStream(userUid)

    val profitAnalytics: StateFlow<ProfitAnalytics> = combine(_invoices, _products) { invoices, products ->
        calculateProfitAnalytics(invoices, products)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfitAnalytics())

    val peakHoursAnalytics: StateFlow<PeakHoursAnalytics> = _invoices.map { invs ->
        calculatePeakHoursAnalytics(invs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeakHoursAnalytics())

    val weeklySalesTrends: StateFlow<List<DailySalesTrend>> = combine(_invoices, _products) { invoices, products ->
        calculateWeeklySalesTrends(invoices, products)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun calculateInvoiceCOGS(invoice: InvoiceEntity, productsMap: Map<String, ProductEntity>): Double {
            var cogs = 0.0
            if (invoice.itemsJson.isNotBlank()) {
                try {
                    val array = JSONArray(invoice.itemsJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val qty = obj.optDouble("quantity", 1.0)
                        val itemUnitPrice = obj.optDouble("unitPrice", 0.0)

                        var costPrice = 0.0
                        if (obj.has("purchasePrice") && obj.getDouble("purchasePrice") > 0) {
                            costPrice = obj.getDouble("purchasePrice")
                        } else if (obj.has("costPrice") && obj.getDouble("costPrice") > 0) {
                            costPrice = obj.getDouble("costPrice")
                        } else {
                            val matchedProd = productsMap[name.lowercase(Locale.ROOT)]
                            if (matchedProd != null && matchedProd.purchasePrice > 0) {
                                costPrice = matchedProd.purchasePrice
                            } else if (matchedProd != null && matchedProd.salePrice > 0) {
                                costPrice = matchedProd.salePrice * 0.70
                            } else if (itemUnitPrice > 0) {
                                costPrice = itemUnitPrice * 0.70
                            }
                        }
                        cogs += costPrice * qty
                    }
                    return cogs
                } catch (e: Exception) {
                    // Fall back to itemsSummary parsing
                }
            }

            if (invoice.itemsSummary.isNotBlank()) {
                val items = invoice.itemsSummary.split(",")
                for (itemStr in items) {
                    val clean = itemStr.trim()
                    if (clean.isBlank()) continue
                    val matchedProd = productsMap.values.firstOrNull { clean.contains(it.name, ignoreCase = true) }
                    if (matchedProd != null) {
                        val cost = if (matchedProd.purchasePrice > 0) matchedProd.purchasePrice else matchedProd.salePrice * 0.70
                        cogs += cost
                    }
                }
            }

            if (cogs == 0.0 && invoice.amount > 0) {
                cogs = invoice.amount * 0.70
            }

            return cogs
        }

        fun calculateProfitAnalytics(
            invoices: List<InvoiceEntity>,
            products: List<ProductEntity>
        ): ProfitAnalytics {
            if (invoices.isEmpty()) return ProfitAnalytics()

            val prodMap = products.associateBy { it.name.lowercase(Locale.ROOT) }

            val calendar = Calendar.getInstance()
            val startOfDay = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            var totalRev = 0.0
            var totalCogs = 0.0
            var todayRev = 0.0
            var todayCogs = 0.0

            for (inv in invoices) {
                val rev = inv.amount
                val cogs = calculateInvoiceCOGS(inv, prodMap)

                totalRev += rev
                totalCogs += cogs

                if (inv.timestamp >= startOfDay) {
                    todayRev += rev
                    todayCogs += cogs
                }
            }

            val netProfit = (totalRev - totalCogs).coerceAtLeast(0.0)
            val marginPct = if (totalRev > 0) (netProfit / totalRev) * 100.0 else 0.0

            val todayNetProfit = (todayRev - todayCogs).coerceAtLeast(0.0)
            val todayMarginPct = if (todayRev > 0) (todayNetProfit / todayRev) * 100.0 else 0.0

            return ProfitAnalytics(
                totalRevenue = totalRev,
                totalCostOfGoods = totalCogs,
                netProfit = netProfit,
                profitMarginPercentage = marginPct,
                todayRevenue = todayRev,
                todayCostOfGoods = todayCogs,
                todayNetProfit = todayNetProfit,
                todayProfitMarginPercentage = todayMarginPct
            )
        }

        fun calculatePeakHoursAnalytics(invoices: List<InvoiceEntity>): PeakHoursAnalytics {
            if (invoices.isEmpty()) return PeakHoursAnalytics()

            val slots = listOf(
                PeakHourSlot("6 AM - 9 AM", 6, 9, 0.0, 0, 0.0),
                PeakHourSlot("9 AM - 12 PM", 9, 12, 0.0, 0, 0.0),
                PeakHourSlot("12 PM - 3 PM", 12, 15, 0.0, 0, 0.0),
                PeakHourSlot("3 PM - 6 PM", 15, 18, 0.0, 0, 0.0),
                PeakHourSlot("6 PM - 9 PM", 18, 21, 0.0, 0, 0.0),
                PeakHourSlot("9 PM - 6 AM", 21, 6, 0.0, 0, 0.0)
            ).toMutableList()

            var totalSalesAll = 0.0
            val cal = Calendar.getInstance()

            for (inv in invoices) {
                totalSalesAll += inv.amount
                cal.timeInMillis = inv.timestamp
                val hour = cal.get(Calendar.HOUR_OF_DAY)

                val slotIndex = when (hour) {
                    in 6..8 -> 0
                    in 9..11 -> 1
                    in 12..14 -> 2
                    in 15..17 -> 3
                    in 18..20 -> 4
                    else -> 5
                }

                val current = slots[slotIndex]
                slots[slotIndex] = current.copy(
                    totalSales = current.totalSales + inv.amount,
                    transactionCount = current.transactionCount + 1
                )
            }

            val updatedSlots = slots.map { slot ->
                val pct = if (totalSalesAll > 0) (slot.totalSales / totalSalesAll) * 100.0 else 0.0
                slot.copy(percentageOfTotal = pct)
            }

            val peakSlot = updatedSlots.maxByOrNull { it.totalSales } ?: updatedSlots[0]

            val windowLabel = if (peakSlot.totalSales > 0) {
                "Peak Sales Time: ${peakSlot.slotLabel}"
            } else {
                "Peak Sales Time: 6 PM - 8 PM"
            }

            return PeakHoursAnalytics(
                peakWindowFormatted = windowLabel,
                peakSlotName = peakSlot.slotLabel,
                peakSlotSales = peakSlot.totalSales,
                peakSlotTransactions = peakSlot.transactionCount,
                peakPercentage = peakSlot.percentageOfTotal,
                hourlySlots = updatedSlots
            )
        }

        fun calculateWeeklySalesTrends(
            invoices: List<InvoiceEntity>,
            products: List<ProductEntity>
        ): List<DailySalesTrend> {
            val prodMap = products.associateBy { it.name.lowercase(Locale.ROOT) }

            val resultList = mutableListOf<DailySalesTrend>()
            val sdf = SimpleDateFormat("EEE", Locale.US)

            for (i in 6 downTo 0) {
                val dateCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val startMs = dateCal.timeInMillis
                val endMs = startMs + (24 * 60 * 60 * 1000) - 1
                val label = sdf.format(Date(startMs))

                var daySales = 0.0
                var dayProfit = 0.0
                var count = 0

                for (inv in invoices) {
                    if (inv.timestamp in startMs..endMs) {
                        daySales += inv.amount
                        val cogs = calculateInvoiceCOGS(inv, prodMap)
                        dayProfit += (inv.amount - cogs).coerceAtLeast(0.0)
                        count++
                    }
                }

                resultList.add(
                    DailySalesTrend(
                        dayLabel = label,
                        dateMillis = startMs,
                        totalSales = daySales,
                        totalProfit = dayProfit,
                        transactionCount = count
                    )
                )
            }

            return resultList
        }
    }
}

class AnalyticsViewModelFactory(
    private val repository: BillingRepository,
    private val userUid: String = ""
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository, userUid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
