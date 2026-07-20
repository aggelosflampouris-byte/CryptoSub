package com.privatemessenger

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.privatemessenger.data.buildIosDatabase
import com.privatemessenger.domain.repository.AuthRepository
import com.privatemessenger.platform.KeyVault
import com.privatemessenger.platform.XmtpService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS entry point — called from ContentView.swift via Kotlin/Native ObjC interop.
 * Creates the shared KMP Compose UIViewController.
 */
@Suppress("FunctionName", "unused") // Called from Swift
fun MainViewController(): UIViewController {
    val keyVault = KeyVault()
    val database = buildIosDatabase()
    val xmtpService = XmtpService()
    val authRepository = AuthRepository(keyVault, xmtpService)

    // Attempt to restore the XMTP client synchronously if key is available.
    // On first launch this will be null and the user will be shown Registration.
    var restoredClient = authRepository.run {
        // Non-suspending check
        if (isRegistered()) {
            // Restore on the main coroutine scope; the UI will update reactively
            var c = xmtpService.run { null } // placeholder for synchronous check
            MainScope().launch {
                c = authRepository.restoreClientFromKeystore()
            }
            c
        } else null
    }

    val startDestination = if (authRepository.isRegistered()) "chat_list" else "registration"

    return ComposeUIViewController {
        App(
            database = database,
            keyVault = keyVault,
            xmtpService = xmtpService,
            xmtpClient = remember { restoredClient },
            onXmtpClientReady = { client ->
                // Schedule a background refresh task once the client is ready
                BackgroundRefreshHandler.shared?.register(client)
            },
            startDestination = startDestination,
        )
    }
}
