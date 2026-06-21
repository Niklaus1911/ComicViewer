package com.comicviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.comicviewer.ui.app.ComicViewerApp
import com.comicviewer.ui.theme.ComicViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ComicViewerTheme {
                ComicViewerApp()
            }
        }
    }
}
