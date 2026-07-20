package com.privatemessenger.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privatemessenger.data.AppDatabase
import com.privatemessenger.data.entity.ConversationEntity
import com.privatemessenger.domain.repository.AuthRepository
import com.privatemessenger.platform.KeyVault
import com.privatemessenger.platform.XmtpClientHandle
import com.privatemessenger.platform.XmtpService
import com.privatemessenger.ui.screens.account.AccountScreen
import com.privatemessenger.ui.screens.chat.ChatScreen
import com.privatemessenger.ui.screens.chatlist.ChatListScreen
import com.privatemessenger.ui.screens.chatlist.CreateGroupScreen
import com.privatemessenger.ui.screens.registration.RegistrationScreen
import com.privatemessenger.ui.screens.scanner.ScannerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    database: AppDatabase,
    keyVault: KeyVault,
    xmtpService: XmtpService,
    xmtpClient: XmtpClientHandle?,
    onXmtpClientReady: (XmtpClientHandle) -> Unit,
    startDestination: String,
) {
    val navController = rememberNavController()
    val authRepository = remember { AuthRepository(keyVault, xmtpService) }
    val coroutineScope = rememberCoroutineScope()

    // Mutable state that updates the current XMTP client across the nav graph
    var currentClient by remember { mutableStateOf(xmtpClient) }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
        },
    ) {
        // ── Registration ───────────────────────────────────────────────────
        composable("registration") {
            RegistrationScreen(
                authRepository = authRepository,
                onRegistrationComplete = { client ->
                    currentClient = client
                    onXmtpClientReady(client)
                    navController.navigate("chat_list") {
                        popUpTo("registration") { inclusive = true }
                    }
                },
                onRestoreComplete = { client ->
                    currentClient = client
                    onXmtpClientReady(client)
                    navController.navigate("chat_list") {
                        popUpTo("registration") { inclusive = true }
                    }
                },
            )
        }

        // ── Chat List ──────────────────────────────────────────────────────
        composable("chat_list") {
            ChatListScreen(
                database = database,
                xmtpClient = currentClient,
                onChatClicked = { navController.navigate("chat/$it") },
                onAddContactClicked = { navController.navigate("scanner") },
                onAddGroupClicked = { navController.navigate("create_group") },
                onAccountClicked = { navController.navigate("account") },
            )
        }

        // ── Account ────────────────────────────────────────────────────────
        composable("account") {
            AccountScreen(
                xmtpClient = currentClient,
                onBack = { navController.popBackStack() },
            )
        }

        // ── Create Group ───────────────────────────────────────────────────
        composable("create_group") {
            CreateGroupScreen(
                database = database,
                xmtpClient = currentClient,
                xmtpService = xmtpService,
                onGroupCreated = { groupId ->
                    navController.navigate("chat/$groupId") { popUpTo("chat_list") }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── QR Scanner ─────────────────────────────────────────────────────
        composable("scanner") {
            ScannerScreen(
                xmtpClient = currentClient,
                xmtpService = xmtpService,
                database = database,
                onContactAdded = { convId ->
                    navController.navigate("chat/$convId") { popUpTo("chat_list") }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Chat ───────────────────────────────────────────────────────────
        composable(
            route = "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")
                ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                database = database,
                xmtpClient = currentClient,
                xmtpService = xmtpService,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
