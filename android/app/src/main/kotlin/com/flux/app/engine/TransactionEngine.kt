package com.flux.app.engine

import androidx.room.withTransaction
import com.flux.app.data.AppDatabase
import com.flux.app.data.TransactionEntity
import com.flux.app.data.TransactionKind
import com.flux.app.data.TrainingSample
import com.flux.app.ml.Categorizer
import com.flux.app.ml.LabeledSample
import com.flux.app.ml.NaiveBayesClassifier
import com.flux.app.parse.UniversalParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The capture pipeline: filter -> parse -> dedup -> categorize -> store -> notify.
 * All ingests are serialized through a mutex so concurrent notifications keep
 * their insertion order and the in-memory categorizer is built exactly once.
 *
 * [seedReady] must complete before the first categorizer build: without it an
 * early ingest racing the seed writer would train the model on an empty
 * database and cache the broken model for the lifetime of the process.
 */
class TransactionEngine(private val db: AppDatabase, private val seedReady: Job? = null) {

    sealed class IngestResult {
        data object Filtered : IngestResult()
        data object Unparsed : IngestResult()
        data object Duplicate : IngestResult()
        data class Stored(val transaction: TransactionEntity) : IngestResult()
    }

    private val mutex = Mutex()
    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val changes: SharedFlow<Unit> = _changes

    private var categorizer: Categorizer? = null
    private var trainingCount = 0

    suspend fun ingest(text: String, sourcePackage: String, postTimeMs: Long): IngestResult =
        mutex.withLock {
            val current = categorizer ?: buildCategorizer().also { categorizer = it }

            val body = maskPan(text.trim())
            if (!isCandidate(body)) return@withLock IngestResult.Filtered

            val parsed = UniversalParser.parse(body, postTimeMs)
                ?: return@withLock IngestResult.Unparsed

            val amount = if (parsed.isCredit) parsed.amount else -parsed.amount
            val hash = Dedup.hash(body, sourcePackage, parsed.timestamp)

            val kind = when {
                parsed.isHold -> TransactionKind.PENDING
                parsed.isRefund -> TransactionKind.REFUND
                else -> TransactionKind.PURCHASE
            }

            val decision = current.categorize(parsed.merchant, body)
            val currencyMismatch = parsed.currency != baseCurrency()
            // Holds are informational until they settle; everything uncertain
            // (weak parse, weak model guess, foreign currency) waits in the Inbox.
            val needsReview = kind != TransactionKind.PENDING &&
                (!decision.auto || parsed.parseConfidence < Categorizer.AUTO_THRESHOLD || currencyMismatch)

            val entity = TransactionEntity(
                hash = hash,
                amount = amount,
                currency = parsed.currency,
                merchant = parsed.merchant,
                accountHint = parsed.accountHint,
                timestamp = parsed.timestamp,
                sourcePackage = sourcePackage,
                rawText = body,
                category = decision.categoryId,
                categoryConfidence = decision.confidence,
                needsReview = needsReview,
                parseMethod = parsed.ruleId,
                kind = kind,
                createdAt = System.currentTimeMillis(),
            )

            // Dedup check, insert and hold settlement share one transaction so a
            // crash can never leave a purchase inserted with its hold unsettled
            // (or a hold deleted for a purchase that was never stored).
            val insertedId = db.withTransaction {
                val id = db.transactions().insertAll(listOf(entity))[0]
                if (id != -1L && kind == TransactionKind.PURCHASE && amount < 0) {
                    settleMatchingHold(entity)
                }
                id
            }
            if (insertedId == -1L) return@withLock IngestResult.Duplicate

            _changes.tryEmit(Unit)
            IngestResult.Stored(entity.copy(id = insertedId))
        }

    /**
     * Records a manual decision and folds it into the model incrementally —
     * no database rebuild on the hot path. The stored text is sanitized:
     * payee plus purpose words only, never the raw alert payload.
     */
    suspend fun learn(merchant: String, rawText: String, categoryId: String) {
        mutex.withLock {
            val text = sanitizeTrainingText(merchant, rawText)
            db.training().add(TrainingSample(text = text, categoryId = categoryId))
            trainingCount++
            val current = categorizer
            categorizer = when {
                current != null -> current.withTraining(text, categoryId)
                trainingCount >= MIN_TRAINING_SAMPLES -> buildCategorizer()
                else -> null
            }
        }
    }

    suspend fun retrain() {
        mutex.withLock { categorizer = buildCategorizer() }
    }

    /**
     * Housekeeping that runs whenever the process wakes up — app open or
     * listener rebinding. No scheduler is involved.
     *
     * Pending holds expire 72 hours after capture if their settlement never
     * arrived. Raw alert text is scrubbed from settled rows after the same
     * window: it is needed for triage while a row waits in the Inbox, and is
     * kept no longer than that.
     */
    suspend fun sweep() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            db.transactions().deletePendingBefore(now - PENDING_TTL_MS)
            db.transactions().scrubRawTextBefore(now - RAW_TEXT_TTL_MS)
        }
    }

    private suspend fun baseCurrency(): String =
        db.settings().get("base_currency") ?: DEFAULT_BASE_CURRENCY

    /**
     * A hold followed by a debit from the same payee is its settlement: a fuel
     * pump authorizes for its maximum and the final charge posts lower. Drop
     * the hold so only the real charge reaches the totals. Runs inside the
     * caller's database transaction.
     */
    private suspend fun settleMatchingHold(purchase: TransactionEntity) {
        val payee = normalizePayee(purchase.merchant)
        val hold = db.transactions().pendingHolds().firstOrNull { candidate ->
            val heldPayee = normalizePayee(candidate.merchant)
            val samePayee = if (payee.length < 3 || heldPayee.length < 3) {
                payee == heldPayee
            } else {
                heldPayee.contains(payee) || payee.contains(heldPayee)
            }
            samePayee && -candidate.amount >= -purchase.amount
        }
        if (hold != null) db.transactions().delete(hold.id)
    }

    private fun normalizePayee(value: String): String =
        value.lowercase(java.util.Locale.ROOT).replace(NON_ALNUM, "")
            .ifEmpty { value.lowercase(java.util.Locale.ROOT) }

    private suspend fun buildCategorizer(): Categorizer {
        seedReady?.join()
        val categories = db.categories().all()
        val samples = db.training().all().map { LabeledSample(it.text, it.categoryId) }
        trainingCount = samples.size
        val model = if (samples.size >= MIN_TRAINING_SAMPLES) NaiveBayesClassifier.train(samples) else null
        return Categorizer(categories, model)
    }

    private fun isCandidate(text: String): Boolean {
        if (text.length < 8) return false
        val lower = text.lowercase(java.util.Locale.ROOT)
        if (OTP_PATTERN.containsMatchIn(lower)) return false
        // Cheap money-indicator gate before the full parse: without a currency
        // marker or a transfer verb there is nothing to extract.
        return MONEY_HINT.containsMatchIn(lower)
    }

    /** Full card numbers are masked before anything is hashed or persisted. */
    private fun maskPan(text: String): String = PAN_PATTERN.replace(text, "[card]")

    companion object {
        private const val MIN_TRAINING_SAMPLES = 10
        private const val DEFAULT_BASE_CURRENCY = "INR"
        private const val PENDING_TTL_MS = 72L * 60 * 60 * 1000
        private const val RAW_TEXT_TTL_MS = 72L * 60 * 60 * 1000

        private val OTP_PATTERN = Regex("""\b(otp|one[\s-]?time\s+password)\b""")
        private val MONEY_HINT = Regex(
            """(rs|inr|usd|eur|gbp|rupee|₹|\$|€|£|credit|debit|spent|paid|refund|received)"""
        )
        private val NON_ALNUM = Regex("[^a-z0-9]")
        private val PAN_PATTERN = Regex("""\d{4}[\s-]\d{4}[\s-]\d{4}[\s-]\d{4}""")

        /**
         * Training text keeps the classifier signal — payee and purpose words —
         * and drops everything that could identify a person: digit runs
         * (references, amounts, account digits) and oversized fragments.
         */
        fun sanitizeTrainingText(merchant: String, rawText: String): String {
            val words = rawText.split(Regex("[^A-Za-z]+"))
                .filter { it.length in 3..20 }
                .distinct()
                .take(40)
            return (listOf(merchant) + words).joinToString(" ").trim()
        }
    }
}
