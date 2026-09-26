package com.flux.app.parse

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extracts structured transactions from raw bank and UPI notification text.
 *
 * Extraction is heuristic by design — amount, direction, payee, account hint
 * and date are pulled from phrasing that generalizes across institutions — and
 * every result carries a confidence score. Low-confidence parses are flagged so
 * the caller can route them to manual review instead of trusting the guess.
 *
 * Pure JVM Kotlin: no Android dependencies, fully covered by unit tests.
 */
data class ParsedTransaction(
    val amount: Double,
    val currency: String,
    val merchant: String,
    val isCredit: Boolean,
    val accountHint: String?,
    val timestamp: Long,
    val ruleId: String,
    /** 0..1 — how sure the parser itself is; < 0.8 forces Inbox triage. */
    val parseConfidence: Double,
)

object UniversalParser {

    private val NOISE = Regex("""(?i)\b(otp|one[\s-]?time\s+password|password|pin)\b""")

    // Rs 1,234.56 / INR 1234 / ₹1234 / $12.50 / EUR 9,90 / 1234.56 rupees
    private val AMOUNT_PREFIX = Regex(
        """(?i)(rs\.?|inr|₹|\$|usd|eur|€|gbp|£)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )
    private val AMOUNT_SUFFIX = Regex(
        """(?i)([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(rs\.?|inr|rupees?|₹|\$|usd|eur|gbp|€|£)"""
    )

    private val CREDIT_VERBS = Regex(
        """(?i)\b(credited|credit|received|received from|refund(?:ed)?|deposited|deposit|salary|salaries|cashback|cash back|reversal|reversed|added to|interest credit)\b"""
    )
    private val DEBIT_VERBS = Regex(
        """(?i)\b(debited|debit|spent|paid|pay|purchase|purchased|withdrawn|withdrawal|deducted|charged|sent|transfer(?:red)? to|order(?:ed)?|booked|subscription)\b"""
    )

    // "at STARBUCKS", "to Swiggy", "by AMAZON PAY INDIA", "towards Electricity Bill" ...
    private val MERCHANT_PREFIXED = Regex(
        """(?i)(?:\bat\b|\bto\b|\bby\b|\btowards\b|\bvia\b|\bin favour of\b|\bin favor of\b)\s+((?:[a-z0-9&.'\-]+[\s]*){1,4})"""
    )
    // UPI handles: paid to swiggy@ybl / UPI/SWIGGY@OKICICI
    private val UPI_HANDLE = Regex("""([a-zA-Z][a-zA-Z0-9._-]{1,30})@[a-zA-Z]{2,}""")
    // Longest ALL-CAPS run that is not a payment-network acronym.
    private val CAPS_RUN = Regex("""\b([A-Z][A-Z0-9&.'\-]{2,}(?:\s+[A-Z0-9&.'\-]{2,})*)\b""")

    private val ACCOUNT_HINT = Regex(
        """(?i)(?:a/?c|acct|account|card)\s*(?:no\.?|number|ending)?\s*[:\-]?\s*([xX*]*\d{4})"""
    )

    private val DATE_DMY = Regex("""\b(\d{1,2})[-/](\d{1,2})(?:[-/](\d{2,4}))?\b""")
    private val DATE_TEXT = Regex("""\b(\d{1,2})[\s-]([A-Za-z]{3,9})\.?(?:[\s-](\d{2,4}))?\b""")

    private val NETWORK_ACRONYMS = setOf(
        "UPI", "IMPS", "NEFT", "RTGS", "POS", "ATM", "OTP", "INR", "USD", "EUR", "GBP",
        "A/C", "MMT", "IMP", "P2A", "P2P", "YBL", "OKICICI", "OKAXIS", "OKHDFCBANK",
        "PAYTM", "THE", "AND", "FOR", "NOT", "REF", "VPA", "TXN", "INF", "YESB", "SBIN",
        "HDFC", "ICICI", "AXIS", "KKBK", "PUNB", "BARB", "CITI", "UTIB",
    )

    private val TRAILING_STOPWORDS = setOf(
        "on", "dt", "dated", "via", "using", "card", "ref", "reference", "from", "acct",
        "account", "and", "with", "upi", "imps", "neft", "at", "by", "to", "a/c", "no",
        "your", "the", "a", "an", "of", "is", "was", "has", "have", "been", "-", ".",
        "not", "you", "call", "towards", "in", "favour", "favor",
    )

    private val LEADING_JUNK = TRAILING_STOPWORDS + setOf(
        "savings", "current", "wallet", "credit", "debit", "xx", "ending",
    )

    fun parse(text: String, postTimeMs: Long): ParsedTransaction? {
        if (text.length < 8) return null
        if (NOISE.containsMatchIn(text)) return null

        val amountMatch = findAmount(text) ?: return null
        val (amount, currency) = amountMatch

        val isCredit = when {
            CREDIT_VERBS.containsMatchIn(text) && !DEBIT_VERBS.containsMatchIn(text) -> true
            DEBIT_VERBS.containsMatchIn(text) && !CREDIT_VERBS.containsMatchIn(text) -> false
            CREDIT_VERBS.containsMatchIn(text) && DEBIT_VERBS.containsMatchIn(text) -> {
                // e.g. "credited to your account after debiting card" — credit is the outcome.
                val creditIdx = CREDIT_VERBS.find(text)!!.range.first
                val debitIdx = DEBIT_VERBS.find(text)!!.range.first
                creditIdx < debitIdx
            }
            else -> false
        }
        val typeClear = CREDIT_VERBS.containsMatchIn(text) || DEBIT_VERBS.containsMatchIn(text)

        val merchant = extractMerchant(text)
        val accountHint = ACCOUNT_HINT.find(text)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        val timestamp = extractDate(text) ?: postTimeMs

        val isHeuristic = !typeClear
        val confidence = if (isHeuristic) 0.5 else 0.9

        return ParsedTransaction(
            amount = amount,
            currency = currency,
            merchant = merchant,
            isCredit = isCredit,
            accountHint = accountHint,
            timestamp = timestamp,
            ruleId = if (isHeuristic) "heuristic" else if (isCredit) "generic_credit" else "generic_debit",
            parseConfidence = confidence,
        )
    }

    /** Currency marker position varies by institution: prefix ("Rs 120") or suffix ("120 INR"). */
    private data class AmountRule(val regex: Regex, val currencyGroup: Int, val valueGroup: Int)

    private val amountRules = listOf(
        AmountRule(AMOUNT_PREFIX, currencyGroup = 1, valueGroup = 2),
        AmountRule(AMOUNT_SUFFIX, currencyGroup = 2, valueGroup = 1),
    )

    private fun findAmount(text: String): Pair<Double, String>? {
        for (rule in amountRules) {
            val match = rule.regex.find(text) ?: continue
            val value = match.groupValues[rule.valueGroup].replace(",", "").toDoubleOrNull() ?: continue
            if (value <= 0.0) continue
            return value to normalizeCurrency(match.groupValues[rule.currencyGroup])
        }
        return null
    }

    private fun normalizeCurrency(token: String): String = when (token.uppercase(Locale.ROOT).trimEnd('.')) {
        "$", "USD" -> "USD"
        "€", "EUR" -> "EUR"
        "£", "GBP" -> "GBP"
        else -> "INR"
    }

    fun extractMerchant(text: String): String {
        MERCHANT_PREFIXED.find(text)?.let { m ->
            val candidate = cleanMerchantCandidate(m.groupValues[1])
            if (candidate != null) return candidate
        }

        UPI_HANDLE.find(text)?.let { m ->
            val handle = m.groupValues[1].replace(Regex("""[._-]+$"""), "")
            if (handle.length >= 3) return handle
        }

        CAPS_RUN.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { run -> run.split(" ").none { it.replace(Regex("""[^A-Z0-9]"""), "") in NETWORK_ACRONYMS } }
            .maxByOrNull { it.length }
            ?.let { if (it.length >= 3) return it }

        return "Unknown"
    }

    /** Strips connector/date junk the prefixed regex inevitably captures. */
    private fun cleanMerchantCandidate(raw: String): String? {
        var parts = raw.replace(Regex("""\s+"""), " ").trim().split(" ").toMutableList()
        // Trailing dates/refs: "SWIGGY on 12-09-26." -> "SWIGGY"
        while (parts.size > 1 &&
            (parts.last().lowercase(Locale.ROOT) in TRAILING_STOPWORDS || parts.last().any { it.isDigit() })
        ) {
            parts.removeAt(parts.size - 1)
        }
        // Leading account phrasing: "your account towards SALARY" -> "SALARY"
        while (parts.size > 1 && parts.first().lowercase(Locale.ROOT) in LEADING_JUNK) {
            parts.removeAt(0)
        }
        val candidate = parts.joinToString(" ").trim().trimEnd('.', '-', ' ')
        return candidate.takeIf { it.length >= 2 && it.lowercase(Locale.ROOT) !in TRAILING_STOPWORDS }
    }

    private fun extractDate(text: String): Long? {
        DATE_TEXT.find(text)?.let { m ->
            runCatching {
                val day = m.groupValues[1].toInt()
                val month = LocalDate.parse(
                    "01 ${m.groupValues[2]} 2020",
                    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
                ).monthValue
                val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { normalizeYear(it) }
                    ?: LocalDate.now(ZoneId.systemDefault()).year
                if (day in 1..31) {
                    return LocalDate.of(year, month, day)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
        }
        DATE_DMY.find(text)?.let { m ->
            runCatching {
                val day = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { normalizeYear(it) }
                    ?: LocalDate.now(ZoneId.systemDefault()).year
                if (day in 1..31 && month in 1..12) {
                    return LocalDate.of(year, month, day)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
        }
        return null
    }

    private fun normalizeYear(y: Int): Int = when {
        y < 100 -> if (y > 69) 1900 + y else 2000 + y
        else -> y
    }
}
