package com.tz.audiobook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tz.audiobook.presentation.navigation.NavGraph
import com.tz.audiobook.presentation.navigation.Screen
import com.tz.audiobook.presentation.settings.SettingsPrefs
import com.tz.audiobook.presentation.theme.AudioBookAppTheme
import com.tz.audiobook.service.PlaybackService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Observe dark mode setting changes - use rememberSaveable for stability
            var darkMode by rememberSaveable { mutableStateOf(SettingsPrefs.getDarkMode(this@MainActivity)) }
            val systemDarkTheme = isSystemInDarkTheme()

            DisposableEffect(Unit) {
                val prefs = getSharedPreferences("audiobook_settings", MODE_PRIVATE)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "dark_mode") {
                        val newMode = SettingsPrefs.getDarkMode(this@MainActivity)
                        if (newMode != darkMode) {
                            darkMode = newMode
                        }
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val useDarkTheme = remember(darkMode, systemDarkTheme) {
                when (darkMode) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDarkTheme
                }
            }

            AudioBookAppTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold { padding ->
                    NavGraph(
                        navController = navController,
                        startDestination = Screen.BookShelf.route
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!SettingsPrefs.isBackgroundPlayEnabled(this)) {
            val intent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PAUSE
            }
            startService(intent)
        }
    }
}
