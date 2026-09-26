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
    fun `refund credits are marked as refunds`() {
        val parsed = UniversalParser.parse(
            "Refund of Rs 1,200.00 received from FLIPKART to your account on 26-09",
            now,
        )!!
        assertTrue(parsed.isCredit)
        assertTrue(parsed.isRefund)
    }

    @Test
    fun `salary is not a refund`() {
        val parsed = UniversalParser.parse(
            "INR 65,000.00 credited to your account towards SALARY from ACME CORP on 01-10",
            now,
        )!!
        assertTrue(parsed.isCredit)
        assertTrue(!parsed.isRefund)
    }

    @Test
    fun `authorization holds are marked as pending`() {
        val parsed = UniversalParser.parse(
            "Rs 100.00 held as pre-authorization by INDIAN OIL on card XX8842",
            now,
        )!!
        assertTrue(parsed.isHold)
        assertTrue(!parsed.isCredit)
    }

    @Test
    fun `pending marker flags a hold`() {
        val parsed = UniversalParser.parse("Rs 500 debited to AMAZON (pending settlement)", now)!!
        assertTrue(parsed.isHold)
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

    // ---- Regressions from the audit ----

    @Test
    fun `credit card debit is not income`() {
        val parsed = UniversalParser.parse(
            "Your credit card ending 4521 was debited Rs 1,499.00 for AMAZON PURCHASE",
            now,
        )!!
        assertTrue(!parsed.isCredit)
        assertEquals("generic_debit", parsed.ruleId)
    }

    @Test
    fun `credit card charge is not income regardless of verb order`() {
        val parsed = UniversalParser.parse(
            "Rs 299 charged on your credit card XX8842 for SPOTIFY",
            now,
        )!!
        assertTrue(!parsed.isCredit)
    }

    @Test
    fun `slash idioms like 24_7 and EMI 2_12 are not dates`() {
        val parsed = UniversalParser.parse(
            "Rs 200.00 debited from A/c XX8842. Services available 24/7. EMI 2/12 done",
            now,
        )!!
        assertEquals(now, parsed.timestamp)
    }

    @Test
    fun `keyword-less dates with a year still parse`() {
        val parsed = UniversalParser.parse("Rs 200.00 debited towards SHOP 13/09/2026 Txn OK", now)
        assertNotNull(parsed)
        assertTrue(parsed!!.timestamp != now)
    }

    @Test
    fun `european decimal comma parses for EUR`() {
        val parsed = UniversalParser.parse("EUR 9,90 debited for SUBSCRIPTION", now)!!
        assertEquals(9.90, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun `comma thousands still parse for INR`() {
        val parsed = UniversalParser.parse("Rs 12,450 debited to SWIGGY", now)!!
        assertEquals(12450.0, parsed.amount, 0.001)
    }

    @Test
    fun `yours 100 is not an amount`() {
        assertNull(UniversalParser.parse("This offer is truly yours 100 percent guaranteed", now))
    }

    @Test
    fun `absurd digit runs are rejected`() {
        assertNull(UniversalParser.parse("Rs 999999999999999999 debited to SOMETHING", now))
    }

    @Test
    fun `clock times do not become merchants`() {
        val parsed = UniversalParser.parse("Rs 100 debited to account at 10:30 today", now)
        assertNotNull(parsed)
        assertTrue(parsed!!.merchant != "10")
    }

    @Test
    fun `upper case month names parse`() {
        val parsed = UniversalParser.parse("Rs 100 debited to TEST on 15 MAR 2026", now)
        assertNotNull(parsed)
        assertTrue(parsed!!.timestamp != now)
    }

    @Test
    fun `full pan never becomes an account hint`() {
        val parsed = UniversalParser.parse(
            "Card 4321 9876 5432 1098 charged Rs 500 at TEST MERCHANT",
            now,
        )
        assertNotNull(parsed)
        assertNull(parsed!!.accountHint)
    }
}
