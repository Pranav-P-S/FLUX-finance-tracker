package com.flux.app.engine

import com.flux.app.data.AppDatabase
import com.flux.app.data.TransactionEntity
import com.flux.app.data.TransactionKind
import com.flux.app.data.TrainingSample
import com.flux.app.ml.Categorizer
import com.flux.app.ml.LabeledSample
import com.flux.app.ml.NaiveBayesClassifier
import com.flux.app.parse.UniversalParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The capture pipeline: filter -> parse -> dedup -> categorize -> store -> notify.
 * All ingests are serialized through a mutex so concurrent notifications keep
 * their insertion order and the in-memory categorizer is built exactly once.
 */
class TransactionEngine(private val db: AppDatabase) {

    sealed class IngestResult {
        data object Filtered : IngestResult()
        data object Unparsed : IngestResult()
        data object Duplicate : IngestResult()
        data class Stored(val transaction: TransactionEntity) : IngestResult()
    }

    private val mutex = Mutex()
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes: SharedFlow<Unit> = _changes

    private var categorizer: Categorizer? = null
    private var trainingCount = 0

    suspend fun ingest(text: String, sourcePackage: String, postTimeMs: Long): IngestResult =
        mutex.withLock {
            val current = categorizer ?: buildCategorizer().also { categorizer = it }

            val body = text.trim()
            if (!isCandidate(body)) return@withLock IngestResult.Filtered

            val parsed = UniversalParser.parse(body, postTimeMs)
                ?: return@withLock IngestResult.Unparsed

            val amount = if (parsed.isCredit) parsed.amount else -parsed.amount
            val hash = Dedup.hash(body, sourcePackage)

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

            val insertedId = db.transactions().insertAll(listOf(entity))[0]
            if (insertedId == -1L) return@withLock IngestResult.Duplicate

            if (kind == TransactionKind.PURCHASE && amount < 0) {
                settleMatchingHold(entity)
            }

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
            val cutoff = System.currentTimeMillis() - RAW_TEXT_TTL_MS
            db.transactions().deletePendingBefore(cutoff)
            db.transactions().scrubRawTextBefore(cutoff)
        }
    }

    private suspend fun baseCurrency(): String =
        db.settings().get("base_currency") ?: DEFAULT_BASE_CURRENCY

    /**
     * A hold followed by a debit from the same payee is its settlement: a fuel
     * pump authorizes for its maximum and the final charge posts lower. Drop
     * the hold so only the real charge reaches the totals.
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
        value.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { value.lowercase() }

    private suspend fun buildCategorizer(): Categorizer {
        val categories = db.categories().all()
        val samples = db.training().all().map { LabeledSample(it.text, it.categoryId) }
        trainingCount = samples.size
        val model = if (samples.size >= MIN_TRAINING_SAMPLES) NaiveBayesClassifier.train(samples) else null
        return Categorizer(categories, model)
    }

    private fun isCandidate(text: String): Boolean {
        if (text.length < 8) return false
        val lower = text.lowercase()
        if (Regex("""\b(otp|one[\s-]?time\s+password)\b""").containsMatchIn(lower)) return false
        return true
    }

    companion object {
        private const val MIN_TRAINING_SAMPLES = 10
        private const val DEFAULT_BASE_CURRENCY = "INR"
        private const val PENDING_TTL_MS = 72L * 60 * 60 * 1000
        private const val RAW_TEXT_TTL_MS = 72L * 60 * 60 * 1000

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
