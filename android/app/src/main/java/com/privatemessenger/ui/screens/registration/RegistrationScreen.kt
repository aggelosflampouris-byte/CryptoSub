package com.privatemessenger.ui.screens.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privatemessenger.domain.repository.AuthRepository
import com.privatemessenger.ui.components.AnimatedBackground
import com.privatemessenger.ui.components.BounceButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    authRepository: AuthRepository,
    onRegistrationComplete: () -> Unit,
    onRestoreComplete: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importKeyText by remember { mutableStateOf("") }
    var generatedKey by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    if (generatedKey != null) {
        AnimatedBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Crucial Security Step",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This is your Ethereum Private Key. It is the ONLY way to access your account and messages if you lose this device.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedKey!!,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Private Key", generatedKey))
                                android.widget.Toast.makeText(context, "Key copied!", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Memorize it or store it securely offline. DO NOT share it with anyone.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    BounceButton(
                        onClick = { onRegistrationComplete() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("I have safely stored it", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
        return
    }

    // ── Import Key Dialog ───────────────────────────────────────────
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showImportDialog = false },
            title = { Text("Import Private Key", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Paste the private key you saved during your first registration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = importKeyText,
                        onValueChange = { importKeyText = it },
                        label = { Text("Private Key (hex)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                BounceButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = authRepository.restore(importKeyText)
                            isLoading = false
                            if (result.isSuccess) {
                                showImportDialog = false
                                onRestoreComplete()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message
                                    ?: "Failed to restore identity."
                            }
                        }
                    },
                    enabled = !isLoading && importKeyText.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                    } else {
                        Text("Restore", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false; errorMessage = null },
                    enabled = !isLoading
                ) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Main Screen ─────────────────────────────────────────────────
    AnimatedBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 32.dp, vertical = 48.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CryptoSub",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A zero-knowledge identity system. No phone number or email required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                if (errorMessage != null && !showImportDialog) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Generate New Identity ───────────────────────────────
                BounceButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = authRepository.register()
                            isLoading = false
                            if (result.isSuccess) {
                                generatedKey = result.getOrNull()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message
                                    ?: "Failed to generate identity."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isLoading
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading && !showImportDialog) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
                            Spacer(Modifier.width(12.dp))
                            Text("Generate Identity", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Import Existing Key ─────────────────────────────────
                BounceButton(
                    onClick = {
                        errorMessage = null
                        importKeyText = ""
                        showImportDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    enabled = !isLoading
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Import Key", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
