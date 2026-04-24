package com.tz.listenbook.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tz.listenbook.presentation.bookshelf.BookShelfScreen
import com.tz.listenbook.presentation.player.PlayerScreen
import com.tz.listenbook.presentation.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.BookShelf.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.BookShelf.route) {
            BookShelfScreen(
                onBookClick = { bookId ->
                    navController.navigate(Screen.Player.createRoute(bookId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("bookId") { type = androidx.navigation.NavType.LongType })
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}