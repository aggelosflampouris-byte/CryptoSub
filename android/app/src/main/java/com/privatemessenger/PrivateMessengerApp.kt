package com.privatemessenger

import android.app.Application
import com.privatemessenger.data.local.AppDatabase
import com.privatemessenger.keystore.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.CertificatePinner
import org.xmtp.android.library.Client
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.privatemessenger.workers.SendMessageWorker

/**
 * Application subclass that initializes the cryptographic and storage
 * infrastructure on startup.
 *
 * Initialization order matters:
 *   1. KeyStoreManager  â€” provides the database passphrase
 *   2. AppDatabase       â€” opens the encrypted database
 *   3. SignalProtocolStoreImpl â€” backed by the database
 *   4. Crypto services   â€” KeyManager, SessionBuilder, RatchetEngine
 *
 * In a production app these would be provided via dependency injection
 * (e.g. Hilt). For now they're exposed as lazy properties on the
 * Application class to keep the initial implementation simple.
 */
class PrivateMessengerApp : Application() {

    /** Application-scoped coroutine scope — cancelled on process death. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Shared OkHttpClient — connection pool is reused across all upload calls. */
    val httpClient: OkHttpClient by lazy {
        val certificatePinner = CertificatePinner.Builder()
            // Let's Encrypt Root & Intermediate keys used by catbox.moe
            .add("catbox.moe", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
            .add("catbox.moe", "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=") // Let's Encrypt R3
            .build()
            
        OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }

    lateinit var keyStoreManager: KeyStoreManager
        private set

    lateinit var database: AppDatabase
        private set

    var xmtpClient: Client? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // Set up global crash handler to catch the elusive bug
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val stackTrace = android.util.Log.getStackTraceString(exception)
            getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("crash_log", stackTrace)
                .commit() // must be synchronous commit before crash
            defaultHandler?.uncaughtException(thread, exception)
        }

        // Initialize SQLCipher native libraries
        System.loadLibrary("sqlcipher")

        // 1. Keystore â€” always available
        keyStoreManager = KeyStoreManager(this)

        // 2. Encrypted database â€” always available
        val passphrase = keyStoreManager.getDatabasePassphrase()
        database = AppDatabase.getInstance(this, passphrase)

        // 3. Try to load existing identity (post-registration)
        initCryptoIfRegistered()
    }

    /**
     * Called after successful registration or on boot to initialize the XMTP
     * layer with the newly created Ethereum identity.
     */
    fun initXmtpClient(client: Client) {
        xmtpClient = client

        // Kick off a background worker to retry any messages stuck in SENDING
        // as soon as the network becomes available.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "retry_offline_messages",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun initCryptoIfRegistered() {
        // Will be called by splash screen or MainActivity, removing auto-init from here for now
        // because XMTP Client.create might be suspendable.
    }

    /**
     * Returns true if the user has completed registration and the
     * crypto layer is initialized.
     */
    fun isRegistered(): Boolean = keyStoreManager.getEthereumPrivateKey() != null

    /**
     * Logs the user out by clearing all keys and database contents.
     */
    fun logout() {
        keyStoreManager.clearAll()
        xmtpClient = null
        applicationScope.launch(Dispatchers.IO) {
            database.clearAllTables()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
