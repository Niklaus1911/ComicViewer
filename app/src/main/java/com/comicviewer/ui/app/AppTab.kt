package com.comicviewer.ui.app

enum class AppTab {
    Library,
    Import,
    Search,
    History,
    Settings,
}

internal fun restoredAppTab(savedName: String): AppTab = when (savedName) {
    "Browse" -> AppTab.Import
    else -> runCatching { AppTab.valueOf(savedName) }.getOrDefault(AppTab.Library)
}
