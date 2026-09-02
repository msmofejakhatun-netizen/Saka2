package com.example.util

import com.example.data.db.CustomerEntity
import java.util.Locale

data class VoiceUdharParsedResult(
    val rawSpeech: String,
    val customerName: String? = null,
    val matchedCustomer: CustomerEntity? = null,
    val amount: Double? = null,
    val itemsOrNote: String? = null,
    val isJama: Boolean = false // false = Udhar (Credit), true = Jama (Payment Received)
)

object VoiceHelper {

    /**
     * Parses spoken Hindi / Hinglish / English natural language phrases for Udhar Khata entries.
     * Examples:
     * - "Ramesh ko 2 kilo chini 100 rupaye ka udhar diya" -> Name: Ramesh, Amount: 100, Note: 2 kilo chini, Udhar
     * - "Amit 500 rupees credit" -> Name: Amit, Amount: 500, Udhar
     * - "Suresh se 250 rupaye jama mile" -> Name: Suresh, Amount: 250, Jama
     * - "Priya 1200 udhar" -> Name: Priya, Amount: 1200, Udhar
     * - "Rahul 450 mobile recharge" -> Name: Rahul, Amount: 450, Note: mobile recharge
     */
    fun parseVoiceUdharEntry(
        rawSpeech: String,
        existingCustomers: List<CustomerEntity>
    ): VoiceUdharParsedResult {
        val cleanSpeech = rawSpeech.trim()
        if (cleanSpeech.isBlank()) {
            return VoiceUdharParsedResult(rawSpeech = rawSpeech)
        }

        val lowerSpeech = cleanSpeech.lowercase(Locale.getDefault())

        // 1. Detect Transaction Type (Udhar vs Jama)
        val jamaKeywords = listOf("jama", "received", "paid", "mila", "mile", "mili", "payment", "clear", "settled", "cash back")
        val isJama = jamaKeywords.any { lowerSpeech.contains(it) }

        // 2. Amount Extraction
        var amount: Double? = null

        // Try extracting numeric digits first
        val numericRegex = Regex("""\b(\d+(\.\d+)?)\b""")
        val numericMatches = numericRegex.findAll(lowerSpeech).mapNotNull { it.value.toDoubleOrNull() }.toList()

        if (numericMatches.isNotEmpty()) {
            // Usually the amount is the number (avoiding single small integers if near "kilo" / "kg" / "litre")
            amount = numericMatches.lastOrNull() ?: numericMatches.first()
        } else {
            // Fallback: Spoken Hindi / English number words
            amount = parseSpokenNumberWords(lowerSpeech)
        }

        // 3. Customer Name Extraction & Database Matching
        var matchedCustomer: CustomerEntity? = null
        var customerName: String? = null

        // Search in existing customers list (exact or partial name match)
        for (customer in existingCustomers) {
            val cNameLower = customer.name.lowercase(Locale.getDefault())
            val firstName = cNameLower.split(" ").firstOrNull() ?: cNameLower

            if (lowerSpeech.contains(cNameLower) || (firstName.length >= 3 && lowerSpeech.contains(firstName))) {
                matchedCustomer = customer
                customerName = customer.name
                break
            }
        }

        // Fallback: Hindi grammar pattern extraction (Words before "ko", "se", "ne", "ka")
        if (customerName == null) {
            val connectorRegex = Regex("""^([a-zA-Z\u0900-\u097F]+)\s+(ko|se|ne|ka|ke|ki)\b""", RegexOption.IGNORE_CASE)
            val match = connectorRegex.find(cleanSpeech)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length >= 2 && !candidate.equals("aaj", ignoreCase = true) && !candidate.equals("kall", ignoreCase = true)) {
                    customerName = candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
        }

        // Second Fallback: First capitalized word or first non-stopword token
        if (customerName == null) {
            val tokens = cleanSpeech.split("""\s+""".toRegex())
            val stopWords = setOf("ko", "ka", "ki", "ke", "se", "ne", "udhar", "diya", "jama", "mile", "mila", "rupaye", "rupee", "rupees", "rs", "inr", "kilo", "kg")
            for (t in tokens) {
                val cleanT = t.lowercase(Locale.getDefault()).replace(Regex("""[^a-zA-Z\u0900-\u097F]"""), "")
                if (cleanT.length >= 2 && !stopWords.contains(cleanT) && cleanT.toDoubleOrNull() == null) {
                    customerName = cleanT.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    break
                }
            }
        }

        // 4. Items / Note / Description Extraction
        var itemsOrNote: String? = null
        val removeWords = mutableListOf(
            "udhar", "diya", "deya", "de do", "de diya", "jama", "mile", "mila", "mili",
            "rupaye", "rupee", "rupees", "rs", "inr", "ko", "se", "ka", "ki", "ke", "ne"
        )
        customerName?.let { removeWords.add(it.lowercase(Locale.getDefault())) }
        matchedCustomer?.name?.let { removeWords.add(it.lowercase(Locale.getDefault())) }
        amount?.let {
            removeWords.add(it.toInt().toString())
            removeWords.add(it.toString())
        }

        var remainingText = lowerSpeech
        for (w in removeWords) {
            remainingText = remainingText.replace(Regex("""\b${Regex.escape(w)}\b"""), " ")
        }
        remainingText = remainingText.replace(Regex("""\s+"""), " ").trim()

        if (remainingText.isNotBlank() && remainingText.length > 2) {
            itemsOrNote = remainingText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            itemsOrNote = if (isJama) "Voice Jama Entry" else "Voice Udhar Entry"
        }

        return VoiceUdharParsedResult(
            rawSpeech = rawSpeech,
            customerName = customerName ?: matchedCustomer?.name,
            matchedCustomer = matchedCustomer,
            amount = amount,
            itemsOrNote = itemsOrNote,
            isJama = isJama
        )
    }

    /**
     * Parses spoken Hindi / Hinglish number words like "sau", "hazaar", "paanch sau".
     */
    private fun parseSpokenNumberWords(lowerText: String): Double? {
        var total = 0.0

        if (lowerText.contains("hazaar") || lowerText.contains("hazar") || lowerText.contains("thousand")) {
            total += 1000.0
        }
        if (lowerText.contains("paanch sau") || lowerText.contains("panch sau") || lowerText.contains("500")) {
            total += 500.0
        } else if (lowerText.contains("sau") || lowerText.contains("hundred")) {
            if (lowerText.contains("do sau")) total += 200.0
            else if (lowerText.contains("teen sau")) total += 300.0
            else if (lowerText.contains("char sau")) total += 400.0
            else total += 100.0
        }
        if (lowerText.contains("pachas") || lowerText.contains("fifty")) {
            total += 50.0
        }

        return if (total > 0.0) total else null
    }
}
