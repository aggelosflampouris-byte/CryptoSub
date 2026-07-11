package com.privatemessenger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
