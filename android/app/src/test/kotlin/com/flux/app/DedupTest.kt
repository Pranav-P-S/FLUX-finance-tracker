package com.flux.app

import com.flux.app.engine.Dedup
import com.flux.app.engine.TransactionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupTest {

    private val alert = "Rs 2,450.00 debited from A/c XX8842 towards SWIGGY Refno 550012"
    private val t0 = 1_726_000_000_000L

    @Test
    fun `same alert from the same app collapses to one identity`() {
        assertEquals(Dedup.hash(alert, "com.bank.app", t0), Dedup.hash(alert, "com.bank.app", t0))
    }

    @Test
    fun `identical purchase from a different app is a distinct row`() {
        assertNotEquals(Dedup.hash(alert, "com.bank.app", t0), Dedup.hash(alert, "com.otherbank", t0))
    }

    @Test
    fun `different authorization code in the text is a distinct purchase`() {
        val secondCoffee = "Rs 450.00 debited from A/c XX8842 towards STARBUCKS Refno 550013"
        assertNotEquals(Dedup.hash(alert, "com.bank.app", t0), Dedup.hash(secondCoffee, "com.bank.app", t0))
    }

    @Test
    fun `replayed alert with different surrounding whitespace still collapses`() {
        assertEquals(
            Dedup.hash(alert, "com.bank.app", t0),
            Dedup.hash("  $alert  ", "com.bank.app", t0),
        )
    }

    @Test
    fun `identical template text hours apart is a new transaction, not a duplicate`() {
        // The same fuel-pump template or a hold followed by its final charge:
        // byte-identical text, different posting time. The old text-only hash
        // silently dropped the second one.
        val fourHoursLater = t0 + 4L * 60 * 60 * 1000
        assertNotEquals(Dedup.hash(alert, "com.bank.app", t0), Dedup.hash(alert, "com.bank.app", fourHoursLater))
    }

    @Test
    fun `redelivery seconds apart still collapses`() {
        val retry = t0 + 5_000L
        assertEquals(Dedup.hash(alert, "com.bank.app", t0), Dedup.hash(alert, "com.bank.app", retry))
    }

    @Test
    fun `hash is 64 hex chars`() {
        val h = Dedup.hash("x", "pkg", t0)
        assertEquals(64, h.length)
        assertTrue(h.all { it in "0123456789abcdef" })
    }

    @Test
    fun `training text strips digits and references from the raw payload`() {
        val sanitized = TransactionEngine.sanitizeTrainingText(
            "SWIGGY",
            "Rs 2,450.00 debited from A/c XX8842 on 26-09-26 towards SWIGGY Refno 550012",
        )
        assertFalse(sanitized.contains("2450"))
        assertFalse(sanitized.contains("8842"))
        assertFalse(sanitized.contains("550012"))
        assertTrue(sanitized.startsWith("SWIGGY"))
        assertTrue(sanitized.contains("debited"))
    }
}
