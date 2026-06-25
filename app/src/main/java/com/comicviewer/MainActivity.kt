package com.comicviewer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.comicviewer.loadAccentColor
import com.comicviewer.loadAmoledThemeEnabled
import com.comicviewer.loadAppLanguage
import com.comicviewer.loadDynamicColorEnabled
import com.comicviewer.loadThemeMode
import com.comicviewer.loadThemePreset
import com.comicviewer.localizedAppContext
import com.comicviewer.saveAccentColor
import com.comicviewer.saveAmoledThemeEnabled
import com.comicviewer.saveAppLanguage
import com.comicviewer.saveDynamicColorEnabled
import com.comicviewer.saveThemeMode
import com.comicviewer.saveThemePreset
import com.comicviewer.themePresetForDynamicColorSelection
import com.comicviewer.ui.app.ComicViewerApp
import com.comicviewer.ui.app.ThemeUiState
import com.comicviewer.ui.theme.AppThemePreset

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedAppContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val selectedLanguage = remember { loadAppLanguage(this) }
            var themeMode by remember { mutableStateOf(loadThemeMode(this)) }
            var dynamicColorEnabled by remember { mutableStateOf(loadDynamicColorEnabled(this)) }
            var accentColor by remember { mutableStateOf(loadAccentColor(this)) }
            var themePreset by remember { mutableStateOf(loadThemePreset(this)) }
            var amoledThemeEnabled by remember { mutableStateOf(loadAmoledThemeEnabled(this)) }

            ComicViewerApp(
                selectedLanguage = selectedLanguage,
                themeUiState = ThemeUiState(
                    themeMode = themeMode,
                    dynamicColorEnabled = dynamicColorEnabled,
                    accentColor = accentColor,
                    themePreset = themePreset,
                    amoledEnabled = amoledThemeEnabled,
                ),
                onLanguageSelected = { language ->
                    saveAppLanguage(this, language)
                    if (language != selectedLanguage) recreate()
                },
                onThemeModeSelected = { mode ->
                    saveThemeMode(this, mode)
                    themeMode = mode
                },
                onDynamicColorChanged = { enabled ->
                    saveDynamicColorEnabled(this, enabled)
                    dynamicColorEnabled = enabled
                    val nextThemePreset = themePresetForDynamicColorSelection(
                        dynamicColorEnabled = enabled,
                        currentThemePreset = themePreset,
                    )
                    if (nextThemePreset != themePreset) {
                        saveThemePreset(this, nextThemePreset)
                        themePreset = nextThemePreset
                    }
                },
                onAccentColorSelected = { color ->
                    saveAccentColor(this, color)
                    accentColor = color
                },
                onThemePresetSelected = { preset ->
                    saveThemePreset(this, preset)
                    themePreset = preset
                    if (preset != AppThemePreset.Default && dynamicColorEnabled) {
                        saveDynamicColorEnabled(this, false)
                        dynamicColorEnabled = false
                    }
                },
                onAmoledThemeChanged = { enabled ->
                    saveAmoledThemeEnabled(this, enabled)
                    amoledThemeEnabled = enabled
                },
            )
        }
    }
}
