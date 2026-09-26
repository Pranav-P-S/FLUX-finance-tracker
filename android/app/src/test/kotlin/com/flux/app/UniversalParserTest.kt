package com.flux.app

import com.flux.app.parse.UniversalParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalParserTest {

    private val now = 1_726_000_000_000L

    @Test
    fun `parses exact emulator shell notification`() {
        val text = "HDFC BANK\nRs 2,450.00 debited from A/c XX8842 on 26-09-26 towards SWIGGY Refno 550012"
        val parsed = UniversalParser.parse(text, now)
        assertNotNull(parsed)
        assertEquals(2450.0, parsed!!.amount, 0.001)
        assertEquals(false, parsed.isCredit)
    }

    @Test
    fun `parses generic debit with merchant and account`() {
        val text = "Rs 450.00 debited from A/c XX1234 to SWIGGY on 12-09-26. Not you? Call 1800123"
        val parsed = UniversalParser.parse(text, now)!!

        assertEquals(false, parsed.isCredit)
        assertEquals(450.00, parsed.amount, 0.001)
        assertEquals("INR", parsed.currency)
        assertEquals("SWIGGY", parsed.merchant)
        assertEquals("XX1234", parsed.accountHint)
        assertEquals("generic_debit", parsed.ruleId)
        assertTrue(parsed.parseConfidence >= 0.8)
    }

    @Test
    fun `parses credit with salary wording`() {
        val text = "INR 65,000.00 credited to your account towards SALARY from ACME CORP on 01-10"
        val parsed = UniversalParser.parse(text, now)!!

        assertTrue(parsed.isCredit)
        assertEquals(65000.0, parsed.amount, 0.001)
        assertEquals("SALARY", parsed.merchant)
        assertEquals("generic_credit", parsed.ruleId)
    }

    @Test
    fun `parses dollar amounts`() {
        val parsed = UniversalParser.parse("Your card was charged $12.50 at Netflix.com", now)!!
        assertEquals(12.50, parsed.amount, 0.001)
        assertEquals("USD", parsed.currency)
        assertTrue(!parsed.isCredit)
    }

    @Test
    fun `extracts upi handle as merchant`() {
        val parsed = UniversalParser.parse("Paid Rs 250 to swiggy@ybl via UPI. Ref 4321", now)!!
        assertEquals("swiggy", parsed.merchant)
    }

    @Test
    fun `rejects otp messages`() {
        assertNull(UniversalParser.parse("Your OTP for login is 482910. Do not share. Rs 0", now))
    }

    @Test
    fun `returns null when no amount present`() {
        assertNull(UniversalParser.parse("Welcome to our bank! Enjoy great offers today.", now))
    }

    @Test
    fun `heuristic path marks low confidence when type unclear`() {
        // No debit/credit verb, but a money amount — heuristic fallback.
        val parsed = UniversalParser.parse("Txn of Rs 999.00 at FLIPKART INTERNET PVT LTD", now)
        assertNotNull(parsed)
        assertEquals("heuristic", parsed!!.ruleId)
        assertTrue(parsed.parseConfidence < 0.8)
        assertEquals(999.0, parsed.amount, 0.001)
    }

    @Test
    fun `caps run merchant without prefix words`() {
        val parsed = UniversalParser.parse("Rs 549 debited on card x4521 for NETFLIX SUBSCRIPTION", now)
        assertNotNull(parsed)
        assertEquals("NETFLIX SUBSCRIPTION", parsed!!.merchant)
    }

    @Test
    fun `textual dates parse to midnight epoch`() {
        val parsed = UniversalParser.parse("Rs 100 debited to TEST on 15 Mar 2026", now)
        assertNotNull(parsed)
        assertTrue(parsed!!.timestamp != now)
    }
}
