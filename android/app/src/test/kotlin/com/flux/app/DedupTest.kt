package com.flux.app

import com.flux.app.engine.Dedup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupTest {

    @Test
    fun `same inputs produce identical hash`() {
        val a = Dedup.hash(1_726_000_000_123, -450.0, "SWIGGY")
        val b = Dedup.hash(1_726_000_000_987, -450.0, "SWIGGY")
        assertEquals(a, b) // same minute, amounts collapse
    }

    @Test
    fun `merchant case is normalized`() {
        assertEquals(
            Dedup.hash(1_726_000_000, -10.0, "uber INDIA"),
            Dedup.hash(1_726_000_000, -10.0, "Uber India"),
        )
    }

    @Test
    fun `different amounts or minutes diverge`() {
        val base = Dedup.hash(1_726_000_000, -10.0, "uber")
        assertNotEquals(base, Dedup.hash(1_726_000_000, -11.0, "uber"))
        assertNotEquals(base, Dedup.hash(1_726_060_000, -10.0, "uber")) // next minute
        assertNotEquals(base, Dedup.hash(1_726_000_000, 10.0, "uber")) // debit vs credit
    }

    @Test
    fun `hash is 64 hex chars`() {
        val h = Dedup.hash(0, 1.0, "x")
        assertEquals(64, h.length)
        assertTrue(h.all { it in "0123456789abcdef" })
    }
}
