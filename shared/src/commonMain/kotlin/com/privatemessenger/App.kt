package com.privatemessenger

import androidx.compose.runtime.Composable
import com.privatemessenger.data.AppDatabase
import com.privatemessenger.platform.KeyVault
import com.privatemessenger.platform.XmtpClientHandle
import com.privatemessenger.platform.XmtpService
import com.privatemessenger.ui.navigation.AppNavGraph
import com.privatemessenger.ui.theme.PrivateMessengerTheme

/**
 * Shared Compose entry point invoked by both the Android Activity and the iOS UIViewController.
 * All platform-specific bootstrapping (DB construction, KeyVault, XMTP client initialization)
 * is done in the platform host before calling this composable.
 */
@Composable
fun App(
    database: AppDatabase,
    keyVault: KeyVault,
    xmtpService: XmtpService,
    xmtpClient: XmtpClientHandle?,
    onXmtpClientReady: (XmtpClientHandle) -> Unit,
    startDestination: String,
) {
    PrivateMessengerTheme {
        AppNavGraph(
            database = database,
            keyVault = keyVault,
            xmtpService = xmtpService,
            xmtpClient = xmtpClient,
            onXmtpClientReady = onXmtpClientReady,
            startDestination = startDestination,
        )
    }
}
