package com.privatemessenger.ui.screens.registration

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown exactly once after registration. Displays the user's Ethereum
 * public address and lets them reveal + copy their private key.
 *
 * The private key is only revealed on explicit tap so it can't be
 * accidentally exposed in screenshots or screen-recording.
 */
@Composable
fun KeyRevealScreen(
    publicAddress: String,
    privateKeyHex: String,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var privateKeyVisible by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Your Crypto Identity",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Save these keys somewhere safe. They cannot be recovered if lost.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // ── Public Address ──────────────────────────────────────
            KeyCard(
                label = "Public Address (Share with contacts)",
                value = publicAddress,
                isSecret = false,
                onCopy = { copyToClipboard("Public Address", publicAddress) }
            )

            Spacer(Modifier.height(16.dp))

            // ── Private Key ─────────────────────────────────────────
            KeyCard(
                label = "Private Key (NEVER share this!)",
                value = if (privateKeyVisible) "0x$privateKeyHex" else "••••••••••••••••••••••••••••••••",
                isSecret = true,
                isRevealed = privateKeyVisible,
                onToggleVisibility = { privateKeyVisible = !privateKeyVisible },
                onCopy = {
                    if (privateKeyVisible) copyToClipboard("Private Key", "0x$privateKeyHex")
                    else Toast.makeText(context, "Reveal the key first", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(Modifier.height(24.dp))

            // ── Confirm checkbox ────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Spacer(Modifier.width(8.dp))
                Text(
                    "I have saved my keys in a secure place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                enabled = confirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enter Messenger", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KeyCard(
    label: String,
    value: String,
    isSecret: Boolean,
    isRevealed: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isSecret && !isRevealed)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSecret && onToggleVisibility != null) {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
