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
            // The database encryption key is 32 bytes securely stored in Android Keystore
            val dbEncryptionKey = application.keyStoreManager.getDatabasePassphrase()
            
            val client = Client().build(
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
            // Extract the private key and save it securely
            val privateKeyHex = account.getPrivateKey().privateKeyBytes.joinToString("") { "%02x".format(it) }
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
}
