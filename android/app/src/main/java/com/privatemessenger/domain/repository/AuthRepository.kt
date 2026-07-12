package com.privatemessenger.domain.repository

import android.util.Log
import com.privatemessenger.PrivateMessengerApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.messages.PrivateKeyBuilder

class AuthRepository(
    private val application: PrivateMessengerApp,
) {

    /**
     * Generate a brand-new Ethereum wallet and register on XMTP.
     */
    suspend fun register(): Result<Unit> = withContext(Dispatchers.IO) {
        val prefs = application.getSharedPreferences("trace_prefs", android.content.Context.MODE_PRIVATE)
        fun trace(msg: String) {
            prefs.edit().putString("last_trace", msg).commit()
        }

        try {
            trace("1. Generating XMTP Ethereum Wallet")

            // Generate a fresh random Ethereum wallet via XMTP's PrivateKeyBuilder
            val account = PrivateKeyBuilder()

            trace("2. Building XMTP Client")
            val dbEncryptionKey = application.keyStoreManager.getDatabasePassphrase()

            val client = Client.create(
                account = account,
                options = ClientOptions(
                    api = ClientOptions.Api(
                        env = XMTPEnvironment.PRODUCTION,
                        isSecure = true
                    ),
                    appContext = application.applicationContext,
                    dbEncryptionKey = dbEncryptionKey
                )
            )

            trace("3. Storing Private Key Securely")
            val privateKeyHex = account.getPrivateKey().secp256K1.bytes.toByteArray().joinToString("") { "%02x".format(it) }
            application.keyStoreManager.storeEthereumPrivateKey(privateKeyHex)

            trace("4. Initializing App Client")
            withContext(Dispatchers.Main) {
                application.initXmtpClient(client)
            }

            trace("5. Success")
            prefs.edit().remove("last_trace").apply()
            Result.success(Unit)
        } catch (e: Throwable) {
            trace("Error: ${e.javaClass.simpleName} - ${e.message}")
            val errString = Log.getStackTraceString(e)
            Log.e("AuthRepository", "Failed to register XMTP: $errString")
            Result.failure(Exception(errString, e))
        }
    }

    /**
     * Restore an existing wallet from a private key hex string.
     * The user supplies the key they saved during their first registration.
     */
    suspend fun restore(privateKeyHex: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Strip optional 0x prefix and whitespace
            val cleanHex = privateKeyHex.trim().removePrefix("0x").removePrefix("0X")
            if (cleanHex.length != 64 || !cleanHex.all { it in "0123456789abcdefABCDEF" }) {
                return@withContext Result.failure(Exception("Invalid private key. Must be 64 hex characters (32 bytes)."))
            }

            // Reconstruct the PrivateKeyBuilder from the raw bytes
            val keyBytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(keyBytes)
            val account = PrivateKeyBuilder(privateKey)

            val dbEncryptionKey = application.keyStoreManager.getDatabasePassphrase()

            val client = Client.create(
                account = account,
                options = ClientOptions(
                    api = ClientOptions.Api(
                        env = XMTPEnvironment.PRODUCTION,
                        isSecure = true
                    ),
                    appContext = application.applicationContext,
                    dbEncryptionKey = dbEncryptionKey
                )
            )

            // Store the key securely in Keystore
            application.keyStoreManager.storeEthereumPrivateKey(cleanHex)

            withContext(Dispatchers.Main) {
                application.initXmtpClient(client)
            }

            Result.success(Unit)
        } catch (e: Throwable) {
            val errString = Log.getStackTraceString(e)
            Log.e("AuthRepository", "Failed to restore XMTP identity: $errString")
            Result.failure(Exception("Restore failed: ${e.message}", e))
        }
    }
}
