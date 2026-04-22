package com.tz.audiobook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
            val darkMode = SettingsPrefs.getDarkMode(this)
            val useDarkTheme = remember {
                when (darkMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
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
