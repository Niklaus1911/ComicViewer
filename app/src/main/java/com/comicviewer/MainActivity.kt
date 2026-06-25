package com.comicviewer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.comicviewer.loadAccentColor
import com.comicviewer.loadAmoledThemeEnabled
import com.comicviewer.loadAppLanguage
import com.comicviewer.loadDynamicColorEnabled
import com.comicviewer.loadThemeMode
import com.comicviewer.localizedAppContext
import com.comicviewer.resolveDarkTheme
import com.comicviewer.saveAccentColor
import com.comicviewer.saveAmoledThemeEnabled
import com.comicviewer.saveAppLanguage
import com.comicviewer.saveDynamicColorEnabled
import com.comicviewer.saveThemeMode
import com.comicviewer.ui.app.ComicViewerApp
import com.comicviewer.ui.app.ThemeUiState
import com.comicviewer.ui.theme.ComicViewerTheme

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
            var amoledThemeEnabled by remember { mutableStateOf(loadAmoledThemeEnabled(this)) }

            val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())

            ComicViewerTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                appColor = accentColor,
                amoledTheme = amoledThemeEnabled,
            ) {
                ComicViewerApp(
                    selectedLanguage = selectedLanguage,
                    themeUiState = ThemeUiState(
                        themeMode = themeMode,
                        dynamicColorEnabled = dynamicColorEnabled,
                        accentColor = accentColor,
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
                    },
                    onAccentColorSelected = { color ->
                        saveAccentColor(this, color)
                        accentColor = color
                    },
                    onAmoledThemeChanged = { enabled ->
                        saveAmoledThemeEnabled(this, enabled)
                        amoledThemeEnabled = enabled
                    },
                )
            }
        }
    }
}
