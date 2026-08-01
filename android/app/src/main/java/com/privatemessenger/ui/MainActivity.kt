package com.privatemessenger.ui

import android.content.Context
import android.os.Bundle
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.privatemessenger.utils.SettingsManager
import androidx.biometric.BiometricPrompt
import android.view.WindowManager
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.ui.navigation.AppNavGraph
import com.privatemessenger.ui.screens.settings.AppTheme
import com.privatemessenger.ui.screens.settings.ThemePreference
import com.privatemessenger.ui.theme.PrivateMessengerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.RemoteAttachmentCodec
import org.xmtp.android.library.XMTPEnvironment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Notification Permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (!isGranted) {
                        android.util.Log.w("MainActivity", "Notification permission denied by user.")
                    }
                }.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
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
                        Client.register(codec = AttachmentCodec())
                        Client.register(codec = RemoteAttachmentCodec())
                        app.initXmtpClient(client)
                        
                        // Start the background listening service
                        val serviceIntent = android.content.Intent(this@MainActivity, com.privatemessenger.notifications.XmtpBackgroundService::class.java)
                        this@MainActivity.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to initialize XMTP Client on boot", e)
                }
            }
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            val settingsManager = remember { SettingsManager.getInstance(this@MainActivity) }
            val isScreenshotProtectionEnabled by settingsManager.isScreenshotProtectionEnabled.collectAsState(initial = false)
            val isBiometricEnabled by settingsManager.isBiometricLockEnabled.collectAsState(initial = false)
            val appLockPin by settingsManager.appLockPin.collectAsState(initial = null)
            
            LaunchedEffect(isScreenshotProtectionEnabled) {
                if (isScreenshotProtectionEnabled) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            var isLocked by rememberSaveable { mutableStateOf(false) }
            var backgroundTime by rememberSaveable { mutableStateOf(0L) }
            val lifecycleOwner = LocalLifecycleOwner.current
            
            LaunchedEffect(isBiometricEnabled, appLockPin) {
                if ((isBiometricEnabled || appLockPin != null) && System.currentTimeMillis() - backgroundTime > 60_000L) {
                    isLocked = true
                }
            }

            DisposableEffect(lifecycleOwner, isBiometricEnabled, appLockPin) {
                val observer = LifecycleEventObserver { _, event ->
                    if (isBiometricEnabled || appLockPin != null) {
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                backgroundTime = System.currentTimeMillis()
                            }
                            Lifecycle.Event.ON_START -> {
                                if (System.currentTimeMillis() - backgroundTime > 60_000L) {
                                    isLocked = true
                                }
                            }
                            else -> {}
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Moved isLocked Dialog down into PrivateMessengerTheme

            val initialTheme = ThemePreference.load(this@MainActivity)
            var currentTheme by remember { mutableStateOf(initialTheme) }
            val isSystemDark = isSystemInDarkTheme()
            val useDark = when (currentTheme) {
                AppTheme.DARK   -> true
                AppTheme.LIGHT  -> false
                AppTheme.SYSTEM -> isSystemDark
            }
            PrivateMessengerTheme(darkTheme = useDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        startDestination = startDestination,
                        app = app,
                        currentTheme = currentTheme,
                        onThemeChanged = { currentTheme = it }
                    )

                    if (isLocked) {
                        Dialog(
                            onDismissRequest = { /* Prevent dismiss */ },
                            properties = DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                Box(contentAlignment = Alignment.Center) {
                                    var unlockPinInput by remember { mutableStateOf("") }
                                    var pinError by remember { mutableStateOf(false) }
                                    var biometricFailCount by rememberSaveable { mutableStateOf(0) }
                                    var biometricCooldownUntil by rememberSaveable { mutableStateOf(0L) }
                                    var tick by remember { mutableStateOf(0) }
                                    
                                    LaunchedEffect(biometricCooldownUntil, tick) {
                                        if (biometricCooldownUntil > System.currentTimeMillis()) {
                                            kotlinx.coroutines.delay(1000)
                                            tick++
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("App Locked", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                                        Spacer(modifier = Modifier.height(32.dp))

                                        if (appLockPin != null) {
                                            androidx.compose.material3.OutlinedTextField(
                                                value = unlockPinInput,
                                                onValueChange = { 
                                                    if (it.length <= 6 && it.all { char -> char.isDigit() }) unlockPinInput = it 
                                                    pinError = false
                                                },
                                                label = { Text("Enter 6-digit PIN") },
                                                isError = pinError,
                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    if (unlockPinInput == appLockPin) {
                                                        isLocked = false
                                                        backgroundTime = System.currentTimeMillis()
                                                        unlockPinInput = ""
                                                    } else {
                                                        pinError = true
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                            ) {
                                                Text("Unlock with PIN")
                                            }
                                        }

                                        if (isBiometricEnabled) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            val cooldownRemaining = biometricCooldownUntil - System.currentTimeMillis()
                                            
                                            if (cooldownRemaining > 0) {
                                                val minutes = (cooldownRemaining / 1000) / 60
                                                val seconds = (cooldownRemaining / 1000) % 60
                                                Text(
                                                    text = "Fingerprint disabled for %d:%02d".format(minutes, seconds),
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            } else {
                                                Button(onClick = {
                                                    val executor = ContextCompat.getMainExecutor(this@MainActivity)
                                                    var prompt: BiometricPrompt? = null
                                                    prompt = BiometricPrompt(this@MainActivity, executor,
                                                        object : BiometricPrompt.AuthenticationCallback() {
                                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                                super.onAuthenticationSucceeded(result)
                                                                isLocked = false
                                                                backgroundTime = System.currentTimeMillis()
                                                                biometricFailCount = 0
                                                            }
                                                            override fun onAuthenticationFailed() {
                                                                super.onAuthenticationFailed()
                                                                biometricFailCount++
                                                                if (biometricFailCount >= 3) {
                                                                    biometricCooldownUntil = System.currentTimeMillis() + 5 * 60 * 1000L
                                                                    biometricFailCount = 0
                                                                    prompt?.cancelAuthentication()
                                                                }
                                                            }
                                                        })
                                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                        .setTitle("Unlock CryptoSub")
                                                        .setSubtitle("Authenticate to access your private messages")
                                                        .setNegativeButtonText("Cancel")
                                                        .build()
                                                    prompt.authenticate(promptInfo)
                                                },
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                                ) {
                                                    Text("Unlock with Fingerprint", color = MaterialTheme.colorScheme.onSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
