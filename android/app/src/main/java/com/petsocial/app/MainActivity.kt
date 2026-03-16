package com.petsocial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableStateOf
import com.petsocial.app.ui.BarkWiseTheme
import com.petsocial.app.ui.PetSocialApp

class MainActivity : ComponentActivity() {
    private val deepLinkState = mutableStateOf<String?>(null)
    private val themeModeState = mutableStateOf(HOME_THEME_MODE_SYSTEM)
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == HOME_THEME_MODE_KEY) {
            themeModeState.value = prefs.getString(HOME_THEME_MODE_KEY, HOME_THEME_MODE_SYSTEM)
                ?: HOME_THEME_MODE_SYSTEM
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(HOME_SETTINGS_PREFS, MODE_PRIVATE)
        themeModeState.value = prefs.getString(HOME_THEME_MODE_KEY, HOME_THEME_MODE_SYSTEM) ?: HOME_THEME_MODE_SYSTEM
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        deepLinkState.value = intent?.dataString
        setContent {
            BarkWiseTheme(themeMode = themeModeState.value) {
                PetSocialApp(initialDeepLink = deepLinkState.value)
            }
        }
    }

    override fun onDestroy() {
        getSharedPreferences(HOME_SETTINGS_PREFS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        deepLinkState.value = intent.dataString
    }

    companion object {
        private const val HOME_SETTINGS_PREFS = "home_settings"
        private const val HOME_THEME_MODE_KEY = "theme_mode"
        private const val HOME_THEME_MODE_SYSTEM = "system"
        private const val HOME_THEME_MODE_LIGHT = "light"
        private const val HOME_THEME_MODE_DARK = "dark"
    }
}
