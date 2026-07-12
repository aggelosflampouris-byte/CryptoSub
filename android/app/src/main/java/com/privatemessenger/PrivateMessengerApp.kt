package com.privatemessenger

import android.app.Application
import com.privatemessenger.crypto.KeyManager
import com.privatemessenger.crypto.RatchetEngine
import com.privatemessenger.crypto.SafetyNumberGenerator
import com.privatemessenger.crypto.SignalProtocolStoreImpl
import com.privatemessenger.crypto.SignalSessionBuilder
import com.privatemessenger.data.local.AppDatabase
import com.privatemessenger.keystore.KeyStoreManager
import org.signal.libsignal.protocol.IdentityKeyPair

/**
 * Application subclass that initializes the cryptographic and storage
 * infrastructure on startup.
 *
 * Initialization order matters:
 *   1. KeyStoreManager  — provides the database passphrase
 *   2. AppDatabase       — opens the encrypted database
 *   3. SignalProtocolStoreImpl — backed by the database
 *   4. Crypto services   — KeyManager, SessionBuilder, RatchetEngine
 *
 * In a production app these would be provided via dependency injection
 * (e.g. Hilt). For now they're exposed as lazy properties on the
 * Application class to keep the initial implementation simple.
 */
class PrivateMessengerApp : Application() {

    lateinit var keyStoreManager: KeyStoreManager
        private set

    lateinit var database: AppDatabase
        private set

    // Crypto services are only available after registration
    var protocolStore: SignalProtocolStoreImpl? = null
        private set

    var keyManager: KeyManager? = null
        private set

    var sessionBuilder: SignalSessionBuilder? = null
        private set

    var ratchetEngine: RatchetEngine? = null
        private set

    val safetyNumberGenerator = SafetyNumberGenerator()

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

        // 1. Keystore — always available
        keyStoreManager = KeyStoreManager(this)

        // 2. Encrypted database — always available
        val passphrase = keyStoreManager.getDatabasePassphrase()
        database = AppDatabase.getInstance(this, passphrase)

        // 3. Try to load existing identity (post-registration)
        initCryptoIfRegistered()
    }

    /**
     * Called after successful registration to initialize the crypto
     * layer with the newly created identity key pair.
     */
    fun initCryptoAfterRegistration(identityKeyPair: IdentityKeyPair, registrationId: Int) {
        // Persist the identity key pair securely
        keyStoreManager.storeIdentityKeyPair(identityKeyPair.serialize())
        keyStoreManager.storeRegistrationId(registrationId)

        // Initialize crypto services
        initCryptoServices(identityKeyPair, registrationId)
    }

    private fun initCryptoIfRegistered() {
        val serializedKeyPair = keyStoreManager.loadIdentityKeyPair() ?: return
        val registrationId = keyStoreManager.loadRegistrationId()
        if (registrationId == -1) return

        val identityKeyPair = IdentityKeyPair(serializedKeyPair)
        initCryptoServices(identityKeyPair, registrationId)
    }

    private fun initCryptoServices(identityKeyPair: IdentityKeyPair, registrationId: Int) {
        val store = SignalProtocolStoreImpl(
            dao = database.signalDao(),
            localIdentityKeyPair = identityKeyPair,
            localRegistrationId = registrationId,
        )
        protocolStore = store
        keyManager = KeyManager(store)
        sessionBuilder = SignalSessionBuilder(store)
        ratchetEngine = RatchetEngine(store)
    }

    /**
     * Returns true if the user has completed registration and the
     * crypto layer is initialized.
     */
    fun isRegistered(): Boolean = protocolStore != null
}
