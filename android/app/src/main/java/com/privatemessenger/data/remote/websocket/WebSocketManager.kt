package com.privatemessenger.data.remote.websocket

import android.util.Log
import com.google.gson.Gson
import com.privatemessenger.crypto.EnvelopeType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages the persistent WebSocket connection to the server for receiving
 * and sending encrypted envelopes.
 *
 * Incoming envelopes are pushed to [incomingEnvelopes] for the repository
 * layer to decrypt and process.
 */
class WebSocketManager(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val serverUrl: String,
) {
    private var webSocket: WebSocket? = null
    private var sessionToken: String? = null

    private val _incomingEnvelopes = MutableSharedFlow<Envelope>()
    val incomingEnvelopes: SharedFlow<Envelope> = _incomingEnvelopes

    fun connect(token: String) {
        sessionToken = token
        // Use wss:// for production, ws:// for local testing
        val wsUrl = serverUrl.replace("http", "ws") + "/v1/ws?token=$token"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketManager", "Connected to server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = gson.fromJson(text, Envelope::class.java)
                    _incomingEnvelopes.tryEmit(envelope)
                } catch (e: Exception) {
                    Log.e("WebSocketManager", "Failed to parse incoming envelope", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketManager", "Connection closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketManager", "Connection failure", t)
                // TODO: Implement exponential backoff reconnection strategy
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        sessionToken = null
    }

    /**
     * Sends an encrypted envelope to the server for routing.
     */
    fun sendEnvelope(envelope: Envelope) {
        val payload = gson.toJson(envelope)
        webSocket?.send(payload) ?: Log.w("WebSocketManager", "Cannot send, websocket not connected")
    }

    /**
     * Sends a delivery acknowledgment to the server so it can delete
     * the message from Cassandra.
     */
    fun sendAck(messageId: String) {
        val ack = Envelope(
            type = EnvelopeType.ACK.wire,
            message_id = messageId,
            recipient_user_id = "",   // Unused for ACK
            recipient_device_id = "", // Unused for ACK
            sealed_sender_ciphertext = ByteArray(0),
            message_ciphertext = ByteArray(0),
            server_timestamp = 0L,
        )
        sendEnvelope(ack)
    }
}

/**
 * Matches the Go server's relay.Envelope struct.
 */
data class Envelope(
    val message_id: String? = null,
    val recipient_user_id: String,
    val recipient_device_id: String,
    val sealed_sender_ciphertext: ByteArray,
    val message_ciphertext: ByteArray,
    val type: String,
    val server_timestamp: Long
)
