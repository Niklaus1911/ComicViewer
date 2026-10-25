package com.comicviewer.ui.app

enum class AppTab {
    Library,
    History,
    Settings,
}

/** Tabs saved by older versions (Browse, Import, Search) now live inside the Library. */
internal fun restoredAppTab(savedName: String): AppTab =
    runCatching { AppTab.valueOf(savedName) }.getOrDefault(AppTab.Library)
