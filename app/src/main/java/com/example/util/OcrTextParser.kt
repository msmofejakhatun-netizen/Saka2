package com.example.util

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

data class OcrParsedProduct(
    val name: String = "",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val mrp: Double? = null,
    val manufacturer: String = "",
    val saltComposition: String = "",
    val packConfig: String = "",
    val rawText: String = ""
)

object OcrTextParser {
    private const val TAG = "OcrTextParser"

    // Recognized Pharma / FMCG Manufacturers in India
    private val knownBrands = listOf(
        "Micro Labs", "Sun Pharma", "GSK", "GlaxoSmithKline", "Cipla", "Abbott",
        "Mankind", "Alkem", "Torrent", "Dr Reddy", "Lupin", "Sanofi", "Alembic",
        "Zydus", "Piramal", "Pfizer", "Novartis", "AstraZeneca", "Glenmark", "Intas",
        "Nestle", "Amul", "Dabur", "Hindustan Unilever", "P&G", "Procter & Gamble",
        "Colgate", "Britannia", "Parle", "Tata", "Fortune", "ITC", "Godrej"
    )

    fun parseRecognizedText(rawText: String): OcrParsedProduct {
        if (rawText.isBlank()) return OcrParsedProduct()

        val lines = rawText.split("\n", "\r").map { it.trim() }.filter { it.isNotBlank() }
        val textUpper = rawText.uppercase(Locale.ROOT)

        var extractedName = ""
        var extractedBatch = ""
        var extractedExpiry = ""
        var extractedMrp: Double? = null
        var extractedManufacturer = ""
        var extractedSalt = ""
        var extractedPack = ""

        // 1. Extract Batch Number
        // Matches BATCH, B.NO, B/N, LOT NO, BATCH NO, B.No.:
        val batchRegex = Regex("""(?:BATCH|B\.?\s*NO\.?|B/N|LOT|B/NO|BATCH\s*NO)[\s:.\-#]*([A-Za-z0-9\-/]+)""", RegexOption.IGNORE_CASE)
        batchRegex.find(rawText)?.let { match ->
            val value = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (value.length in 2..20 && !value.equals("NO", ignoreCase = true)) {
                extractedBatch = value.uppercase(Locale.ROOT)
            }
        }

        // 2. Extract Expiry Date
        // Formats: EXP: 08/27, EXP DATE: 11/2026, EXP 05/2028, 08/2027, EXP: MAY-2027, EXP: 08-27
        val expRegex = Regex("""(?:EXP|EXPIRY|EXP\.?\s*DATE|EXP\.|USE\s*BEFORE|MFG/EXP)[\s:.\-#]*([0-9]{1,2}[/\-.:][0-9]{2,4}|[A-Za-z]{3}[/\-.:]?[0-9]{2,4})""", RegexOption.IGNORE_CASE)
        expRegex.find(rawText)?.let { match ->
            val rawExp = match.groupValues.getOrNull(1)?.trim() ?: ""
            extractedExpiry = formatExpiryString(rawExp)
        }

        // Fallback standalone date if EXP keyword wasn't present
        if (extractedExpiry.isBlank()) {
            val standaloneExpRegex = Regex("""\b(0[1-9]|1[0-2])[/\-.](20[2-9][0-9]|[2-9][0-9])\b""")
            standaloneExpRegex.find(rawText)?.let { match ->
                extractedExpiry = formatExpiryString(match.value)
            }
        }

        // 3. Extract MRP / Price
        // Formats: "MRP Rs. 120.00", "MRP ₹120", "M.R.P. 45.50", "Rs. 150.00", "MRP: Rs. 165.00"
        val mrpRegex = Regex("""(?:MRP|M\.R\.P\.?|Rs\.?|₹|PRICE)[\s:.\-#]*([0-9]+\.?[0-9]*)""", RegexOption.IGNORE_CASE)
        mrpRegex.find(rawText)?.let { match ->
            val priceVal = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            if (priceVal != null && priceVal > 0.0 && priceVal < 100000.0) {
                extractedMrp = priceVal
            }
        }

        // 4. Extract Manufacturer Name
        val mfrRegex = Regex("""(?:MFG\s*BY|MFR\s*BY|MANUFACTURED\s*BY|MARKETED\s*BY|MFR\.|FABRIQUE\s*PAR)[\s:.\-#]*(.+)""", RegexOption.IGNORE_CASE)
        mfrRegex.find(rawText)?.let { match ->
            val mfrCandidate = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (mfrCandidate.length in 3..50) {
                extractedManufacturer = mfrCandidate
            }
        }

        if (extractedManufacturer.isBlank()) {
            // Match against known brands
            for (brand in knownBrands) {
                if (textUpper.contains(brand.uppercase(Locale.ROOT))) {
                    extractedManufacturer = brand
                    break
                }
            }
        }

        // 5. Extract Salt / Active Composition
        val saltRegex = Regex("""(?:Composition|Contains|Salt|Active\s*Ingredient|Each\s*tablet\s*contains)[\s:.\-#]*(.+)""", RegexOption.IGNORE_CASE)
        saltRegex.find(rawText)?.let { match ->
            val saltCandidate = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (saltCandidate.length in 3..80) {
                extractedSalt = saltCandidate
            }
        }

        // Fallback for salt composition: find dosage lines like "Paracetamol IP 650mg"
        if (extractedSalt.isBlank()) {
            val mgMlRegex = Regex("""\b([A-Za-z\s\-]+(?:\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|IU)\b.*))\b""", RegexOption.IGNORE_CASE)
            mgMlRegex.find(rawText)?.let { match ->
                val lineVal = match.value.trim()
                if (lineVal.length in 5..60 && !lineVal.uppercase(Locale.ROOT).startsWith("MRP")) {
                    extractedSalt = lineVal
                }
            }
        }

        // 6. Extract Pack Config (e.g., "1 Strip = 10 Tablets", "10 Tablets", "100ml Bottle")
        val packRegex = Regex("""\b(\d+\s*(?:Tablets?|Capsules?|Strips?|ml|g|gm|kg)\b(?:[\s/]*Strip)?)""", RegexOption.IGNORE_CASE)
        packRegex.find(rawText)?.let { match ->
            extractedPack = match.value.trim()
        }

        // 7. Extract Product / Medicine Name
        // Heuristics: The most prominent top text line that isn't metadata (EXP, BATCH, MRP, MFG)
        val metaKeywords = listOf("MRP", "BATCH", "EXP", "MFG", "PRICE", "RS.", "B.NO", "FOR", "USE", "STORE", "KEEP", "DATE", "INCL", "TAXES", "MARKETED", "MANUFACTURED")
        for (line in lines) {
            val upper = line.uppercase(Locale.ROOT)
            val isMetaLine = metaKeywords.any { upper.startsWith(it) || upper.contains("MRP") || upper.contains("BATCH") || upper.contains("EXP:") }
            
            if (!isMetaLine && line.length in 3..45) {
                // Ignore lines that are pure numbers or dates
                if (!line.matches(Regex("""^[0-9/\-.:\s]+$"""))) {
                    extractedName = cleanProductName(line)
                    break
                }
            }
        }

        if (extractedName.isBlank() && lines.isNotEmpty()) {
            extractedName = cleanProductName(lines.first())
        }

        val result = OcrParsedProduct(
            name = extractedName,
            batchNumber = extractedBatch,
            expiryDate = extractedExpiry,
            mrp = extractedMrp,
            manufacturer = extractedManufacturer,
            saltComposition = extractedSalt,
            packConfig = extractedPack,
            rawText = rawText
        )

        Log.d(TAG, "Parsed OCR result: $result")
        return result
    }

    private fun cleanProductName(raw: String): String {
        return raw.replace(Regex("""[®™]"""), "")
            .replace(Regex("""^\d+\.\s*"""), "")
            .trim()
    }

    private fun formatExpiryString(rawExp: String): String {
        val clean = rawExp.replace(" ", "").trim()
        // e.g. 08/2027 or 08/27
        if (clean.matches(Regex("""^\d{1,2}/\d{2,4}$"""))) {
            val parts = clean.split("/")
            val month = parts[0].padStart(2, '0')
            var year = parts[1]
            if (year.length == 2) year = "20$year"
            return "$month/$year"
        }
        if (clean.matches(Regex("""^\d{1,2}-\d{2,4}$"""))) {
            val parts = clean.split("-")
            val month = parts[0].padStart(2, '0')
            var year = parts[1]
            if (year.length == 2) year = "20$year"
            return "$month/$year"
        }
        return rawExp
    }
}
