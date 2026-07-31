package com.privatemessenger.webrtc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class WebRTCSignal(
    val type: String,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val senderInboxId: String
)

object WebRTCSignalingManager {
    // Keep the most recent offer in memory so if the app is launched from a notification, it can grab it
    var pendingOffer: WebRTCSignal? = null
    var activeCallConversationId: String? = null

    private val _incomingSignals = MutableSharedFlow<WebRTCSignal>(replay = 50)
    val incomingSignals = _incomingSignals.asSharedFlow()

    suspend fun emitSignal(signal: WebRTCSignal) {
        if (signal.type == "webrtc_offer") {
            pendingOffer = signal
        }
        if (signal.type == "webrtc_call_end" || signal.type == "webrtc_call_reject") {
            pendingOffer = null
        }
        _incomingSignals.emit(signal)
    }
    
    fun clearPendingOffer() {
        pendingOffer = null
    }
}
