package com.flux.app

import com.flux.app.data.CategoryEntity
import com.flux.app.ml.Categorizer
import com.flux.app.ml.LabeledSample
import com.flux.app.ml.NaiveBayesClassifier
import com.flux.app.ml.SeedCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {

    private fun categories() = SeedCorpus.categories.map { (id, label, color) ->
        CategoryEntity(
            id = id,
            label = label,
            color = color,
            icon = "category",
            keywords = SeedCorpus.keywords[id].orEmpty(),
            isDefault = true,
        )
    }

    private fun model() = NaiveBayesClassifier.train(
        SeedCorpus.samples.map { (text, category) -> LabeledSample(text, category) },
    )

    @Test
    fun `level 1 dictionary categorizes automatically`() {
        val c = Categorizer(categories(), model())
        val decision = c.categorize("SWIGGY LIMITED", "Rs 450 debited to SWIGGY")
        assertEquals("food_drink", decision.categoryId)
        assertTrue(decision.auto)
        assertTrue(decision.confidence >= 0.8)
        assertEquals("dictionary", decision.source)
    }

    @Test
    fun `longest keyword wins`() {
        val c = Categorizer(categories(), model())
        val decision = c.categorize("INDIAN OIL PETROL PUMP", "fuel Rs 2000")
        assertEquals("transport", decision.categoryId)
    }

    @Test
    fun `unknown merchant gets model category suggestion`() {
        val c = Categorizer(categories(), model())
        // Trained on many food samples; a food-adjacent unseen merchant should classify
        // into food_drink. Whether it auto-applies depends on the posterior crossing
        // the 80% plan threshold — both outcomes are valid, the category must match.
        val decision = c.categorize("PIZZA CORNER", "Rs 500 debited for pizza order")
        assertEquals("food_drink", decision.categoryId)
        assertEquals("naive_bayes", decision.source)
    }

    @Test
    fun `low confidence routes to inbox`() {
        val c = Categorizer(categories(), null) // no model at all
        val decision = c.categorize("RANDOM TRADERS", "Rs 500 debited")
        assertFalse(decision.auto)
        assertEquals("uncategorized", decision.categoryId)
    }

    @Test
    fun `income keyword beats naive bayes`() {
        val c = Categorizer(categories(), model())
        val decision = c.categorize("MONTHLY PAYOUT", "salary credited Rs 50000")
        assertEquals("income", decision.categoryId)
        assertTrue(decision.auto)
    }
}
