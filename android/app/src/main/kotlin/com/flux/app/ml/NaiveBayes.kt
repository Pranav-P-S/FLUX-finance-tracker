package com.flux.app.ml

/**
 * Multinomial Naive Bayes classifier with Laplace smoothing, trained entirely
 * on device. Tokens are lowercased with digit runs collapsed, which keeps the
 * vocabulary small enough to retrain after every user correction.
 * Pure JVM Kotlin, fully covered by unit tests.
 */
data class LabeledSample(val text: String, val categoryId: String)

data class Classification(val categoryId: String, val confidence: Double)

class NaiveBayesClassifier private constructor(
    private val tokenCounts: Map<String, Map<String, Int>>,
    private val tokenTotals: Map<String, Int>,
    private val docCounts: Map<String, Int>,
    private val vocabulary: Set<String>,
) {
    private val totalDocs: Int = docCounts.values.sum()
    /**
     * Returns the most probable category with its posterior probability,
     * or null when the model has no usable training data.
     */
    fun classify(text: String): Classification? {
        if (docCounts.isEmpty() || totalDocs == 0) return null
        val tokens = tokenize(text).filter { it in vocabulary }
        if (tokens.isEmpty()) return null
        if (docCounts.size == 1) {
            val only = docCounts.entries.first()
            return Classification(only.key, 1.0)
        }

        val vocabularySize = vocabulary.size
        val logPosteriors = docCounts.mapValues { (category, docCount) ->
            var score = ln(docCount.toDouble() / totalDocs)
            val counts = tokenCounts[category].orEmpty()
            val denominator = (tokenTotals[category] ?: 0) + vocabularySize
            for (token in tokens) {
                score += ln(((counts[token] ?: 0) + 1.0) / denominator.coerceAtLeast(1))
            }
            score
        }

        val maxLog = logPosteriors.values.max()
        val expScores = logPosteriors.mapValues { kotlin.math.exp(it.value - maxLog) }
        val sum = expScores.values.sum()
        if (sum <= 0.0) return null
        val best = expScores.maxByOrNull { it.value } ?: return null
        return Classification(best.key, best.value / sum)
    }

    /**
     * Returns a classifier that additionally knows one labeled example. Used for
     * incremental learning on user corrections so a correction never triggers a
     * full database rebuild. Text is sanitized by the caller.
     */
    fun updated(sample: LabeledSample): NaiveBayesClassifier {
        val tokens = tokenize(sample.text).toSet()
        if (tokens.isEmpty()) return this

        val counts = HashMap(tokenCounts)
        val perCategory = HashMap(counts[sample.categoryId] ?: emptyMap())
        for (token in tokens) perCategory.merge(token, 1, Int::plus)
        counts[sample.categoryId] = perCategory

        val totals = HashMap(tokenTotals)
        totals[sample.categoryId] = (totals[sample.categoryId] ?: 0) + tokens.size

        val docs = HashMap(docCounts)
        docs[sample.categoryId] = (docs[sample.categoryId] ?: 0) + 1

        return NaiveBayesClassifier(counts, totals, docs, vocabulary + tokens)
    }

    private fun ln(x: Double) = kotlin.math.ln(x)

    companion object {
        private val TOKEN_SPLIT = Regex("[^a-z0-9]+")

        /** Lowercase, split on non-alphanumerics, digits collapsed to a single "#" token. */
        fun tokenize(text: String): List<String> =
            text.lowercase()
                .split(TOKEN_SPLIT)
                .filter { it.length >= 2 }
                .map { if (it.all { c -> c.isDigit() }) "#" else it }

        fun train(samples: List<LabeledSample>): NaiveBayesClassifier {
            val tokenCounts = mutableMapOf<String, MutableMap<String, Int>>()
            val tokenTotals = mutableMapOf<String, Int>()
            val docCounts = mutableMapOf<String, Int>()
            val vocabulary = mutableSetOf<String>()

            for (sample in samples) {
                val tokens = tokenize(sample.text).toSet()
                if (tokens.isEmpty()) continue
                docCounts.merge(sample.categoryId, 1, Int::plus)
                for (token in tokens) {
                    vocabulary.add(token)
                    val perCategory = tokenCounts.getOrPut(sample.categoryId) { mutableMapOf() }
                    perCategory.merge(token, 1, Int::plus)
                    tokenTotals.merge(sample.categoryId, 1, Int::plus)
                }
            }
            return NaiveBayesClassifier(tokenCounts, tokenTotals, docCounts, vocabulary)
        }
    }
}
