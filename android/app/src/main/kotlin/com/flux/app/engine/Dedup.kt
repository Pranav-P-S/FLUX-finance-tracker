package com.flux.app.engine

import java.security.MessageDigest

/**
 * Transaction identity. Re-delivered notifications are byte-identical AND land
 * within a few seconds of the original, so identity = (posting app, raw alert
 * text, posting-time bucket). A later, genuinely new debit whose text happens
 * to be identical — the same fuel-pump template, or a hold followed by its
 * final charge — falls in a different bucket and is kept, while carrier-level
 * redelivery retries collapse onto the original row. Surrounding whitespace is
 * normalized; case is preserved because payment references are case-sensitive.
 */
object Dedup {
    /** Re-deliveries arrive within seconds; minutes apart is a new event. */
    private const val DUPLICATE_WINDOW_MS = 10L * 60 * 1000

    fun hash(rawText: String, sourcePackage: String, timestampMs: Long): String {
        val bucket = timestampMs / DUPLICATE_WINDOW_MS
        val payload = "${sourcePackage.trim().lowercase()}|$bucket|${rawText.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
