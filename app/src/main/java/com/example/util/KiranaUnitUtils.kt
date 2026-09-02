package com.example.util

import com.example.data.db.ProductEntity
import java.util.Locale

enum class LooseInputType {
    DUAL,
    DECIMAL,
    PHARMACY_FULL_STRIP,
    PHARMACY_LOOSE_TABLETS
}

object KiranaUnitUtils {

    val SUPPORTED_UNITS = listOf("Kg", "Gm", "Ltr", "Ml", "Pcs", "Pack", "Box", "Strip", "Meter", "Set")

    fun isLooseUnit(unit: String, product: ProductEntity? = null): Boolean {
        if (product != null && (PharmacyUtils.isPharmacyProduct(product) || product.packUnitConfig.isNotBlank())) {
            return true
        }
        val u = unit.trim().lowercase(Locale.ROOT)
        return u == "kg" || u == "gm" || u == "gram" || u == "grams" ||
               u == "ltr" || u == "l" || u == "liter" || u == "liters" || u == "ml" ||
               u == "strip" || u == "strips" || u == "tab" || u == "tablets" || u == "box"
    }

    fun isWeightUnit(unit: String): Boolean {
        val u = unit.trim().lowercase(Locale.ROOT)
        return u == "kg" || u == "gm" || u == "gram" || u == "grams"
    }

    fun isVolumeUnit(unit: String): Boolean {
        val u = unit.trim().lowercase(Locale.ROOT)
        return u == "ltr" || u == "l" || u == "liter" || u == "liters" || u == "ml"
    }

    /**
     * Formats decimal quantity to human-readable Kirana loose format:
     * e.g. 1.2 Kg -> "1 kg 200 gm"
     * e.g. 0.5 Kg -> "500 gm"
     * e.g. 1.25 Ltr -> "1 Ltr 250 ml"
     * e.g. 0.5 Ltr -> "500 ml"
     * e.g. 1200 Gm -> "1 kg 200 gm"
     * e.g. 1200 Ml -> "1 Ltr 200 ml"
     * e.g. 3 Pcs -> "3 Pcs"
     */
    fun formatQuantityWithUnit(quantity: Double, unit: String, product: ProductEntity? = null): String {
        if (product != null && (PharmacyUtils.isPharmacyProduct(product) || unit.equals("Strip", ignoreCase = true) || product.packUnitConfig.isNotBlank())) {
            return PharmacyUtils.formatPharmacyQuantity(quantity, product)
        }
        val u = unit.trim().lowercase(Locale.ROOT)
        if (u == "strip" || u == "strips") {
            val packSize = 10
            val totalTablets = Math.round(quantity * packSize).toInt()
            val fullStrips = totalTablets / packSize
            val looseTablets = totalTablets % packSize
            return when {
                fullStrips > 0 && looseTablets > 0 -> "$fullStrips Strip + $looseTablets Tablets"
                fullStrips > 0 -> "$fullStrips Strip${if (fullStrips > 1) "s" else ""}"
                looseTablets > 0 -> "$looseTablets Tablet${if (looseTablets > 1) "s" else ""}"
                else -> "0 Strip"
            }
        }
        if (quantity <= 0.0) return "0 $unit"

        return when {
            u == "kg" -> {
                val totalGrams = Math.round(quantity * 1000).toInt()
                val kgPart = totalGrams / 1000
                val gmPart = totalGrams % 1000
                when {
                    kgPart > 0 && gmPart > 0 -> "$kgPart kg $gmPart gm"
                    kgPart > 0 -> "$kgPart kg"
                    else -> "$gmPart gm"
                }
            }
            u == "ltr" || u == "l" || u == "liter" || u == "liters" -> {
                val totalMl = Math.round(quantity * 1000).toInt()
                val ltrPart = totalMl / 1000
                val mlPart = totalMl % 1000
                when {
                    ltrPart > 0 && mlPart > 0 -> "$ltrPart Ltr $mlPart ml"
                    ltrPart > 0 -> "$ltrPart Ltr"
                    else -> "$mlPart ml"
                }
            }
            u == "gm" || u == "gram" || u == "grams" -> {
                val totalGrams = Math.round(quantity).toInt()
                val kgPart = totalGrams / 1000
                val gmPart = totalGrams % 1000
                when {
                    kgPart > 0 && gmPart > 0 -> "$kgPart kg $gmPart gm"
                    kgPart > 0 -> "$kgPart kg"
                    else -> "$gmPart gm"
                }
            }
            u == "ml" -> {
                val totalMl = Math.round(quantity).toInt()
                val ltrPart = totalMl / 1000
                val mlPart = totalMl % 1000
                when {
                    ltrPart > 0 && mlPart > 0 -> "$ltrPart Ltr $mlPart ml"
                    ltrPart > 0 -> "$ltrPart Ltr"
                    else -> "$mlPart ml"
                }
            }
            else -> {
                if (quantity % 1.0 == 0.0) {
                    "${quantity.toInt()} $unit"
                } else {
                    "${String.format(Locale.US, "%.2f", quantity)} $unit"
                }
            }
        }
    }

    /**
     * Converts Dual or Decimal inputs into total quantity relative to base unit.
     */
    fun computeQuantityFromInput(
        baseUnit: String,
        inputType: LooseInputType,
        decimalValue: Double,
        primaryVal: Int,
        secondaryVal: Int
    ): Double {
        if (inputType == LooseInputType.DECIMAL) {
            return decimalValue.coerceAtLeast(0.0)
        }

        val u = baseUnit.trim().lowercase(Locale.ROOT)
        val p = primaryVal.coerceAtLeast(0)
        val s = secondaryVal.coerceAtLeast(0)

        return when (u) {
            "kg" -> p + (s / 1000.0)
            "ltr", "l", "liter", "liters" -> p + (s / 1000.0)
            "gm", "gram", "grams" -> (p * 1000.0) + s
            "ml" -> (p * 1000.0) + s
            else -> p.toDouble() + (s / 1000.0)
        }
    }

    /**
     * Converts decimal quantity back to dual values (e.g. 1.25 Kg -> Pair(1, 250)).
     */
    fun extractDualValues(quantity: Double, unit: String): Pair<Int, Int> {
        val u = unit.trim().lowercase(Locale.ROOT)
        return when (u) {
            "kg", "ltr", "l", "liter", "liters" -> {
                val totalSubUnits = Math.round(quantity * 1000).toInt()
                Pair(totalSubUnits / 1000, totalSubUnits % 1000)
            }
            "gm", "gram", "grams", "ml" -> {
                val totalSubUnits = Math.round(quantity).toInt()
                Pair(totalSubUnits / 1000, totalSubUnits % 1000)
            }
            else -> Pair(quantity.toInt(), 0)
        }
    }

    /**
     * Computes exact total item price from base price per unit and total quantity.
     * Formula: Total Item Price = Base Price per unit * Total Quantity in Decimals.
     */
    fun calculateExactPrice(basePricePerUnit: Double, quantityInBaseUnit: Double): Double {
        return (basePricePerUnit * quantityInBaseUnit).coerceAtLeast(0.0)
    }

    fun getPrimaryUnitLabel(unit: String): String {
        return if (isVolumeUnit(unit)) "Ltr" else "Kg"
    }

    fun getSecondaryUnitLabel(unit: String): String {
        return if (isVolumeUnit(unit)) "Ml" else "Grams"
    }
}
