package com.comicviewer

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import java.util.Locale

enum class AppLanguage(val preferenceValue: String) {
    System("system"),
    English("en"),
    Italian("it"),
}

enum class AppThemeMode(val preferenceValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

enum class ReaderReadingDirection(val preferenceValue: String) {
    LeftToRight("left_to_right"),
    RightToLeft("right_to_left"),
}

enum class ReaderPageFitMode(val preferenceValue: String) {
    FitScreen("fit_screen"),
    FitWidth("fit_width"),
}

enum class ReaderBackground(val preferenceValue: String) {
    Black("black"),
    DarkGray("dark_gray"),
    Sepia("sepia"),
    White("white"),
}

data class ReaderPreferences(
    val readingDirection: ReaderReadingDirection = ReaderReadingDirection.LeftToRight,
    val pageFitMode: ReaderPageFitMode = ReaderPageFitMode.FitScreen,
    val background: ReaderBackground = ReaderBackground.Black,
)

private const val APP_PREFERENCES_NAME = "comic_viewer_app_preferences"
private const val LANGUAGE_KEY = "language"
private const val AMOLED_THEME_KEY = "amoled_theme"
private const val THEME_MODE_KEY = "theme_mode"
private const val DYNAMIC_COLOR_KEY = "dynamic_color"
private const val ACCENT_COLOR_KEY = "accent_color"
private const val THEME_PRESET_KEY = "theme_preset"
private const val WELCOME_SEEN_KEY = "welcome_seen"
private const val READER_READING_DIRECTION_KEY = "reader_reading_direction"
private const val READER_PAGE_FIT_MODE_KEY = "reader_page_fit_mode"
private const val READER_BACKGROUND_KEY = "reader_background"
private const val DEBUG_PRO_UNLOCK_KEY = "debug_pro_unlock"

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

fun loadThemeMode(context: Context): AppThemeMode {
    val value = appPreferences(context).getString(THEME_MODE_KEY, AppThemeMode.System.preferenceValue)
    return AppThemeMode.entries.firstOrNull { it.preferenceValue == value } ?: AppThemeMode.System
}

fun saveThemeMode(context: Context, mode: AppThemeMode) {
    appPreferences(context)
        .edit()
        .putString(THEME_MODE_KEY, mode.preferenceValue)
        .apply()
}

fun loadDynamicColorEnabled(context: Context): Boolean =
    appPreferences(context).getBoolean(DYNAMIC_COLOR_KEY, true)

fun saveDynamicColorEnabled(context: Context, enabled: Boolean) {
    appPreferences(context)
        .edit()
        .putBoolean(DYNAMIC_COLOR_KEY, enabled)
        .apply()
}

fun loadAccentColor(context: Context): AppColor {
    val value = appPreferences(context).getString(ACCENT_COLOR_KEY, AppColor.BLUE.name)
    return AppColor.entries.firstOrNull { it.name == value } ?: AppColor.BLUE
}

fun saveAccentColor(context: Context, color: AppColor) {
    appPreferences(context)
        .edit()
        .putString(ACCENT_COLOR_KEY, color.name)
        .apply()
}

fun loadThemePreset(context: Context): AppThemePreset {
    val value = appPreferences(context).getString(THEME_PRESET_KEY, AppThemePreset.Default.preferenceValue)
    return AppThemePreset.entries.firstOrNull { it.preferenceValue == value } ?: AppThemePreset.Default
}

fun saveThemePreset(context: Context, preset: AppThemePreset) {
    appPreferences(context)
        .edit()
        .putString(THEME_PRESET_KEY, preset.preferenceValue)
        .apply()
}

fun resolvedThemePreset(
    themePreset: AppThemePreset,
    isProUnlocked: Boolean,
): AppThemePreset =
    if (isProUnlocked || !themePreset.proOnly) themePreset else AppThemePreset.Default

fun themePresetForDynamicColorSelection(
    dynamicColorEnabled: Boolean,
    currentThemePreset: AppThemePreset,
): AppThemePreset =
    if (dynamicColorEnabled) AppThemePreset.Default else currentThemePreset

fun loadReaderPreferences(context: Context): ReaderPreferences {
    val preferences = appPreferences(context)
    return ReaderPreferences(
        readingDirection = preferences.enumValue(
            key = READER_READING_DIRECTION_KEY,
            defaultValue = ReaderReadingDirection.LeftToRight,
            storageValue = ReaderReadingDirection::preferenceValue,
        ),
        pageFitMode = preferences.enumValue(
            key = READER_PAGE_FIT_MODE_KEY,
            defaultValue = ReaderPageFitMode.FitScreen,
            storageValue = ReaderPageFitMode::preferenceValue,
        ),
        background = preferences.enumValue(
            key = READER_BACKGROUND_KEY,
            defaultValue = ReaderBackground.Black,
            storageValue = ReaderBackground::preferenceValue,
        ),
    )
}

fun saveReaderPreferences(context: Context, readerPreferences: ReaderPreferences) {
    appPreferences(context)
        .edit()
        .putString(READER_READING_DIRECTION_KEY, readerPreferences.readingDirection.preferenceValue)
        .putString(READER_PAGE_FIT_MODE_KEY, readerPreferences.pageFitMode.preferenceValue)
        .putString(READER_BACKGROUND_KEY, readerPreferences.background.preferenceValue)
        .apply()
}

fun resolvedReaderPreferences(
    readerPreferences: ReaderPreferences,
    isProUnlocked: Boolean,
): ReaderPreferences =
    if (isProUnlocked) {
        readerPreferences
    } else {
        ReaderPreferences()
    }

fun effectiveProUnlocked(
    billingUnlocked: Boolean,
    debugOverrideUnlocked: Boolean,
    isDebugBuild: Boolean,
): Boolean = billingUnlocked || (isDebugBuild && debugOverrideUnlocked)

fun loadDebugProUnlocked(context: Context): Boolean =
    appPreferences(context).getBoolean(DEBUG_PRO_UNLOCK_KEY, false)

fun saveDebugProUnlocked(context: Context, unlocked: Boolean) {
    appPreferences(context)
        .edit()
        .putBoolean(DEBUG_PRO_UNLOCK_KEY, unlocked)
        .apply()
}

/**
 * Resolves whether the dark color scheme should be used for the given [mode]. Pure so it can be
 * unit-tested off-device; [systemInDark] is the platform's current night-mode flag.
 */
fun resolveDarkTheme(mode: AppThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    AppThemeMode.System -> systemInDark
    AppThemeMode.Light -> false
    AppThemeMode.Dark -> true
}

fun loadWelcomeSeen(context: Context): Boolean =
    appPreferences(context).getBoolean(WELCOME_SEEN_KEY, false)

fun saveWelcomeSeen(context: Context, seen: Boolean) {
    appPreferences(context)
        .edit()
        .putBoolean(WELCOME_SEEN_KEY, seen)
        .apply()
}

internal fun shouldShowWelcome(
    welcomeSeen: Boolean,
    hasLibraryComics: Boolean,
    hasRecentComics: Boolean,
): Boolean =
    !welcomeSeen && !hasLibraryComics && !hasRecentComics

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

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    defaultValue: T,
    storageValue: (T) -> String,
): T {
    val value = getString(key, storageValue(defaultValue))
    return enumValues<T>().firstOrNull { item -> storageValue(item) == value } ?: defaultValue
}

private fun systemLocale(context: Context): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale
    }
