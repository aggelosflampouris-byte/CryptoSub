package com.privatemessenger.crypto

import android.util.Log
import com.google.gson.Gson
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES-256-GCM encryption and trial-decryption for the Sealed Sender
 * metadata (sender user ID and device ID).
 *
 * This layer ensures the routing server (Go backend) only sees an opaque
 * ciphertext payload for the sender identity. The recipient uses Trial Decryption
 * against their local contacts' Profile Keys to discover the sender.
 */
class SealedSenderCrypto(private val gson: Gson = Gson()) {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12 // 96 bits for GCM
    }

    data class SenderIdentity(
        val userId: String,
        val deviceId: Int
    )

    /**
     * Encrypts the sender's identity using their own 32-byte Profile Key.
     * The resulting ciphertext is placed in the Envelope's `sealed_sender_ciphertext`.
     */
    fun encrypt(
        senderUserId: String,
        senderDeviceId: Int,
        myProfileKey: ByteArray
    ): ByteArray {
        require(myProfileKey.size == 32) { "Profile key must be exactly 32 bytes for AES-256" }

        val identity = SenderIdentity(senderUserId, senderDeviceId)
        val plaintext = gson.toJson(identity).toByteArray()

        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val secretKey = SecretKeySpec(myProfileKey, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(plaintext)
        
        // Output format: [IV (12 bytes)] + [Ciphertext + GCM Tag]
        return iv + ciphertext
    }

    /**
     * Attempts to decrypt an incoming Sealed Sender ciphertext using a list
     * of candidate Profile Keys (e.g., from the local Contact database).
     *
     * This uses "Trial Decryption". Because AES-GCM includes an authentication
     * tag, decrypting with the wrong key will throw an exception (MAC mismatch),
     * making it cryptographically safe to trial-decrypt.
     *
     * @param ciphertext The incoming `sealed_sender_ciphertext`.
     * @param candidateKeys A map of Contact User ID -> 32-byte Profile Key.
     * @return The decrypted [SenderIdentity] and the matching `userId` from the candidate map,
     *         or null if no keys matched (e.g. non-contact or corrupted data).
     */
    fun trialDecrypt(
        ciphertext: ByteArray,
        candidateKeys: Map<String, ByteArray>
    ): Pair<SenderIdentity, String>? {
        if (ciphertext.size < IV_LENGTH + (GCM_TAG_LENGTH / 8)) {
            Log.w("SealedSenderCrypto", "Ciphertext too short to contain IV and Tag")
            return null
        }

        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val encryptedData = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        for ((candidateUserId, profileKey) in candidateKeys) {
            try {
                val secretKey = SecretKeySpec(profileKey, "AES")
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                // If this succeeds without throwing AEADBadTagException, we found the sender!
                val plaintext = cipher.doFinal(encryptedData)
                val identity = gson.fromJson(String(plaintext), SenderIdentity::class.java)
                return Pair(identity, candidateUserId)

            } catch (e: Exception) {
                // Expected failure for all incorrect keys. Move to the next candidate.
                continue
            }
        }

        return null
    }
}
