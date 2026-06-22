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
import com.comicviewer.loadAmoledThemeEnabled
import com.comicviewer.loadAppLanguage
import com.comicviewer.localizedAppContext
import com.comicviewer.saveAmoledThemeEnabled
import com.comicviewer.saveAppLanguage
import com.comicviewer.ui.app.ComicViewerApp
import com.comicviewer.ui.theme.ComicViewerTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedAppContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var amoledThemeEnabled by remember { mutableStateOf(loadAmoledThemeEnabled(this)) }
            val selectedLanguage = remember { loadAppLanguage(this) }

            ComicViewerTheme(amoledTheme = amoledThemeEnabled) {
                ComicViewerApp(
                    selectedLanguage = selectedLanguage,
                    amoledThemeEnabled = amoledThemeEnabled,
                    onLanguageSelected = { language ->
                        saveAppLanguage(this, language)
                        if (language != selectedLanguage) recreate()
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
