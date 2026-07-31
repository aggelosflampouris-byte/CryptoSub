package com.privatemessenger.ui.screens.call

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.privatemessenger.webrtc.WebRTCClient
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.PeerConnection
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.RtpReceiver
import kotlinx.coroutines.launch

@Composable
fun VideoCallScreen(
    conversationId: String,
    webRTCClient: WebRTCClient,
    isIncoming: Boolean,
    onEndCall: () -> Unit,
    onOfferCreated: (String) -> Unit = {},
    onAnswerCreated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isAudioMuted by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(false) }
    
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    
    val localRenderer = remember { SurfaceViewRenderer(context) }
    val remoteRenderer = remember { SurfaceViewRenderer(context) }

    DisposableEffect(Unit) {
        localRenderer.init(webRTCClient.eglBaseContext, null)
        remoteRenderer.init(webRTCClient.eglBaseContext, null)
        localRenderer.setMirror(true)
        
        webRTCClient.startLocalVideo(localRenderer)

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                // Send candidate over XMTP (Implementation later)
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream) {
                if (stream.videoTracks.isNotEmpty()) {
                    val track = stream.videoTracks[0]
                    coroutineScope.launch {
                        remoteVideoTrack = track
                        track.addSink(remoteRenderer)
                    }
                }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }
        
        // We will initialize the peer connection and handle the offer/answer signaling outside this UI,
        // typically driven by the ViewModel or Navigation arguments.
        
        onDispose {
            webRTCClient.close()
            localRenderer.release()
            remoteRenderer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Remote Video (Full Screen)
        AndroidView(
            factory = { remoteRenderer },
            modifier = Modifier.fillMaxSize()
        )
        
        // Local Video (PIP)
        AndroidView(
            factory = { localRenderer },
            modifier = Modifier
                .size(120.dp, 160.dp)
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        
        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    isAudioMuted = !isAudioMuted
                    webRTCClient.toggleAudio(!isAudioMuted)
                },
                modifier = Modifier
                    .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Toggle Audio",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = {
                    onEndCall()
                },
                modifier = Modifier
                    .background(Color.Red, CircleShape)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = {
                    isVideoMuted = !isVideoMuted
                    webRTCClient.toggleVideo(!isVideoMuted)
                },
                modifier = Modifier
                    .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    contentDescription = "Toggle Video",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
