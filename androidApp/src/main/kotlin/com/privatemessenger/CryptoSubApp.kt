package com.privatemessenger

import android.app.Application
import android.content.Context
import com.privatemessenger.data.AppDatabase
import com.privatemessenger.data.buildAndroidDatabase
import com.privatemessenger.platform.KeyVault
import com.privatemessenger.platform.XmtpClientHandle
import com.privatemessenger.platform.XmtpService
import com.privatemessenger.platform.notificationContext

/**
 * Thin Android Application subclass.
 *
 * Initialises the platform components from the :shared KMP module and
 * holds them as lazily-created singletons accessible to the Activity.
 * This replaces the old PrivateMessengerApp (which mixed platform and
 * business logic together).
 */
class CryptoSubApp : Application() {

    lateinit var keyVault: KeyVault
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var xmtpService: XmtpService
        private set

    /** Null until the user registers or the XMTP client is restored on boot. */
    var xmtpClient: XmtpClientHandle? = null

    override fun onCreate() {
        super.onCreate()

        // Wire up the Android notification context for the shared notification helpers
        notificationContext = this as Context

        keyVault = KeyVault(this)

        val passphrase = keyVault.getDatabasePassphrase()
        database = buildAndroidDatabase(this, passphrase)

        xmtpService = XmtpService(this)
    }

    /** Called after registration or on boot to set the active XMTP client. */
    fun setXmtpClient(client: XmtpClientHandle) {
        xmtpClient = client
    }

    fun isRegistered(): Boolean = keyVault.getEthereumPrivateKey() != null
}
