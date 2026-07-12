package com.privatemessenger.crypto

import android.security.keystore.KeyProperties
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES-256-GCM encryption for large media attachments.
 * These keys are strictly generated on-the-fly, used once to encrypt a single blob,
 * and then the raw key bytes are transmitted securely via the Signal Double Ratchet payload.
 */
object AttachmentCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val KEY_LENGTH_BIT = 256

    class EncryptedAttachment(
        val ciphertext: ByteArray,
        val key: ByteArray,
        val iv: ByteArray
    )

    /**
     * Generates a random AES-256 key and IV, encrypts the plaintext data,
     * and returns the ciphertext along with the key material needed to decrypt it.
     */
    fun encrypt(plaintext: ByteArray): EncryptedAttachment {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGen.init(KEY_LENGTH_BIT, SecureRandom())
        val secretKey = keyGen.generateKey()

        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedAttachment(ciphertext, secretKey.encoded, iv)
    }

    /**
     * Decrypts an attachment downloaded from S3 using the key and IV
     * that were received securely via the Double Ratchet.
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, KeyProperties.KEY_ALGORITHM_AES)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return cipher.doFinal(ciphertext)
    }
}
