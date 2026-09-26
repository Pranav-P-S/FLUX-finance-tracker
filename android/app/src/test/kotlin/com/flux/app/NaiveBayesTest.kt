package com.flux.app

import com.flux.app.ml.LabeledSample
import com.flux.app.ml.NaiveBayesClassifier
import com.flux.app.ml.SeedCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiveBayesTest {

    private fun trainFromSeed() = NaiveBayesClassifier.train(
        SeedCorpus.samples.map { (text, category) -> LabeledSample(text, category) },
    )

    @Test
    fun `classifies food alerts after seed training`() {
        val result = trainFromSeed().classify("Rs 250 debited for pizza order at PIZZA CORNER")
        assertEquals("food_drink", result!!.categoryId)
        assertTrue(result.confidence > 0.5)
    }

    @Test
    fun `empty or unknown text returns null`() {
        val model = trainFromSeed()
        assertNull(model.classify(""))
        assertNull(model.classify("!!! ???"))
    }

    @Test
    fun `single-class model is fully confident`() {
        val model = NaiveBayesClassifier.train(listOf(LabeledSample("coffee shop order", "food_drink")))
        assertEquals(1.0, model.classify("another coffee order")!!.confidence, 0.001)
    }

    @Test
    fun `incremental update absorbs a correction without a rebuild`() {
        val model = trainFromSeed()
        val before = model.classify("PREMIUM JUICE BAR order")!!.categoryId

        val updated = model.updated(LabeledSample("premium juice bar order", "health"))
        val after = updated.classify("PREMIUM JUICE BAR order")!!.categoryId

        assertEquals("health", after)
        // The original model is untouched — updates are copy-on-write.
        assertEquals(before, model.classify("PREMIUM JUICE BAR order")!!.categoryId)
    }

    @Test
    fun `update with an unsanitizable sample is a no-op`() {
        val model = trainFromSeed()
        val before = model.classify("pizza order")!!.categoryId
        val after = model.updated(LabeledSample("!!!", "health")).classify("pizza order")!!.categoryId
        assertEquals(before, after)
    }

    @Test
    fun `repeating one token cannot force a confidence`() {
        // Crafted notification spamming an in-vocabulary token must not drive
        // the posterior to ~1.0 and auto-apply (classify dedupes like train).
        val model = NaiveBayesClassifier.train(
            listOf(
                LabeledSample("pizza order delivered hot", "food_drink"),
                LabeledSample("flight ticket booking confirmed", "travel"),
            ),
        )
        val single = model.classify("pizza")!!
        val spammed = model.classify(List(300) { "pizza" }.joinToString(" "))!!
        assertEquals(single.categoryId, spammed.categoryId)
        assertEquals(single.confidence, spammed.confidence, 1e-9)
    }
}
