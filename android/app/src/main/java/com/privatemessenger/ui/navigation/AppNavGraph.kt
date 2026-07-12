package com.privatemessenger.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.privatemessenger.ui.screens.registration.RegistrationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity

@Composable
fun AppNavGraph(
    startDestination: String,
    app: PrivateMessengerApp
) {
    val navController = rememberNavController()
    val authRepository = AuthRepository(app)

    // Stream all incoming XMTP messages. Use message.conversationId as the canonical DB key.
    androidx.compose.runtime.LaunchedEffect(app.xmtpClient) {
        val client = app.xmtpClient ?: return@LaunchedEffect
        try {
            client.conversations.streamAllMessages().collect { message ->
                try {
                    // The XMTP conversation ID is the authoritative shared key — it is the
                    // same hex on both devices for the same DM thread.
                    val convId = message.conversationId

                    val conversationExists = app.database.conversationDao().getConversation(convId) != null
                    if (!conversationExists) {
                        val label = "${message.senderInboxId.take(6)}...${message.senderInboxId.takeLast(4)}"
                        val contact = com.privatemessenger.data.local.entity.ConversationEntity(
                            id = convId,
                            deviceId = 1,
                            displayName = label,
                            lastMessage = message.body,
                            lastMessageTimestamp = message.sentAt.time,
                            unreadCount = 1
                        )
                        app.database.conversationDao().upsert(contact)
                        // 🔔 Push notification: new contact
                        NotificationHelper.showNewContactNotification(app, label)
                    } else {
                        // 🔔 Push notification: new message in existing conversation
                        val conv = app.database.conversationDao().getConversation(convId)
                        val senderLabel = conv?.displayName ?: "${message.senderInboxId.take(6)}..."
                        NotificationHelper.showNewMessageNotification(app, senderLabel, message.body, convId)
                    }

                    val msgEntity = com.privatemessenger.data.local.entity.MessageEntity(
                        id = message.id,
                        conversationId = convId,
                        senderUserId = message.senderInboxId,
                        content = message.body,
                        timestamp = message.sentAt.time,
                        status = com.privatemessenger.data.local.entity.MessageStatus.DELIVERED
                    )
                    app.database.messageDao().insert(msgEntity)
                    app.database.conversationDao().updateLastMessage(convId, message.body, message.sentAt.time)
                } catch (e: Exception) {
                    android.util.Log.e("AppNavGraph", "Failed to process incoming XMTP message", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppNavGraph", "XMTP Stream disconnected", e)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        composable("registration") {
            RegistrationScreen(
                authRepository = authRepository,
                onRegistrationComplete = {
                    // After registering, go to key reveal before entering the app
                    navController.navigate("key_reveal") {
                        popUpTo("registration") { inclusive = true }
                    }
                }
            )
        }

        composable("key_reveal") {
            val publicAddress = app.xmtpClient?.publicIdentity?.identifier ?: ""
            val privateKeyHex = app.keyStoreManager.getEthereumPrivateKey() ?: ""
            com.privatemessenger.ui.screens.registration.KeyRevealScreen(
                publicAddress = publicAddress,
                privateKeyHex = privateKeyHex,
                onContinue = {
                    navController.navigate("chat_list") {
                        popUpTo("key_reveal") { inclusive = true }
                    }
                }
            )
        }

        composable("chat_list") {
            ChatListScreen(
                database = app.database,
                app = app,
                onChatClicked = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onAddContactClicked = {
                    navController.navigate("scanner")
                }
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

                            // 3. Sync so the other device's welcome message is received
                            client.conversations.sync()

                            // 4. Persist the contact using xmtpConvId as the local key
                            val existing = app.database.conversationDao().getConversation(xmtpConvId)
                            if (existing == null) {
                                val contact = com.privatemessenger.data.local.entity.ConversationEntity(
                                    id = xmtpConvId,
                                    deviceId = 1,
                                    displayName = "${address.take(6)}...${address.takeLast(4)}",
                                    lastMessage = "Connected via XMTP",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    unreadCount = 0
                                )
                                app.database.conversationDao().upsert(contact)
                            }

                            // 5. Navigate using the XMTP conversation ID, not the raw address
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                navController.navigate("chat/$xmtpConvId") {
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
