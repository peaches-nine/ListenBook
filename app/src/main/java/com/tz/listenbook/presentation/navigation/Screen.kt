package com.tz.listenbook.presentation.navigation

sealed class Screen(val route: String) {
    object BookShelf : Screen("bookshelf")
    object Player : Screen("player/{bookId}") {
        fun createRoute(bookId: Long) = "player/$bookId"
    }
    object Settings : Screen("settings")
}