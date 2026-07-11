package com.privatemessenger.crypto

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.fingerprint.DisplayableFingerprint
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator

/**
 * SafetyNumberGenerator creates verifiable safety numbers (fingerprints)
 * that two users can compare to confirm they are communicating with the
 * expected identity keys, with no man-in-the-middle.
 *
 * Usage:
 *   1. Both users open the safety-number screen for their conversation.
 *   2. Each device computes the safety number from both identity keys.
 *   3. They compare numbers (verbally, by scanning a QR code, etc.).
 *   4. If the numbers match, the session is verified.
 *
 * If a user's identity key changes (e.g. they reinstalled the app),
 * the safety number changes, and the peer sees a "safety number changed"
 * warning — the correct response is to re-verify, not to silently continue.
 */
class SafetyNumberGenerator {

    companion object {
        /**
         * Version tag for the fingerprint format. Bumping this forces
         * re-verification if the algorithm ever changes.
         */
        private const val FINGERPRINT_VERSION = 2

        /**
         * Number of iterations in the hash chain. Higher = harder to
         * brute-force a collision, at the cost of computation time.
         * 5200 matches Signal's production value.
         */
        private const val ITERATIONS = 5200
    }

    private val generator = NumericFingerprintGenerator(ITERATIONS)

    /**
     * Generates a displayable safety number for a conversation between
     * the local user and a remote user.
     *
     * The safety number is symmetric: both sides compute the same value
     * regardless of who calls this method, because the generator sorts
     * the inputs internally.
     *
     * @param localUserId       This device's user ID (stable identifier)
     * @param localIdentityKey  This device's public identity key
     * @param remoteUserId      The peer's user ID
     * @param remoteIdentityKey The peer's public identity key
     *
     * @return A [DisplayableFingerprint] whose [DisplayableFingerprint.getDisplayText]
     *         returns a numeric string (e.g. "12345 67890 12345 67890 12345 67890")
     *         suitable for display in the UI.
     */
    fun generate(
        localUserId: String,
        localIdentityKey: IdentityKey,
        remoteUserId: String,
        remoteIdentityKey: IdentityKey,
    ): DisplayableFingerprint {
        val fingerprint = generator.createFor(
            FINGERPRINT_VERSION,
            localUserId.toByteArray(),
            localIdentityKey,
            remoteUserId.toByteArray(),
            remoteIdentityKey,
        )
        return fingerprint.displayableFingerprint
    }

    /**
     * Formats the raw numeric fingerprint into groups of 5 digits
     * separated by spaces for readability.
     */
    fun formatForDisplay(fingerprint: DisplayableFingerprint): String {
        val raw = fingerprint.getDisplayText()
        return raw.chunked(5).joinToString(" ")
    }
}
