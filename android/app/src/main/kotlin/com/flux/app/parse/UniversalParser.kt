package com.flux.app.parse

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
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
    /** Money returned for a prior purchase — must offset spend, not inflate income. */
    val isRefund: Boolean = false,
    /** Authorization hold or pending entry — excluded from totals until it settles. */
    val isHold: Boolean = false,
)

object UniversalParser {

    /** Fabricated or absurd amounts (crafted text, digit runs) never parse. */
    private const val MAX_AMOUNT = 1e9

    private val NOISE = Regex("""(?i)\b(otp|one[\s-]?time\s+password|password|pin)\b""")

    // Rs 1,234.56 / INR 1234 / ₹1234 / $12.50 / EUR 9,90 / 1234.56 rupees.
    // The lookbehind keeps "yours 100" from matching as "rs 100".
    private val AMOUNT_PREFIX = Regex(
        """(?i)(?<![a-z])(rs\.?|inr|₹|\$|usd|eur|€|gbp|£)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )
    private val AMOUNT_SUFFIX = Regex(
        """(?i)([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(rs\.?|inr|rupees?|₹|\$|usd|eur|gbp|€|£)"""
    )

    // Direction detection runs on text where the "credit card" collocation is
    // masked out: "Your credit card was debited" must be a debit, not income
    // because a bare `credit` verb matched inside "credit card".
    private val CREDIT_CARD_COLLOCATION = Regex("""(?i)\bcredit\s+(card|acct|account|limit)\b""")
    private val CREDIT_VERBS = Regex(
        """(?i)\b(credited|credit|received|refund(?:ed)?|deposited|deposit|salary|salaries|cashback|cash back|reversal|reversed|added to|interest credit)\b"""
    )
    private val DEBIT_VERBS = Regex(
        """(?i)\b(debited|debit|spent|paid|pay|purchase|purchased|withdrawn|withdrawal|deducted|charged|sent|transfer(?:red)? to|order(?:ed)?|booked|subscription)\b"""
    )

    /** Money coming back for a previous purchase — not new income. */
    private val REFUND_MARKERS = Regex("""(?i)\b(refund(?:ed)?|reversal|reversed)\b""")

    /**
     * Authorization holds and pending entries (fuel pumps, hotels). The actual
     * charge usually posts later at a different amount, so these must never
     * count toward totals.
     */
    private val HOLD_MARKERS = Regex("""(?i)\b(pre-?auth(?:orization)?|hold(?:ing)?|held|pending)\b""")

    // "at STARBUCKS", "to Swiggy", "by AMAZON PAY INDIA", "towards Electricity Bill" ...
    private val MERCHANT_PREFIXED = Regex(
        """(?i)(?:\bat\b|\bto\b|\bby\b|\btowards\b|\bvia\b|\bin favour of\b|\bin favor of\b)\s+((?:[a-z0-9&.'\-]+[\s]*){1,4})"""
    )
    // UPI handles: paid to swiggy@ybl / UPI/SWIGGY@OKICICI
    private val UPI_HANDLE = Regex("""([a-zA-Z][a-zA-Z0-9._-]{1,30})@[a-zA-Z]{2,}""")
    // Longest ALL-CAPS run that is not a payment-network acronym. Separators are
    // spaces/tabs only — a run must never swallow across lines.
    private val CAPS_RUN = Regex("""([A-Z][A-Z0-9&.'\-]{2,}(?:[ \t]+[A-Z0-9&.'\-]{2,})*)""")

    private val ACCOUNT_HINT = Regex(
        """(?i)(?:a/?c|acct|account|card)\s*(?:no\.?|number|ending)?\s*[:\-]?\s*([xX*]*\d{4})(?!\d)"""
    )
    // A full 16-digit PAN must never become a 4-digit "hint" (or reach the DB).
    private val FULL_PAN = Regex("""\d{4}[\s-]\d{4}[\s-]\d{4}[\s-]\d{4}""")

    // Dates must be anchored to a date keyword (on/dt/dated) or carry a year:
    // unanchored "24/7" and "EMI 2/12" are idioms, not calendar dates.
    private val DATE_DMY = Regex(
        """(?i)\b(?:on|dt|dated)\s*[:]?\s*(\d{1,2})[-/](\d{1,2})(?:[-/](\d{2,4}))?\b"""
    )
    private val DATE_TEXT = Regex(
        """(?i)\b(?:on|dt|dated)\s*[:]?\s*(\d{1,2})[\s-]([A-Za-z]{3,9})\.?(?:[\s-](\d{2,4}))?\b"""
    )
    // Standalone full dates with years are unambiguous even without a keyword.
    private val DATE_DMY_YEARED = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""")

    private val MONTH_FORMATTER = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd MMM yyyy")
        .toFormatter(Locale.ENGLISH)

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

        val isCredit = detectDirection(text)
        val typeClear = isDirectionExplicit(text)

        val merchant = extractMerchant(text)
        val accountHint = extractAccountHint(text)
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
            isRefund = isCredit && REFUND_MARKERS.containsMatchIn(text),
            isHold = HOLD_MARKERS.containsMatchIn(text),
        )
    }

    private fun directionText(text: String): String =
        CREDIT_CARD_COLLOCATION.replace(text, "ccard")

    private fun detectDirection(text: String): Boolean {
        val body = directionText(text)
        val credit = CREDIT_VERBS.containsMatchIn(body)
        val debit = DEBIT_VERBS.containsMatchIn(body)
        return when {
            credit && !debit -> true
            debit && !credit -> false
            credit && debit -> {
                // e.g. "credited to your account after debiting card" — first
                // verb is the outcome.
                CREDIT_VERBS.find(body)!!.range.first < DEBIT_VERBS.find(body)!!.range.first
            }
            else -> false
        }
    }

    private fun isDirectionExplicit(text: String): Boolean {
        val body = directionText(text)
        return CREDIT_VERBS.containsMatchIn(body) || DEBIT_VERBS.containsMatchIn(body)
    }

    /** Currency marker position varies by institution: prefix ("Rs 120") or suffix ("120 INR"). */
    private data class AmountRule(val regex: Regex, val currencyGroup: Int, val valueGroup: Int)

    private val amountRules = listOf(
        AmountRule(AMOUNT_PREFIX, currencyGroup = 1, valueGroup = 2),
        AmountRule(AMOUNT_SUFFIX, currencyGroup = 2, valueGroup = 1),
    )

    private fun findAmount(text: String): Pair<Double, String>? {
        for (rule in amountRules) {
            for (match in rule.regex.findAll(text)) {
                val currencyToken = match.groupValues[rule.currencyGroup]
                val rawValue = match.groupValues[rule.valueGroup]
                val value = parseMoneyValue(rawValue, currencyToken) ?: continue
                if (value <= 0.0 || value > MAX_AMOUNT) continue
                return value to normalizeCurrency(currencyToken)
            }
        }
        return null
    }

    /**
     * "1,234,567" is thousands-grouped; a single trailing ",90" is a decimal
     * comma in European formats ("EUR 9,90"). Everything else is rejected so
     * malformed values never reach the ledger.
     */
    private fun parseMoneyValue(raw: String, currencyToken: String): Double? {
        val isEuropean = currencyToken.uppercase(Locale.ROOT).trimEnd('.') in setOf("EUR", "€")
        val decimalComma = isEuropean && Regex("""^\d+,\d{2}$""").matches(raw)
        val cleaned = if (decimalComma) raw.replace(",", ".") else raw.replace(",", "")
        return cleaned.toDoubleOrNull()
    }

    private fun normalizeCurrency(token: String): String = when (token.uppercase(Locale.ROOT).trimEnd('.')) {
        "$", "USD" -> "USD"
        "€", "EUR" -> "EUR"
        "£", "GBP" -> "GBP"
        else -> "INR"
    }

    private fun extractAccountHint(text: String): String? {
        if (FULL_PAN.containsMatchIn(text)) return null
        return ACCOUNT_HINT.find(text)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
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
            .filter { run ->
                run.length <= 40 &&
                    run.split(" ").none { it.replace(Regex("""[^A-Z0-9]"""), "") in NETWORK_ACRONYMS }
            }
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
        if (candidate.length < 2) return null
        if (candidate.lowercase(Locale.ROOT) in TRAILING_STOPWORDS) return null
        // "at 10:30" must not mint a merchant "10" — payees contain letters.
        if (candidate.none { it.isLetter() }) return null
        return candidate
    }

    private fun extractDate(text: String): Long? {
        DATE_TEXT.find(text)?.let { m ->
            runCatching {
                val day = m.groupValues[1].toInt()
                val month = LocalDate.parse(
                    "01 ${m.groupValues[2]} 2020",
                    MONTH_FORMATTER,
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
            parseDmy(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { return it }
        }
        DATE_DMY_YEARED.find(text)?.let { m ->
            parseDmy(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { return it }
        }
        return null
    }

    private fun parseDmy(dayToken: String, monthToken: String, yearToken: String): Long? {
        return runCatching {
            val day = dayToken.toInt()
            val month = monthToken.toInt()
            val year = yearToken.takeIf { it.isNotEmpty() }?.toInt()?.let { normalizeYear(it) }
                ?: LocalDate.now(ZoneId.systemDefault()).year
            if (day in 1..31 && month in 1..12) {
                LocalDate.of(year, month, day)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                null
            }
        }.getOrNull()
    }

    private fun normalizeYear(y: Int): Int = when {
        y < 100 -> if (y > 69) 1900 + y else 2000 + y
        else -> y
    }
}
