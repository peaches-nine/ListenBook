package com.tz.listenbook

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
import androidx.core.view.WindowCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tz.listenbook.presentation.navigation.NavGraph
import com.tz.listenbook.presentation.navigation.Screen
import com.tz.listenbook.presentation.settings.SettingsPrefs
import com.tz.listenbook.presentation.theme.AudioBookAppTheme
import com.tz.listenbook.service.PlaybackService
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

        // Set initial status bar appearance
        updateStatusBarAppearance(_useDarkTheme.value)

        // Listen for SharedPreferences changes - store as member to prevent GC
        val prefs = getSharedPreferences("audiobook_settings", MODE_PRIVATE)
        prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dark_mode") {
                darkModeSetting = SettingsPrefs.getDarkMode(this)
                val newTheme = resolveDarkTheme()
                _useDarkTheme.value = newTheme
                updateStatusBarAppearance(newTheme)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        enableEdgeToEdge()
        setContent {
            val useDarkTheme by _useDarkTheme.collectAsState()

            // Update status bar when theme changes
            SideEffect {
                updateStatusBarAppearance(useDarkTheme)
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

    /**
     * Update status bar icon/text color
     * - darkTheme = true: white icons (for dark background)
     * - darkTheme = false: dark icons (for light background)
     */
    private fun updateStatusBarAppearance(darkTheme: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).let { controller ->
            // isAppearanceLightStatusBars = true means dark icons (for light theme)
            // isAppearanceLightStatusBars = false means white icons (for dark theme)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefsListener?.let { listener ->
            getSharedPreferences("audiobook_settings", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (darkModeSetting == "system") {
            val newTheme = resolveDarkTheme()
            _useDarkTheme.value = newTheme
            updateStatusBarAppearance(newTheme)
        }
    }

    private fun resolveDarkTheme(): Boolean {
        return when (darkModeSetting) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    override fun onPause() {
        super.onPause()
        if (!SettingsPrefs.isBackgroundPlayEnabled(this)) {
            startService(Intent(this, PlaybackService::class.java).apply { action = PlaybackService.ACTION_PAUSE })
        }
    }
}