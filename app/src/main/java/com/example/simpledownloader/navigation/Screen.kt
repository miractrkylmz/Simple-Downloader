package com.example.simpledownloader.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object History : Screen("history")
}
