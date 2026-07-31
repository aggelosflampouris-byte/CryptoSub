package com.privatemessenger.ui.screens.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.privatemessenger.webrtc.WebRTCClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

enum class CallStatus { WAITING, CONNECTED }

@Composable
fun VideoCallScreen(
    conversationId: String,
    webRTCClient: WebRTCClient,
    isIncoming: Boolean,
    isVoiceOnly: Boolean = false,
    isConnected: Boolean = false,
    callerName: String = "Unknown",
    onEndCall: (durationSeconds: Int) -> Unit,
    onOfferCreated: (String) -> Unit = {},
    onAnswerCreated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isAudioMuted by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(isVoiceOnly) }
    var callStatus by remember { mutableStateOf(CallStatus.WAITING) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }

    val localRenderer = remember { SurfaceViewRenderer(context) }
    val remoteRenderer = remember { SurfaceViewRenderer(context) }
    
    LaunchedEffect(isConnected) {
        if (isConnected) {
            callStatus = CallStatus.CONNECTED
        }
    }

    // Timer
    LaunchedEffect(callStatus) {
        if (callStatus == CallStatus.CONNECTED) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Pulse animation for waiting state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    DisposableEffect(Unit) {
        if (!isVoiceOnly) {
            localRenderer.init(webRTCClient.eglBaseContext, null)
            remoteRenderer.init(webRTCClient.eglBaseContext, null)
            localRenderer.setMirror(true)
            webRTCClient.startLocalVideo(localRenderer)
        }

        onDispose {
            webRTCClient.close()
            if (!isVoiceOnly) {
                localRenderer.release()
                remoteRenderer.release()
            }
        }
    }

    fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1923), Color(0xFF16213E), Color(0xFF0D3460))
                )
            )
    ) {
        if (!isVoiceOnly) {
            // Remote video — full screen
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Waiting overlay — shown until connected
        if (callStatus == CallStatus.WAITING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0F1923)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pulsing avatar circle
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(100.dp)
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = callerName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(callerName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

                    Text(
                        text = if (isIncoming) "Incoming ${if (isVoiceOnly) "voice" else "video"} call…"
                               else "Ringing…",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Local video PIP (video calls only, hidden while waiting if remote hasn't connected)
        if (!isVoiceOnly && callStatus == CallStatus.CONNECTED) {
            AndroidView(
                factory = { localRenderer },
                modifier = Modifier
                    .size(120.dp, 160.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            )
        }

        // Timer badge — shown when connected
        if (callStatus == CallStatus.CONNECTED) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = formatTime(elapsedSeconds),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Voice-only: show centered avatar when connected too
        if (isVoiceOnly && callStatus == CallStatus.CONNECTED) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(callerName.firstOrNull()?.uppercase() ?: "?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(callerName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Controls row at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute audio
            IconButton(
                onClick = {
                    isAudioMuted = !isAudioMuted
                    webRTCClient.toggleAudio(!isAudioMuted)
                },
                modifier = Modifier
                    .background(
                        if (isAudioMuted) Color.Red.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Toggle Audio",
                    tint = if (isAudioMuted) Color.Red else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // End call
            IconButton(
                onClick = { onEndCall(elapsedSeconds) },
                modifier = Modifier
                    .background(Color.Red, CircleShape)
                    .size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Toggle video (only relevant for video calls)
            if (!isVoiceOnly) {
                IconButton(
                    onClick = {
                        isVideoMuted = !isVideoMuted
                        webRTCClient.toggleVideo(!isVideoMuted)
                    },
                    modifier = Modifier
                        .background(
                            if (isVideoMuted) Color.Red.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Toggle Video",
                        tint = if (isVideoMuted) Color.Red else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}
