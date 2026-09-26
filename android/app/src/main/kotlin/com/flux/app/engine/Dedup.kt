package com.flux.app.engine

import java.security.MessageDigest

/**
 * Transaction identity: SHA-256 over `epochMinute|amount|payee` (case-normalized).
 * Used as the unique index on the transactions table so replayed or duplicated
 * bank alerts collapse into a single row.
 */
object Dedup {
    fun hash(timestampMs: Long, amount: Double, merchant: String): String {
        val minute = timestampMs / 60_000
        val payload = "%d|%.2f|%s".format(minute, amount, merchant.trim().lowercase())
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
