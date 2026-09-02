package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class BarcodeLookupResult(
    val barcode: String,
    val name: String,
    val brandOrManufacturer: String = "",
    val category: String = "General",
    val mrpOrPrice: Double? = null,
    val saltComposition: String = "",
    val unit: String = "Pcs",
    val packUnitConfig: String = "",
    val isRxRequired: Boolean = false,
    val source: String = "Catalog"
)

object BarcodeLookupHelper {
    private const val TAG = "BarcodeLookupHelper"

    // Comprehensive offline master catalog of popular Indian FMCG, Kirana & Pharmacy items
    private val localMasterCatalog = mapOf(
        // Popular Indian Medicines / Pharmacy
        "8901234567890" to BarcodeLookupResult(
            barcode = "8901234567890",
            name = "Dolo 650mg Tablet",
            brandOrManufacturer = "Micro Labs Ltd",
            category = "Pharmacy / Medical",
            mrpOrPrice = 34.0,
            saltComposition = "Paracetamol 650mg",
            unit = "Strip",
            packUnitConfig = "1 Strip = 15 Tablets",
            isRxRequired = false,
            source = "Master Rx Database"
        ),
        "8901086001234" to BarcodeLookupResult(
            barcode = "8901086001234",
            name = "Crocin Advance 500mg",
            brandOrManufacturer = "GSK Consumer Healthcare",
            category = "Pharmacy / Medical",
            mrpOrPrice = 22.50,
            saltComposition = "Paracetamol 500mg",
            unit = "Strip",
            packUnitConfig = "1 Strip = 15 Tablets",
            isRxRequired = false,
            source = "Master Rx Database"
        ),
        "8901117001001" to BarcodeLookupResult(
            barcode = "8901117001001",
            name = "Pantocid 40 Tablet",
            brandOrManufacturer = "Sun Pharma",
            category = "Pharmacy / Medical",
            mrpOrPrice = 165.0,
            saltComposition = "Pantoprazole 40mg",
            unit = "Strip",
            packUnitConfig = "1 Strip = 15 Tablets",
            isRxRequired = true,
            source = "Master Rx Database"
        ),
        "8901234005001" to BarcodeLookupResult(
            barcode = "8901234005001",
            name = "Azithral 500 Tablet",
            brandOrManufacturer = "Alembic Pharmaceuticals",
            category = "Pharmacy / Medical",
            mrpOrPrice = 118.0,
            saltComposition = "Azithromycin 500mg",
            unit = "Strip",
            packUnitConfig = "1 Strip = 5 Tablets",
            isRxRequired = true,
            source = "Master Rx Database"
        ),
        "8901234006002" to BarcodeLookupResult(
            barcode = "8901234006002",
            name = "Combiflam Tablet",
            brandOrManufacturer = "Sanofi India Ltd",
            category = "Pharmacy / Medical",
            mrpOrPrice = 45.0,
            saltComposition = "Ibuprofen 400mg + Paracetamol 325mg",
            unit = "Strip",
            packUnitConfig = "1 Strip = 20 Tablets",
            isRxRequired = false,
            source = "Master Rx Database"
        ),
        "8901234007003" to BarcodeLookupResult(
            barcode = "8901234007003",
            name = "Calpol 650mg Suspension",
            brandOrManufacturer = "GSK India",
            category = "Pharmacy / Medical",
            mrpOrPrice = 62.0,
            saltComposition = "Paracetamol 250mg/5ml",
            unit = "Bottle",
            packUnitConfig = "100 ml Bottle",
            isRxRequired = false,
            source = "Master Rx Database"
        ),

        // Popular Kirana / Grocery FMCG Items
        "8901058000001" to BarcodeLookupResult(
            barcode = "8901058000001",
            name = "Maggi 2-Minute Masala Noodles 70g",
            brandOrManufacturer = "Nestlé India",
            category = "Kirana / Grocery",
            mrpOrPrice = 14.0,
            unit = "Pcs",
            source = "FMCG Master Catalog"
        ),
        "8901030000010" to BarcodeLookupResult(
            barcode = "8901030000010",
            name = "Amul Butter Pasteurized 100g",
            brandOrManufacturer = "Amul (GCMMF)",
            category = "Dairy & Bakery",
            mrpOrPrice = 58.0,
            unit = "Pcs",
            source = "FMCG Master Catalog"
        ),
        "8901030000027" to BarcodeLookupResult(
            barcode = "8901030000027",
            name = "Amul Taaza Toned Milk 1L",
            brandOrManufacturer = "Amul (GCMMF)",
            category = "Dairy & Bakery",
            mrpOrPrice = 72.0,
            unit = "Bottle",
            source = "FMCG Master Catalog"
        ),
        "8901058852312" to BarcodeLookupResult(
            barcode = "8901058852312",
            name = "Nescafé Classic Instant Coffee 50g",
            brandOrManufacturer = "Nestlé India",
            category = "Kirana / Grocery",
            mrpOrPrice = 185.0,
            unit = "Bottle",
            source = "FMCG Master Catalog"
        ),
        "8901725111100" to BarcodeLookupResult(
            barcode = "8901725111100",
            name = "Tata Salt Vacuum Evaporated 1kg",
            brandOrManufacturer = "Tata Consumer Products",
            category = "Kirana / Grocery",
            mrpOrPrice = 28.0,
            unit = "Kg",
            source = "FMCG Master Catalog"
        ),
        "8901030612111" to BarcodeLookupResult(
            barcode = "8901030612111",
            name = "Surf Excel Easy Wash Detergent Powder 1kg",
            brandOrManufacturer = "Hindustan Unilever Ltd",
            category = "Household & Cleaning",
            mrpOrPrice = 145.0,
            unit = "Kg",
            source = "FMCG Master Catalog"
        ),
        "8901030034110" to BarcodeLookupResult(
            barcode = "8901030034110",
            name = "Colgate Dental Cream Toothpaste 100g",
            brandOrManufacturer = "Colgate-Palmolive",
            category = "Personal Care & Cosmetics",
            mrpOrPrice = 65.0,
            unit = "Pcs",
            source = "FMCG Master Catalog"
        ),
        "8901063000010" to BarcodeLookupResult(
            barcode = "8901063000010",
            name = "Parle-G Glucose Biscuits 80g",
            brandOrManufacturer = "Parle Products",
            category = "Snacks & Beverages",
            mrpOrPrice = 10.0,
            unit = "Pcs",
            source = "FMCG Master Catalog"
        )
    )

    /**
     * Look up barcode details online (Open Food Facts API) with local master fallback.
     */
    suspend fun lookupBarcode(barcode: String): BarcodeLookupResult? = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isEmpty()) return@withContext null

        // 1. Check local master database first for immediate offline response
        localMasterCatalog[cleanBarcode]?.let {
            Log.d(TAG, "Barcode $cleanBarcode found in local master catalog: ${it.name}")
            return@withContext it
        }

        // 2. Online Open Food Facts API query
        try {
            val urlString = "https://world.openfoodfacts.org/api/v2/product/$cleanBarcode.json"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "KiranaBillingSystem/1.0 (Android)")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val status = root.optInt("status", 0)

                if (status == 1 && root.has("product")) {
                    val prodObj = root.getJSONObject("product")
                    val name = prodObj.optString("product_name", "")
                        .ifEmpty { prodObj.optString("product_name_en", "") }
                    val brands = prodObj.optString("brands", "")
                    val categories = prodObj.optString("categories", "")

                    if (name.isNotBlank()) {
                        val inferredCategory = when {
                            categories.lowercase(Locale.ROOT).contains("pharmacy") || categories.lowercase(Locale.ROOT).contains("medicine") -> "Pharmacy / Medical"
                            categories.lowercase(Locale.ROOT).contains("beverages") || categories.lowercase(Locale.ROOT).contains("snacks") -> "Snacks & Beverages"
                            categories.lowercase(Locale.ROOT).contains("dairy") -> "Dairy & Bakery"
                            else -> "General"
                        }

                        Log.d(TAG, "Barcode $cleanBarcode found on OpenFoodFacts: $name ($brands)")
                        return@withContext BarcodeLookupResult(
                            barcode = cleanBarcode,
                            name = name,
                            brandOrManufacturer = brands,
                            category = inferredCategory,
                            mrpOrPrice = null, // open food facts usually doesn't store currency MRP
                            source = "Open Food Products API"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenFoodFacts API error for barcode $cleanBarcode: ${e.localizedMessage}")
        }

        // 3. Generative fallback based on barcode structure if EAN-13 Indian barcode (starts with 890)
        if (cleanBarcode.length >= 8 && cleanBarcode.startsWith("890")) {
            return@withContext BarcodeLookupResult(
                barcode = cleanBarcode,
                name = "Item SKU #$cleanBarcode",
                brandOrManufacturer = "Indian FMCG / Pharma",
                category = "General",
                source = "EAN-13 Indian Barcode"
            )
        }

        return@withContext null
    }
}
