package com.privatemessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.privatemessenger.domain.repository.AuthRepository
import com.privatemessenger.notifications.XmtpBackgroundService
import kotlinx.coroutines.launch

/**
 * Thin Android host Activity.
 *
 * Delegates all business logic and UI to the :shared KMP module via [App].
 * The only Android-specific concern here is bootstrapping and starting the
 * background foreground service.
 */
class MainActivity : ComponentActivity() {

    private val cryptoSubApp get() = application as CryptoSubApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = cryptoSubApp
        val authRepository = AuthRepository(app.keyVault, app.xmtpService)

        // If the user has already registered, restore the XMTP client before rendering the UI
        if (app.isRegistered() && app.xmtpClient == null) {
            lifecycleScope.launch {
                val client = authRepository.restoreClientFromKeystore()
                if (client != null) {
                    app.setXmtpClient(client)
                    // Restart the background streaming service
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        android.content.Intent(this@MainActivity, XmtpBackgroundService::class.java),
                    )
                }
            }
        }

        val startDestination = if (app.isRegistered()) "chat_list" else "registration"

        setContent {
            App(
                database = app.database,
                keyVault = app.keyVault,
                xmtpService = app.xmtpService,
                xmtpClient = app.xmtpClient,
                onXmtpClientReady = { client ->
                    app.setXmtpClient(client)
                    ContextCompat.startForegroundService(
                        this,
                        android.content.Intent(this, XmtpBackgroundService::class.java),
                    )
                },
                startDestination = startDestination,
            )
        }
    }
}
