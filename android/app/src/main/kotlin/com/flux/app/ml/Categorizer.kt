package com.flux.app.ml

import com.flux.app.data.CategoryEntity

/**
 * Two-level categorization: a fuzzy merchant dictionary answers first with high
 * confidence; anything it cannot match goes to the Naive Bayes model. Decisions
 * below [Companion.AUTO_THRESHOLD] never apply automatically — they are marked
 * for manual review in the Inbox instead.
 */
class Categorizer(
    private val categories: List<CategoryEntity>,
    private val model: NaiveBayesClassifier?,
) {
    data class Decision(
        val categoryId: String,
        val confidence: Double,
        val auto: Boolean,
        val source: String,
    )

    private data class DictEntry(val categoryId: String, val keyword: String)

    private val dictionary: List<DictEntry> = categories
        .filter { it.id != "uncategorized" }
        .flatMap { c -> c.keywords.filter { it.length >= 3 }.map { DictEntry(c.id, it.lowercase()) } }
        .sortedByDescending { it.keyword.length }

    /** Returns a categorizer whose model has additionally learned one example. */
    fun withTraining(text: String, categoryId: String): Categorizer =
        Categorizer(categories, model?.updated(LabeledSample(text, categoryId)))

    fun categorize(merchant: String, rawText: String): Decision {        val merchantLower = merchant.lowercase().trim()

        // Level 1 — longest keyword wins (e.g. "indian oil" beats "oil").
        dictionary.firstOrNull { merchantLower.contains(it.keyword) }?.let {
            return Decision(it.categoryId, 0.95, auto = true, source = "dictionary")
        }

        // Level 2 — Naive Bayes over the merchant + notification text.
        model?.classify("$merchant $rawText")?.let { c ->
            return Decision(c.categoryId, c.confidence, auto = c.confidence >= AUTO_THRESHOLD, source = "naive_bayes")
        }

        return Decision("uncategorized", 0.0, auto = false, source = "none")
    }

    companion object {
        const val AUTO_THRESHOLD = 0.80
    }
}
