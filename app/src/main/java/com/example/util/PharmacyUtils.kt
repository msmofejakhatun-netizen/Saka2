package com.example.util

import com.example.data.db.ProductEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class ExpiryStatus {
    object Expired : ExpiryStatus()
    data class NearExpiry(val daysRemaining: Int) : ExpiryStatus()
    object Valid : ExpiryStatus()
    object NotSpecified : ExpiryStatus()
}

object PharmacyUtils {

    /**
     * Parses expiry date strings like "11/2026", "11/26", "2026-11", "11-2026", "15/11/2026".
     * Returns timestamp of end of month or date, or null if invalid.
     */
    fun parseExpiryDate(expiryStr: String): Long? {
        if (expiryStr.isBlank()) return null
        val clean = expiryStr.trim()
        val formats = listOf("MM/yyyy", "MM/yy", "yyyy-MM", "MM-yyyy", "dd/MM/yyyy")
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(clean)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    // If standard month/year, set to end of month
                    if (!format.startsWith("dd")) {
                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    }
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun getExpiryStatus(expiryStr: String, nearExpiryDaysThreshold: Int = 90): ExpiryStatus {
        val expiryTime = parseExpiryDate(expiryStr) ?: return ExpiryStatus.NotSpecified
        val now = System.currentTimeMillis()
        if (expiryTime < now) {
            return ExpiryStatus.Expired
        }
        val diffMillis = expiryTime - now
        val daysRemaining = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        return if (daysRemaining <= nearExpiryDaysThreshold) {
            ExpiryStatus.NearExpiry(daysRemaining)
        } else {
            ExpiryStatus.Valid
        }
    }

    fun isPharmacyProduct(product: ProductEntity): Boolean {
        val cat = product.category.lowercase(Locale.ROOT)
        return cat.contains("pharmacy") || cat.contains("medical") ||
                product.batchNumber.isNotBlank() || product.saltComposition.isNotBlank()
    }

    fun matchesPharmacySearch(product: ProductEntity, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase(Locale.ROOT)
        return product.name.lowercase(Locale.ROOT).contains(q) ||
                product.saltComposition.lowercase(Locale.ROOT).contains(q) ||
                product.batchNumber.lowercase(Locale.ROOT).contains(q) ||
                product.manufacturer.lowercase(Locale.ROOT).contains(q) ||
                product.barcode.lowercase(Locale.ROOT).contains(q) ||
                product.category.lowercase(Locale.ROOT).contains(q)
    }

    /**
     * Extracts tablets/capsules per strip or pack size for pharmacy items.
     * e.g., "1 Strip = 10 Tablets" -> 10
     * e.g., "15 Tablets" -> 15
     */
    fun getPackSize(product: ProductEntity): Int {
        val config = product.packUnitConfig.trim()
        if (config.isNotBlank()) {
            val digits = config.replace(Regex("[^0-9]"), " ").trim().split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }
            if (digits.isNotEmpty()) {
                val maxDigit = digits.maxOrNull() ?: 10
                if (maxDigit > 1) return maxDigit
            }
        }
        val unitLower = product.unit.lowercase(Locale.ROOT)
        if (unitLower == "strip" || unitLower == "box" || isPharmacyProduct(product)) {
            return 10
        }
        return 1
    }

    /**
     * Calculates unit price per loose tablet = Strip Price / Pack Size.
     */
    fun getPerTabletUnitPrice(product: ProductEntity): Double {
        val packSize = getPackSize(product)
        return if (packSize > 0) product.salePrice / packSize else product.salePrice
    }

    /**
     * Formats pharmacy quantity into clear unit labels (e.g. "3 Tablets", "1 Strip + 3 Tablets", "2 Strips").
     */
    fun formatPharmacyQuantity(quantity: Double, product: ProductEntity): String {
        val packSize = getPackSize(product)
        if (packSize <= 1) {
            return if (quantity % 1.0 == 0.0) "${quantity.toInt()} ${product.unit}" else "${String.format(Locale.US, "%.2f", quantity)} ${product.unit}"
        }
        val totalTablets = Math.round(quantity * packSize).toInt()
        val fullStrips = totalTablets / packSize
        val looseTablets = totalTablets % packSize
        return when {
            fullStrips > 0 && looseTablets > 0 -> "$fullStrips Strip + $looseTablets Tablets"
            fullStrips > 0 -> "$fullStrips Strip${if (fullStrips > 1) "s" else ""}"
            looseTablets > 0 -> "$looseTablets Tablet${if (looseTablets > 1) "s" else ""}"
            else -> "0 ${product.unit}"
        }
    }
}
