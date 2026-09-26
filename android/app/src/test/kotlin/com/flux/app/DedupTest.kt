package com.flux.app

import com.flux.app.engine.Dedup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupTest {

    private val alert = "Rs 2,450.00 debited from A/c XX8842 towards SWIGGY Refno 550012"

    @Test
    fun `same alert from the same app collapses to one identity`() {
        assertEquals(Dedup.hash(alert, "com.bank.app"), Dedup.hash(alert, "com.bank.app"))
    }

    @Test
    fun `identical purchase from a different app is a distinct row`() {
        assertNotEquals(Dedup.hash(alert, "com.bank.app"), Dedup.hash(alert, "com.otherbank"))
    }

    @Test
    fun `different authorization code in the text is a distinct purchase`() {
        val secondCoffee = "Rs 450.00 debited from A/c XX8842 towards STARBUCKS Refno 550013"
        assertNotEquals(Dedup.hash(alert, "com.bank.app"), Dedup.hash(secondCoffee, "com.bank.app"))
    }

    @Test
    fun `replayed alert with different surrounding whitespace still collapses`() {
        assertEquals(
            Dedup.hash(alert, "com.bank.app"),
            Dedup.hash("  $alert  ", "com.bank.app"),
        )
    }

    @Test
    fun `hash is 64 hex chars`() {
        val h = Dedup.hash("x", "pkg")
        assertEquals(64, h.length)
        assertTrue(h.all { it in "0123456789abcdef" })
    }
}
