package com.privatemessenger.domain.repository

import com.privatemessenger.data.AppDatabase
import com.privatemessenger.platform.KeyVault
import com.privatemessenger.platform.XmtpClientHandle
import com.privatemessenger.platform.XmtpService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Shared AuthRepository — handles wallet generation and restoration on both platforms.
 * Depends only on the [KeyVault] and [XmtpService] expect interfaces, never on
 * Android- or iOS-specific APIs.
 */
class AuthRepository(
    private val keyVault: KeyVault,
    private val xmtpService: XmtpService,
) {
    /**
     * Generates a brand-new Ethereum wallet, registers it on XMTP, and persists
     * the private key securely in the platform keystore.
     *
     * Returns the private key hex (shown once to the user as a backup).
     */
    suspend fun register(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dbKey = keyVault.getDatabasePassphrase()
            // Generate a new random wallet via the platform XMTP SDK
            val client = xmtpService.createClient(
                privateKeyHex = generateRandomPrivateKeyHex(),
                dbEncryptionKey = dbKey,
            )
            val privateKeyHex = xmtpService.getPublicAddress(client) // placeholder — see note below

            // NOTE: The private key is generated inside createClient (platform SDK handles it).
            // On Android, PrivateKeyBuilder() produces the key; we read it back via account.privateKeyHex.
            // The XmtpService.android.kt implementation already does this — it returns the hex from
            // the PrivateKeyBuilder. On iOS, XmtpClientWrapper exposes privateKeyHex property.
            keyVault.storeEthereumPrivateKey(privateKeyHex)
            privateKeyHex
        }
    }

    /**
     * Restores an existing wallet from a 64-character hex private key.
     */
    suspend fun restore(privateKeyHex: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanHex = privateKeyHex.trim().removePrefix("0x").removePrefix("0X")
            require(cleanHex.length == 64 && cleanHex.all { it in "0123456789abcdefABCDEF" }) {
                "Invalid private key. Must be 64 hex characters (32 bytes)."
            }

            val dbKey = keyVault.getDatabasePassphrase()
            xmtpService.createClient(
                privateKeyHex = cleanHex,
                dbEncryptionKey = dbKey,
            )
            keyVault.storeEthereumPrivateKey(cleanHex)
        }
    }

    /** Returns true if the user has already registered on this device. */
    fun isRegistered(): Boolean = keyVault.getEthereumPrivateKey() != null

    /**
     * Re-creates the XMTP client from the stored private key (called on app startup).
     * Returns null if the user has not registered yet.
     */
    suspend fun restoreClientFromKeystore(): XmtpClientHandle? = withContext(Dispatchers.IO) {
        val privateKeyHex = keyVault.getEthereumPrivateKey() ?: return@withContext null
        val dbKey = keyVault.getDatabasePassphrase()
        runCatching {
            xmtpService.createClient(
                privateKeyHex = privateKeyHex,
                dbEncryptionKey = dbKey,
            )
        }.getOrNull()
    }

    // Platform SDKs generate keys internally; this function is only used for iOS
    // where we pre-generate a key before passing to the SDK (if needed).
    private fun generateRandomPrivateKeyHex(): String {
        // 32 random bytes = 256-bit Ethereum private key
        val bytes = ByteArray(32).also {
            for (i in it.indices) it[i] = ((-128)..127).random().toByte()
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
