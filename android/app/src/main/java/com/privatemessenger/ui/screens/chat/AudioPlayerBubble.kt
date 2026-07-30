package com.privatemessenger.ui.screens.chat

import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Renders an inline audio player for voice memo messages.
 * Accepts a local file path and handles MediaPlayer lifecycle safely.
 */
@Composable
fun AudioPlayerBubble(
    audioPath: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }   // ms
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(audioPath) {
        try {
            mediaPlayer.setDataSource(audioPath)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerBubble", "Failed to load audio: ${e.message}")
        }
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            progress = 0f
            it.seekTo(0)
        }
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    // Progress ticker
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive && isPlaying) {
                val dur = mediaPlayer.duration
                if (dur > 0) {
                    progress = mediaPlayer.currentPosition.toFloat() / dur
                }
                delay(100)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.widthIn(min = 180.dp, max = 260.dp)
    ) {
        // Play / Pause button
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    mediaPlayer.start()
                    isPlaying = true
                }
            },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(textColor.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Scrubber bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = textColor.copy(alpha = 0.8f),
                trackColor = textColor.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(3.dp))
            // Duration label
            val displayMs = if (isPlaying && duration > 0) (progress * duration).toInt() else duration
            Text(
                text = formatMs(displayMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = textColor.copy(alpha = 0.65f)
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
