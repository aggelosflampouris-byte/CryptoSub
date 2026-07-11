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

@Composable
fun AppNavGraph(
    startDestination: String,
    app: PrivateMessengerApp
) {
    val navController = rememberNavController()

    // Setup basic dependencies for the UI
    // In a real app with DI (e.g., Hilt), these would be injected into ViewModels
    val apiClient = ApiClient(app, "http://10.0.2.2:8080") // Android emulator localhost
    val authRepository = AuthRepository(apiClient, app)

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
                }
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
