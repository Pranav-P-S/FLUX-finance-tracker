package com.flux.app.engine

import androidx.room.withTransaction
import com.flux.app.data.AppDatabase
import com.flux.app.data.TransactionEntity
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
 * The capture pipeline: filter → parse → dedup → categorize → store → notify.
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

    suspend fun ingest(text: String, sourcePackage: String, postTimeMs: Long): IngestResult =
        mutex.withLock {
            val current = categorizer ?: buildCategorizer().also { categorizer = it }

            val body = text.trim()
            if (!isCandidate(body)) return@withLock IngestResult.Filtered

            val parsed = UniversalParser.parse(body, postTimeMs)
                ?: return@withLock IngestResult.Unparsed

            val amount = if (parsed.isCredit) parsed.amount else -parsed.amount
            val hash = Dedup.hash(parsed.timestamp, amount, parsed.merchant)

            val decision = current.categorize(parsed.merchant, body)
            val needsReview = !decision.auto || parsed.parseConfidence < Categorizer.AUTO_THRESHOLD

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
                createdAt = System.currentTimeMillis(),
            )

            val insertedId = db.transactions().insertAll(listOf(entity))[0]
            if (insertedId == -1L) return@withLock IngestResult.Duplicate

            _changes.tryEmit(Unit)
            IngestResult.Stored(entity.copy(id = insertedId))
        }

    /** Record a manual user decision as a training sample and retrain immediately. */
    suspend fun learn(merchant: String, rawText: String, categoryId: String) {
        mutex.withLock {
            db.training().add(TrainingSample(text = "$merchant $rawText".trim(), categoryId = categoryId))
            categorizer = buildCategorizer()
        }
    }

    suspend fun retrain() {
        mutex.withLock { categorizer = buildCategorizer() }
    }

    private suspend fun buildCategorizer(): Categorizer {
        val categories = db.categories().all()
        val samples = db.training().all().map { LabeledSample(it.text, it.categoryId) }
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
    }
}
