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
import com.privatemessenger.data.remote.ApiClient
import com.privatemessenger.domain.repository.AuthRepository
import com.privatemessenger.ui.screens.chat.ChatScreen
import com.privatemessenger.ui.screens.chatlist.ChatListScreen
import com.privatemessenger.ui.screens.registration.RegistrationScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    startDestination: String,
    app: PrivateMessengerApp
) {
    val navController = rememberNavController()

    // Setup basic dependencies for the UI
    // In a real app with DI (e.g., Hilt), these would be injected into ViewModels
    val apiClient = ApiClient(app, "https://contemporary-tons-hrs-annie.trycloudflare.com/") // Cloudflare Tunnel for live testing IP
    val authRepository = AuthRepository(apiClient, app)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        apiClient.webSocketManager.incomingEnvelopes.collect { envelope ->
            try {
                // 1. Trial Decrypt Sealed Sender
                val contacts = app.database.conversationDao().getAllConversationsSync()
                val candidateKeys = contacts.associate { it.id to (it.profileKey ?: ByteArray(32)) }
                val sealedSenderCrypto = com.privatemessenger.crypto.SealedSenderCrypto()
                
                val decryptedSender = sealedSenderCrypto.trialDecrypt(envelope.sealed_sender_ciphertext, candidateKeys)
                if (decryptedSender != null) {
                    val (senderIdentity, contactId) = decryptedSender
                    
                    val protocolStore = app.protocolStore ?: return@collect
                    val ratchetEngine = com.privatemessenger.crypto.RatchetEngine(protocolStore)
                    val type = com.privatemessenger.crypto.EnvelopeType.fromWire(envelope.type)
                    val decryptedPayload = ratchetEngine.decrypt(
                        senderUserId = senderIdentity.userId,
                        senderDeviceId = senderIdentity.deviceId,
                        ciphertext = envelope.message_ciphertext,
                        type = type
                    )
                    
                    // 3. Save to DB
                    val msgEntity = com.privatemessenger.data.local.entity.MessageEntity(
                        id = envelope.message_id ?: java.util.UUID.randomUUID().toString(),
                        conversationId = contactId,
                        senderUserId = senderIdentity.userId,
                        content = decryptedPayload.text,
                        timestamp = envelope.server_timestamp,
                        status = com.privatemessenger.data.local.entity.MessageStatus.DELIVERED
                    )
                    app.database.messageDao().insert(msgEntity)
                    app.database.conversationDao().updateLastMessage(contactId, decryptedPayload.text, envelope.server_timestamp)
                    
                    // 4. Send ACK
                    if (envelope.message_id != null) {
                        apiClient.webSocketManager.sendAck(envelope.message_id)
                    }
                } else {
                    android.util.Log.w("AppNavGraph", "Incoming message failed Sealed Sender trial decryption")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppNavGraph", "Failed to process incoming envelope", e)
            }
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
                apiClient = apiClient,
                onContactScanned = { userId, deviceId, profileKey ->
                    // 1. Check if session already exists
                    val protocolStore = app.protocolStore ?: return@ScannerScreen
                    val sessionBuilder = com.privatemessenger.crypto.SignalSessionBuilder(protocolStore)
                    if (sessionBuilder.hasSession(userId, deviceId)) {
                        navController.navigate("chat/$userId") {
                            popUpTo("chat_list")
                        }
                        return@ScannerScreen
                    }

                    // 2. Fetch PreKey bundle and build session
                    coroutineScope.launch {
                        try {
                            val response = apiClient.api.fetchPreKeyBundle(userId, deviceId.toString())
                            val signedPreKeyRecord = org.whispersystems.libsignal.state.SignedPreKeyRecord(response.signed_pre_key)
                            val bundle = sessionBuilder.bundleFromServerResponse(
                                registrationId = response.registration_id,
                                deviceId = deviceId,
                                signedPreKeyId = signedPreKeyRecord.id,
                                signedPreKeyPublic = signedPreKeyRecord.keyPair.publicKey.serialize(),
                                signedPreKeySignature = signedPreKeyRecord.signature,
                                identityKeyPublic = response.identity_public_key,
                                oneTimePreKeyId = response.one_time_pre_key_id,
                                oneTimePreKeyPublic = response.one_time_pre_key
                            )
                            sessionBuilder.buildSession(userId, deviceId, bundle)

                            // 3. Save contact to local DB
                            val contact = com.privatemessenger.data.local.entity.ConversationEntity(
                                id = userId,
                                deviceId = deviceId,
                                displayName = "Contact ${userId.take(4)}",
                                profileKey = android.util.Base64.decode(profileKey, android.util.Base64.NO_WRAP),
                                lastMessage = "Session established",
                                lastMessageTimestamp = System.currentTimeMillis(),
                                unreadCount = 0
                            )
                            app.database.conversationDao().upsert(contact)

                            // 4. Navigate to Chat
                            navController.navigate("chat/$userId") {
                                popUpTo("chat_list")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppNavGraph", "Failed to add contact", e)
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
                apiClient = apiClient,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
