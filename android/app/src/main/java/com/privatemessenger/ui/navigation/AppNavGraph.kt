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
    onThemeChanged: (AppTheme) -> Unit
) {
    val navController = rememberNavController()
    val authRepository = AuthRepository(app)
    val coroutineScope = rememberCoroutineScope()


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
            
            // We need a remembered WebRTCClient
            val context = androidx.compose.ui.platform.LocalContext.current
            val webRTCClient = remember { 
                com.privatemessenger.webrtc.WebRTCClient(
                    context, 
                    object : org.webrtc.PeerConnection.Observer {
                        override fun onSignalingChange(p0: org.webrtc.PeerConnection.SignalingState?) {}
                        override fun onIceConnectionChange(p0: org.webrtc.PeerConnection.IceConnectionState?) {}
                        override fun onIceConnectionReceivingChange(p0: Boolean) {}
                        override fun onIceGatheringChange(p0: org.webrtc.PeerConnection.IceGatheringState?) {}
                        override fun onIceCandidate(candidate: org.webrtc.IceCandidate) {
                            // Send candidate over XMTP
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
                                // Send offer via XMTP
                            }
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    })
                }
            }

            val callerName = app.database.conversationDao().getConversation(conversationId)?.displayName
                ?: "${conversationId.take(6)}…${conversationId.takeLast(4)}"

            com.privatemessenger.ui.screens.call.VideoCallScreen(
                conversationId = conversationId,
                webRTCClient = webRTCClient,
                isIncoming = isIncoming,
                isVoiceOnly = isVoiceOnly,
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
                                val payload = "${callTypeLabel} ended${durationLabel}"
                                when (xmtpConv) {
                                    is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                    is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppNavGraph", "call end message failed", e)
                        }
                    }
                    webRTCClient.close()
                    navController.popBackStack()
                }
            )
        }
    }
}
