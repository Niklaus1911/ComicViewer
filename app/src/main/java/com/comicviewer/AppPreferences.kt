package com.comicviewer

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val preferenceValue: String) {
    System("system"),
    English("en"),
    Italian("it"),
}

private const val APP_PREFERENCES_NAME = "comic_viewer_app_preferences"
private const val LANGUAGE_KEY = "language"
private const val AMOLED_THEME_KEY = "amoled_theme"

fun loadAppLanguage(context: Context): AppLanguage {
    val value = appPreferences(context).getString(LANGUAGE_KEY, AppLanguage.System.preferenceValue)
    return AppLanguage.entries.firstOrNull { it.preferenceValue == value } ?: AppLanguage.System
}

fun saveAppLanguage(context: Context, language: AppLanguage) {
    appPreferences(context)
        .edit()
        .putString(LANGUAGE_KEY, language.preferenceValue)
        .apply()
}

fun loadAmoledThemeEnabled(context: Context): Boolean =
    appPreferences(context).getBoolean(AMOLED_THEME_KEY, false)

fun saveAmoledThemeEnabled(context: Context, enabled: Boolean) {
    appPreferences(context)
        .edit()
        .putBoolean(AMOLED_THEME_KEY, enabled)
        .apply()
}

fun localizedAppContext(context: Context): Context {
    val language = loadAppLanguage(context)
    val locale = when (language) {
        AppLanguage.System -> {
            Locale.setDefault(systemLocale(context))
            return context
        }
        AppLanguage.English -> Locale.ENGLISH
        AppLanguage.Italian -> Locale.ITALIAN
    }

    Locale.setDefault(locale)
    val configuration = Configuration(context.resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.setLocales(LocaleList(locale))
    } else {
        @Suppress("DEPRECATION")
        configuration.locale = locale
    }
    return context.createConfigurationContext(configuration)
}

private fun appPreferences(context: Context) =
    context.getSharedPreferences(APP_PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun systemLocale(context: Context): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale
    }
