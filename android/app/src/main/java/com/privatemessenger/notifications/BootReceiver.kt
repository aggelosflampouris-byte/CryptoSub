package com.privatemessenger.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.privatemessenger.PrivateMessengerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.XMTPEnvironment

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device booted. Checking if background service needs to start.")
            
            val app = context.applicationContext as? PrivateMessengerApp ?: return
            
            // Only start the service if the user is fully registered
            if (app.isRegistered()) {
                // If XMTP client isn't initialized yet, we must initialize it before the service tries to use it
                if (app.xmtpClient == null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val privateKeyHex = app.keyStoreManager.getEthereumPrivateKey()
                            if (privateKeyHex != null) {
                                val keyBytes = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                val privateKey = org.xmtp.android.library.messages.PrivateKeyBuilder.buildFromPrivateKeyData(keyBytes)
                                val account = org.xmtp.android.library.messages.PrivateKeyBuilder(privateKey)
                                val dbEncryptionKey = app.keyStoreManager.getDatabasePassphrase()
                                
                                val client = Client.create(
                                    account = account,
                                    options = ClientOptions(
                                        api = ClientOptions.Api(
                                            env = XMTPEnvironment.PRODUCTION,
                                            isSecure = true
                                        ),
                                        appContext = app.applicationContext,
                                        dbEncryptionKey = dbEncryptionKey
                                    )
                                )
                                app.initXmtpClient(client)
                                
                                // Now that it's initialized, start the service
                                val serviceIntent = Intent(context, XmtpBackgroundService::class.java)
                                ContextCompat.startForegroundService(context, serviceIntent)
                                Log.d("BootReceiver", "XMTP Client initialized and background service started.")
                            }
                        } catch (e: Exception) {
                            Log.e("BootReceiver", "Failed to restore identity on boot", e)
                        }
                    }
                } else {
                    // Already initialized (unlikely on boot, but safe to check)
                    val serviceIntent = Intent(context, XmtpBackgroundService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}
