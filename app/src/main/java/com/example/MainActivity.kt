package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NameInputScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.SonetAITheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ChatViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val factory = ChatViewModelFactory(applicationContext)
            val viewModel: ChatViewModel = viewModel(factory = factory)

            SonetAITheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SonetAppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun SonetAppNavigation(viewModel: ChatViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable(
            route = "splash",
            exitTransition = {
                fadeOut(animationSpec = tween(400))
            }
        ) {
            SplashScreen(
                onAnimationFinished = {
                    val isLoggedIn = viewModel.isLoggedIn.value
                    val hasNameSetup = viewModel.hasNameSetup.value
                    val targetRoute = when {
                        !isLoggedIn -> "login"
                        !hasNameSetup -> "name_input"
                        else -> "chat"
                    }
                    navController.navigate(targetRoute) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "login",
            enterTransition = {
                fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300))
            }
        ) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    val targetRoute = if (viewModel.hasNameSetup.value) "chat" else "name_input"
                    navController.navigate(targetRoute) {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "name_input",
            enterTransition = {
                fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300))
            }
        ) {
            NameInputScreen(
                viewModel = viewModel,
                onNameConfirmed = {
                    navController.navigate("chat") {
                        popUpTo("name_input") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "chat",
            enterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            }
        ) {
            ChatScreen(
                viewModel = viewModel,
                onNavigateToProfile = {
                    navController.navigate("profile")
                }
            )
        }

        composable(
            route = "profile",
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            ProfileScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("chat") { inclusive = true }
                    }
                }
            )
        }
    }
}
