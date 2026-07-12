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
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.ui.navigation.AppNavGraph
import com.privatemessenger.ui.theme.PrivateMessengerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for captured crash logs
        val crashPrefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val crashLog = crashPrefs.getString("crash_log", null)
        if (crashLog != null) {
            AlertDialog.Builder(this)
                .setTitle("App Crashed!")
                .setMessage("Please copy this and send it to the developer:\n\n$crashLog")
                .setPositiveButton("OK") { _, _ -> }
                .show()
            crashPrefs.edit().remove("crash_log").apply()
        }
        
        val app = application as PrivateMessengerApp
        val startDestination = if (app.isRegistered()) "chat_list" else "registration"

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
