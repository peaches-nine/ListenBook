package com.tz.audiobook

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tz.audiobook.presentation.navigation.NavGraph
import com.tz.audiobook.presentation.navigation.Screen
import com.tz.audiobook.presentation.settings.SettingsPrefs
import com.tz.audiobook.presentation.theme.AudioBookAppTheme
import com.tz.audiobook.service.PlaybackService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _useDarkTheme = MutableStateFlow(false)
    val useDarkTheme: StateFlow<Boolean> = _useDarkTheme.asStateFlow()

    private var darkModeSetting: String = "system"
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolve initial dark theme BEFORE setContent
        darkModeSetting = SettingsPrefs.getDarkMode(this)
        _useDarkTheme.value = resolveDarkTheme()

        // Listen for SharedPreferences changes - store as member to prevent GC
        val prefs = getSharedPreferences("audiobook_settings", MODE_PRIVATE)
        prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dark_mode") {
                darkModeSetting = SettingsPrefs.getDarkMode(this)
                _useDarkTheme.value = resolveDarkTheme()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        enableEdgeToEdge()
        setContent {
            val useDarkTheme by _useDarkTheme.collectAsState()

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

    override fun onDestroy() {
        super.onDestroy()
        // Unregister listener to prevent leaks
        prefsListener?.let { listener ->
            getSharedPreferences("audiobook_settings", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Update dark theme when system mode changes
        if (darkModeSetting == "system") {
            _useDarkTheme.value = resolveDarkTheme()
        }
    }

    private fun resolveDarkTheme(): Boolean {
        return when (darkModeSetting) {
            "dark" -> true
            "light" -> false
            else -> {
                // Read directly from Activity resources, not from Compose
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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