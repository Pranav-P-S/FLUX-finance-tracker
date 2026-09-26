package com.flux.app.engine

import java.security.MessageDigest

/**
 * Transaction identity. The hash covers the raw alert text and the posting
 * app: a re-delivered notification is byte-identical and collapses onto the
 * original row, while two genuine purchases that share an amount, payee and
 * minute carry different authorization codes in their text and are both kept.
 * Surrounding whitespace is normalized; case is preserved because payment
 * references are case-sensitive.
 */
object Dedup {
    fun hash(rawText: String, sourcePackage: String): String {
        val payload = "${sourcePackage.trim().lowercase()}|${rawText.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
