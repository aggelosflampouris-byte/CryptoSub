package com.privatemessenger.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.ui.navigation.AppNavGraph
import com.privatemessenger.ui.theme.PrivateMessengerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.XMTPEnvironment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for captured crash logs and trace logs
        val crashPrefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val crashLog = crashPrefs.getString("crash_log", null)
        
        val tracePrefs = getSharedPreferences("trace_prefs", Context.MODE_PRIVATE)
        val lastTrace = tracePrefs.getString("last_trace", null)
        
        if (lastTrace != null) {
            AlertDialog.Builder(this)
                .setTitle("Diagnostic Trace")
                .setMessage("App died after reaching this step:\n\n$lastTrace\n\nCrash Log:\n$crashLog")
                .setPositiveButton("OK") { _, _ -> }
                .show()
            tracePrefs.edit().remove("last_trace").apply()
            crashPrefs.edit().remove("crash_log").apply()
        } else if (crashLog != null) {
            AlertDialog.Builder(this)
                .setTitle("App Crashed!")
                .setMessage("Please copy this and send it to the developer:\n\n$crashLog")
                .setPositiveButton("OK") { _, _ -> }
                .show()
            crashPrefs.edit().remove("crash_log").apply()
        }
        
        val app = application as PrivateMessengerApp
        val startDestination = if (app.isRegistered()) "chat_list" else "registration"

        if (app.isRegistered() && app.xmtpClient == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val privateKeyHex = app.keyStoreManager.getEthereumPrivateKey()
                    if (privateKeyHex != null) {
                        val keyBytes = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        val privateKey = org.xmtp.android.library.messages.PrivateKeyBuilder.buildFromPrivateKeyData(keyBytes)
                        val account = org.xmtp.android.library.messages.PrivateKeyBuilder(privateKey)
                        val dbEncryptionKey = app.keyStoreManager.getDatabasePassphrase()
                        val client = Client().build(
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
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to initialize XMTP Client on boot", e)
                }
            }
        }

        setContent {
            PrivateMessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        startDestination = startDestination,
                        app = app
                    )
                }
            }
        }
    }
}
