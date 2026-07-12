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
import com.privatemessenger.ui.screens.chat.ChatScreen
import com.privatemessenger.ui.screens.chatlist.ChatListScreen
import com.privatemessenger.ui.screens.registration.RegistrationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    startDestination: String,
    app: PrivateMessengerApp
) {
    val navController = rememberNavController()

    // Setup basic dependencies for the UI
    val authRepository = AuthRepository(app)

    // Listen for incoming messages on the decentralized network
    androidx.compose.runtime.LaunchedEffect(app.xmtpClient) {
        val client = app.xmtpClient ?: return@LaunchedEffect
        try {
            client.conversations.streamAllMessages().collect { message ->
                try {
                    // Save the contact if it's the first time we hear from them
                    val conversationExists = app.database.conversationDao().getConversation(message.senderInboxId) != null
                    if (!conversationExists) {
                        val contact = com.privatemessenger.data.local.entity.ConversationEntity(
                            id = message.senderInboxId,
                            deviceId = 1,
                            displayName = "Contact ${message.senderInboxId.take(6)}",
                            lastMessage = message.body,
                            lastMessageTimestamp = message.sentAt.time,
                            unreadCount = 1
                        )
                        app.database.conversationDao().upsert(contact)
                    }

                    // Save the incoming message
                    val msgEntity = com.privatemessenger.data.local.entity.MessageEntity(
                        id = message.id,
                        conversationId = message.senderInboxId,
                        senderUserId = message.senderInboxId,
                        content = message.body,
                        timestamp = message.sentAt.time,
                        status = com.privatemessenger.data.local.entity.MessageStatus.DELIVERED
                    )
                    app.database.messageDao().insert(msgEntity)
                    app.database.conversationDao().updateLastMessage(message.senderInboxId, message.body, message.sentAt.time)
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
                    navController.navigate("chat_list") {
                        popUpTo("registration") { inclusive = true }
                    }
                }
            )
        }

        composable("chat_list") {
            ChatListScreen(
                database = app.database,
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
                            
                            val publicIdentity = org.xmtp.android.library.libxmtp.PublicIdentity(
                                org.xmtp.android.library.libxmtp.IdentityKind.ETHEREUM,
                                address
                            )
                            val canMessage = client.canMessage(listOf(publicIdentity))[address] == true
                            
                            if (canMessage) {
                                // Save contact to local DB
                                val contact = com.privatemessenger.data.local.entity.ConversationEntity(
                                    id = address,
                                    deviceId = 1,
                                    displayName = "Contact ${address.take(6)}",
                                    lastMessage = "Connected via XMTP",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    unreadCount = 0
                                )
                                app.database.conversationDao().upsert(contact)

                                // Navigate to Chat
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    navController.navigate("chat/$address") {
                                        popUpTo("chat_list")
                                    }
                                }
                            } else {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(app, "Address is not registered on XMTP", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppNavGraph", "Failed to add XMTP contact", e)
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
