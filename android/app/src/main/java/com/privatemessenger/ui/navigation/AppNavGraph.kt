package com.privatemessenger.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.domain.repository.AuthRepository
import com.privatemessenger.notifications.NotificationHelper
import com.privatemessenger.ui.screens.chat.ChatScreen
import com.privatemessenger.ui.screens.chatlist.ChatListScreen
import com.privatemessenger.ui.screens.chatlist.CreateGroupScreen
import com.privatemessenger.ui.screens.registration.RegistrationScreen
import com.privatemessenger.ui.screens.settings.AppTheme
import com.privatemessenger.ui.screens.settings.SettingsScreen
import com.privatemessenger.ui.screens.share.ShareAppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity

@Composable
fun AppNavGraph(
    startDestination: String,
    app: PrivateMessengerApp,
    currentTheme: AppTheme,
    onThemeChanged: (AppTheme) -> Unit,
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    val authRepository = AuthRepository(app)
    val coroutineScope = rememberCoroutineScope()

    // ── Global incoming call state (visible from any screen) ─────────────────
    val pendingCallOffer = remember { androidx.compose.runtime.mutableStateOf<com.privatemessenger.webrtc.WebRTCSignal?>(null) }

    LaunchedEffect(Unit) {
        com.privatemessenger.webrtc.WebRTCSignalingManager.incomingSignals.collect { signal ->
            if (signal.type == "webrtc_offer" && signal.senderInboxId != app.xmtpClient?.inboxId) {
                pendingCallOffer.value = signal
            } else if (signal.type == "webrtc_call_end" || signal.type == "webrtc_call_reject") {
                // Caller hung up before we answered
                if (pendingCallOffer.value?.senderInboxId == signal.senderInboxId) {
                    pendingCallOffer.value = null
                }
            }
        }
    }

    // Show incoming call dialog from any screen
    pendingCallOffer.value?.let { offer ->
        val convId = com.privatemessenger.webrtc.WebRTCSignalingManager.activeCallConversationId ?: ""
        val isVoiceOnly = false // default; the offer may carry this flag
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text(if (isVoiceOnly) "Incoming Voice Call" else "Incoming Call") },
            text = { androidx.compose.material3.Text("Someone is calling you.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.privatemessenger.ui.MainActivity.pendingOfferSdp = offer.sdp ?: ""
                    val callConvId = com.privatemessenger.webrtc.WebRTCSignalingManager.activeCallConversationId ?: ""
                    pendingCallOffer.value = null
                    navController.navigate("video_call/${android.net.Uri.encode(callConvId)}?isIncoming=true&isVoiceOnly=false")
                }) { androidx.compose.material3.Text("Answer", color = androidx.compose.ui.graphics.Color(0xFF22C55E)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingCallOffer.value = null
                    com.privatemessenger.webrtc.WebRTCSignalingManager.clearPendingOffer()
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val client = app.xmtpClient ?: return@launch
                            val callConvId = com.privatemessenger.webrtc.WebRTCSignalingManager.activeCallConversationId ?: return@launch
                            val xmtpConv = client.conversations.findConversation(callConvId) ?: return@launch
                            val payload = com.google.gson.Gson().toJson(mapOf("type" to "webrtc_call_reject"))
                            when (xmtpConv) {
                                is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                            }
                        } catch (e: Exception) { android.util.Log.e("AppNavGraph", "reject failed", e) }
                    }
                }) { androidx.compose.material3.Text("Decline", color = androidx.compose.ui.graphics.Color(0xFFEF4444)) }
            }
        )
    }


    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            )
        }
    ) {
        composable("registration") {
            RegistrationScreen(
                authRepository = authRepository,
                onRegistrationComplete = {
                    navController.navigate("chat_list") {
                        popUpTo("registration") { inclusive = true }
                    }
                },
                onRestoreComplete = {
                    // Imported existing key → user already has their keys, skip reveal
                    navController.navigate("chat_list") {
                        popUpTo("registration") { inclusive = true }
                    }
                }
            )
        }



        composable("chat_list") {
            ChatListScreen(
                database = app.database,
                app = app,
                onChatClicked = { conversationId ->
                    navController.navigate("chat/${android.net.Uri.encode(conversationId)}")
                },
                onAddContactClicked = {
                    navController.navigate("scanner")
                },
                onAddGroupClicked = {
                    navController.navigate("create_group")
                },
                onAccountClicked = {
                    navController.navigate("account")
                },
                onShareAppClicked = {
                    navController.navigate("share_app")
                },
                onContactsClicked = {
                    navController.navigate("contacts")
                },
                onGroupsClicked = {
                    navController.navigate("groups")
                },
                onSettingsClicked = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                currentTheme = currentTheme,
                database = app.database,
                onThemeChanged = onThemeChanged,
                onBack = { navController.popBackStack() }
            )
        }

        composable("share_app") {
            ShareAppScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("account") {
            com.privatemessenger.ui.screens.account.AccountScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onLogoutComplete = {
                    navController.navigate("registration") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("contacts") {
            com.privatemessenger.ui.screens.contacts.ContactsScreen(
                database = app.database,
                onChatClicked = { conversationId ->
                    navController.navigate("contact_details/$conversationId")
                },
                onAddContactClicked = {
                    navController.navigate("scanner")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "contact_details/{conversationId}?editName={editName}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("editName") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val editName = backStackEntry.arguments?.getBoolean("editName") ?: false
            com.privatemessenger.ui.screens.contacts.ContactDetailsScreen(
                conversationId = conversationId,
                initialEditMode = editName,
                database = app.database,
                onBack = { navController.popBackStack() },
                onNavigateToChat = { convId ->
                    navController.navigate("chat/${android.net.Uri.encode(convId)}") {
                        popUpTo("chat_list")
                    }
                }
            )
        }

        composable("groups") {
            com.privatemessenger.ui.screens.groups.GroupsScreen(
                database = app.database,
                onChatClicked = { conversationId ->
                    navController.navigate("chat/${android.net.Uri.encode(conversationId)}") {
                        popUpTo("chat_list")
                    }
                },
                onCreateGroupClicked = {
                    navController.navigate("create_group")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "group_details/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            com.privatemessenger.ui.screens.groups.GroupDetailsScreen(
                conversationId = conversationId,
                app = app,
                onBack = { navController.popBackStack() }
            )
        }

        composable("create_group") {
            CreateGroupScreen(
                app = app,
                onGroupCreated = { groupId ->
                    navController.navigate("chat/${android.net.Uri.encode(groupId)}") {
                        popUpTo("chat_list")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("scanner") {
            val coroutineScope = rememberCoroutineScope()
            com.privatemessenger.ui.screens.scanner.ScannerScreen(
                app = app,
                onContactScanned = { address ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val client = app.xmtpClient ?: return@launch

                            // 1. Resolve Ethereum address -> XMTP inboxId
                            val peerIdentity = PublicIdentity(IdentityKind.ETHEREUM, address)
                            val canMessageMap = client.canMessage(listOf(peerIdentity))
                            val canMessage = canMessageMap[address.lowercase()] == true

                            if (!canMessage) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(app, "Address is not registered on XMTP", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            // 2. Create (or find) the deterministic XMTP DM thread.
                            //    findOrCreateDmWithIdentity returns the same Dm.id on BOTH devices.
                            val dm = client.conversations.findOrCreateDmWithIdentity(peerIdentity)
                            val xmtpConvId = dm.id   // This is the canonical shared ID

                            // 3. Persist the contact using xmtpConvId as the local key FIRST
                            // to prevent race conditions with the background sync service
                            val existing = app.database.conversationDao().getConversation(xmtpConvId)
                            if (existing == null) {
                                val contact = com.privatemessenger.data.local.entity.ConversationEntity(
                                    id = xmtpConvId,
                                    recipientUserId = address,
                                    deviceId = 1,
                                    displayName = "${address.take(6)}...${address.takeLast(4)}",
                                    lastMessage = "Connected via XMTP",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    unreadCount = 0
                                )
                                app.database.conversationDao().upsert(contact)
                            } else {
                                // If the contact already exists, bump its timestamp so it appears at the top of the list
                                app.database.conversationDao().updateLastMessage(xmtpConvId, existing.lastMessage ?: "Scanned", System.currentTimeMillis())
                            }

                            // 4. Sync so the other device's welcome message is received
                            client.conversations.sync()

                            // 5. Navigate using the XMTP conversation ID, not the raw address, and trigger edit mode
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                navController.navigate("contact_details/${android.net.Uri.encode(xmtpConvId)}?editName=true") {
                                    popUpTo("chat_list")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppNavGraph", "Failed to add XMTP contact", e)
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(app, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                database = app.database,
                app = app,
                onBack = { navController.popBackStack() },
                onHeaderClicked = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val isGroup = app.database.conversationDao().getConversation(conversationId)?.isGroup == true
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            if (isGroup) {
                                navController.navigate("group_details/$conversationId")
                            } else {
                                navController.navigate("contact_details/$conversationId")
                            }
                        }
                    }
                },
                onCallClicked = { isVoiceOnly ->
                    navController.navigate("video_call/${android.net.Uri.encode(conversationId)}?isIncoming=false&isVoiceOnly=${isVoiceOnly}")
                }
            )
        }

        composable(
            route = "video_call/{conversationId}?isIncoming={isIncoming}&isVoiceOnly={isVoiceOnly}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false },
                navArgument("isVoiceOnly") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            val isVoiceOnly = backStackEntry.arguments?.getBoolean("isVoiceOnly") ?: false
            val context = androidx.compose.ui.platform.LocalContext.current
            val isConnectedState = remember { androidx.compose.runtime.mutableStateOf(false) }
            val webRTCClient = remember { 
                com.privatemessenger.webrtc.WebRTCClient(
                    context, 
                    object : org.webrtc.PeerConnection.Observer {
                        override fun onSignalingChange(p0: org.webrtc.PeerConnection.SignalingState?) {}
                        override fun onIceConnectionChange(state: org.webrtc.PeerConnection.IceConnectionState?) {
                            if (state == org.webrtc.PeerConnection.IceConnectionState.CONNECTED) {
                                isConnectedState.value = true
                            }
                        }
                        override fun onIceConnectionReceivingChange(p0: Boolean) {}
                        override fun onIceGatheringChange(p0: org.webrtc.PeerConnection.IceGatheringState?) {}
                        override fun onIceCandidate(candidate: org.webrtc.IceCandidate) {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val client = app.xmtpClient ?: return@launch
                                    val xmtpConv = client.conversations.findConversation(conversationId) ?: return@launch
                                    val payload = com.google.gson.Gson().toJson(mapOf(
                                        "type" to "webrtc_ice_candidate",
                                        "candidate" to candidate.sdp,
                                        "sdpMid" to candidate.sdpMid,
                                        "sdpMLineIndex" to candidate.sdpMLineIndex
                                    ))
                                    when (xmtpConv) {
                                        is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                        is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("AppNavGraph", "Failed to send ICE candidate", e)
                                }
                            }
                        }
                        override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
                        override fun onAddStream(p0: org.webrtc.MediaStream) {}
                        override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
                        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
                        override fun onRenegotiationNeeded() {}
                        override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
                    }
                )
            }

            LaunchedEffect(Unit) {
                webRTCClient.initPeerConnection()
                if (!isIncoming) {
                    webRTCClient.call(object : org.webrtc.SdpObserver {
                        override fun onCreateSuccess(desc: org.webrtc.SessionDescription?) {
                            desc?.let { 
                                webRTCClient.setLocalDescription(it)
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val client = app.xmtpClient ?: return@launch
                                        val xmtpConv = client.conversations.findConversation(conversationId) ?: return@launch
                                        val payload = com.google.gson.Gson().toJson(mapOf(
                                            "type" to "webrtc_offer",
                                            "sdp" to it.description,
                                            "isVoiceOnly" to isVoiceOnly
                                        ))
                                        when (xmtpConv) {
                                            is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                            is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("AppNavGraph", "Failed to send webrtc_offer", e)
                                    }
                                }
                            }
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    })
                } else {
                    val offerSdp = com.privatemessenger.ui.MainActivity.pendingOfferSdp
                    com.privatemessenger.ui.MainActivity.pendingOfferSdp = ""

                    if (offerSdp.isNotEmpty()) {
                        val remoteDesc = org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.OFFER, offerSdp)
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val success = webRTCClient.setRemoteDescriptionAwait(remoteDesc)
                            if (success) {
                                val desc = webRTCClient.answerAwait()
                                desc?.let {
                                    webRTCClient.setLocalDescription(it)
                                    try {
                                        val client = app.xmtpClient ?: return@launch
                                        val xmtpConv = client.conversations.findConversation(conversationId) ?: return@launch
                                        val payload = com.google.gson.Gson().toJson(mapOf(
                                            "type" to "webrtc_answer",
                                            "sdp" to it.description
                                        ))
                                        when (xmtpConv) {
                                            is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                            is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                        }
                                        val nm = context.getSystemService(android.app.NotificationManager::class.java)
                                        nm.cancel("call_${conversationId}".hashCode())
                                    } catch (e: Exception) {
                                        android.util.Log.e("AppNavGraph", "Failed to send webrtc_answer", e)
                                    }
                                }
                            }
                        }
                    }
                }

                coroutineScope.launch {
                    com.privatemessenger.webrtc.WebRTCSignalingManager.incomingSignals.collect { signal: com.privatemessenger.webrtc.WebRTCSignal ->
                        if (signal.senderInboxId == app.xmtpClient?.inboxId) return@collect
                        when (signal.type) {
                            "webrtc_answer" -> {
                                val answerDesc = org.webrtc.SessionDescription(
                                    org.webrtc.SessionDescription.Type.ANSWER, signal.sdp
                                )
                                val success = webRTCClient.setRemoteDescriptionAwait(answerDesc)
                                if (success) {
                                    isConnectedState.value = true
                                }
                            }
                            "webrtc_ice_candidate" -> {
                                if (signal.candidate != null) {
                                    val ice = org.webrtc.IceCandidate(signal.sdpMid, signal.sdpMLineIndex ?: 0, signal.candidate)
                                    webRTCClient.addIceCandidate(ice)
                                }
                            }
                            "webrtc_call_reject", "webrtc_call_end" -> {
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }

            val callerNameState = remember { androidx.compose.runtime.mutableStateOf("${conversationId.take(6)}…${conversationId.takeLast(4)}") }
            LaunchedEffect(conversationId) {
                app.database.conversationDao().getConversation(conversationId)?.displayName?.let {
                    callerNameState.value = it
                }
            }
            val callerName = callerNameState.value

            com.privatemessenger.ui.screens.call.VideoCallScreen(
                conversationId = conversationId,
                webRTCClient = webRTCClient,
                isIncoming = isIncoming,
                isVoiceOnly = isVoiceOnly,
                isConnected = isConnectedState.value,
                callerName = callerName,
                onEndCall = { durationSeconds ->
                    // Send call-ended chat message
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val client = app.xmtpClient
                            val xmtpConv = client?.conversations?.findConversation(conversationId)
                            if (client != null && xmtpConv != null) {
                                val callTypeLabel = if (isVoiceOnly) "📞 Voice call" else "📹 Video call"
                                val durationLabel = if (durationSeconds > 0) {
                                    val m = durationSeconds / 60
                                    val s = durationSeconds % 60
                                    " • %d:%02d".format(m, s)
                                } else ""
                                val payload = "SYSTEM_UI:call_ended:${callTypeLabel} ended${durationLabel}"
                                when (xmtpConv) {
                                    is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                    is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppNavGraph", "call end message failed", e)
                        }
                    }
                    navController.popBackStack()
                }
            )
        }
    }
}
