package com.comicviewer

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
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

enum class LibraryDisplayMode(val preferenceValue: String) {
    Grid("grid"),
    CompactGrid("compact_grid"),
    List("list"),
}

enum class ReaderReadingDirection(val preferenceValue: String) {
    LeftToRight("left_to_right"),
    RightToLeft("right_to_left"),
}

enum class ReaderPageMode(val preferenceValue: String) {
    SinglePage("single_page"),
    TwoPage("two_page"),
    Vertical("vertical"),
}

enum class ReaderPageFitMode(val preferenceValue: String) {
    FitScreen("fit_screen"),
    FitWidth("fit_width"),
    FitHeight("fit_height"),
    Original("original"),
}

enum class ReaderBackground(val preferenceValue: String) {
    Black("black"),
    DarkGray("dark_gray"),
    Sepia("sepia"),
    White("white"),
}

data class ReaderPreferences(
    val pageMode: ReaderPageMode = ReaderPageMode.SinglePage,
    val readingDirection: ReaderReadingDirection = ReaderReadingDirection.LeftToRight,
    val pageFitMode: ReaderPageFitMode = ReaderPageFitMode.FitScreen,
    val background: ReaderBackground = ReaderBackground.Black,
    val brightness: Float? = null,
    val marginDp: Int = 0,
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
)

data class LibraryAutomationPreferences(
    val importFolderUri: String? = null,
    val importFolderName: String? = null,
    val autoScanEnabled: Boolean = false,
    val lastAutoScanAt: Long = 0L,
    val autoDeleteTempFiles: Boolean = true,
)

private const val APP_PREFERENCES_NAME = "comic_viewer_app_preferences"
private const val LANGUAGE_KEY = "language"
private const val AMOLED_THEME_KEY = "amoled_theme"
private const val THEME_MODE_KEY = "theme_mode"
private const val DYNAMIC_COLOR_KEY = "dynamic_color"
private const val ACCENT_COLOR_KEY = "accent_color"
private const val THEME_PRESET_KEY = "theme_preset"
private const val WELCOME_SEEN_KEY = "welcome_seen"
private const val READER_PAGE_MODE_KEY = "reader_page_mode"
private const val READER_READING_DIRECTION_KEY = "reader_reading_direction"
private const val READER_PAGE_FIT_MODE_KEY = "reader_page_fit_mode"
private const val READER_BACKGROUND_KEY = "reader_background"
private const val READER_BRIGHTNESS_ENABLED_KEY = "reader_brightness_enabled"
private const val READER_BRIGHTNESS_KEY = "reader_brightness"
private const val READER_MARGIN_DP_KEY = "reader_margin_dp"
private const val READER_KEEP_SCREEN_ON_KEY = "reader_keep_screen_on"
private const val READER_SHOW_PAGE_NUMBER_KEY = "reader_show_page_number"
private const val IMPORT_FOLDER_URI_KEY = "import_folder_uri"
private const val IMPORT_FOLDER_NAME_KEY = "import_folder_name"
private const val AUTO_SCAN_LIBRARY_KEY = "auto_scan_library"
private const val LAST_AUTO_SCAN_AT_KEY = "last_auto_scan_at"
private const val AUTO_DELETE_TEMP_FILES_KEY = "auto_delete_temp_files"
private const val DEBUG_PRO_UNLOCK_KEY = "debug_pro_unlock"
private const val LIBRARY_DISPLAY_MODE_KEY = "library_display_mode"

fun loadAppLanguage(context: Context): AppLanguage {
    val value = appPreferences(context).getString(LANGUAGE_KEY, AppLanguage.System.preferenceValue)
    return AppLanguage.entries.firstOrNull { it.preferenceValue == value } ?: AppLanguage.System
}

fun saveAppLanguage(context: Context, language: AppLanguage) {
    appPreferences(context).edit {
        putString(LANGUAGE_KEY, language.preferenceValue)
    }
}

fun loadAmoledThemeEnabled(context: Context): Boolean =
    appPreferences(context).getBoolean(AMOLED_THEME_KEY, false)

fun saveAmoledThemeEnabled(context: Context, enabled: Boolean) {
    appPreferences(context).edit {
        putBoolean(AMOLED_THEME_KEY, enabled)
    }
}

fun loadThemeMode(context: Context): AppThemeMode {
    val value = appPreferences(context).getString(THEME_MODE_KEY, AppThemeMode.Dark.preferenceValue)
    return AppThemeMode.entries.firstOrNull { it.preferenceValue == value } ?: AppThemeMode.Dark
}

fun saveThemeMode(context: Context, mode: AppThemeMode) {
    appPreferences(context).edit {
        putString(THEME_MODE_KEY, mode.preferenceValue)
    }
}

fun loadLibraryDisplayMode(context: Context): LibraryDisplayMode {
    val value = appPreferences(context).getString(LIBRARY_DISPLAY_MODE_KEY, LibraryDisplayMode.Grid.preferenceValue)
    return LibraryDisplayMode.entries.firstOrNull { it.preferenceValue == value } ?: LibraryDisplayMode.Grid
}

fun saveLibraryDisplayMode(context: Context, mode: LibraryDisplayMode) {
    appPreferences(context).edit {
        putString(LIBRARY_DISPLAY_MODE_KEY, mode.preferenceValue)
    }
}

fun loadDynamicColorEnabled(context: Context): Boolean =
    appPreferences(context).getBoolean(DYNAMIC_COLOR_KEY, false)

fun saveDynamicColorEnabled(context: Context, enabled: Boolean) {
    appPreferences(context).edit {
        putBoolean(DYNAMIC_COLOR_KEY, enabled)
    }
}

fun loadAccentColor(context: Context): AppColor {
    val value = appPreferences(context).getString(ACCENT_COLOR_KEY, AppColor.PURPLE.name)
    return AppColor.entries.firstOrNull { it.name == value } ?: AppColor.PURPLE
}

fun saveAccentColor(context: Context, color: AppColor) {
    appPreferences(context).edit {
        putString(ACCENT_COLOR_KEY, color.name)
    }
}

fun loadThemePreset(context: Context): AppThemePreset {
    val value = appPreferences(context).getString(THEME_PRESET_KEY, AppThemePreset.Default.preferenceValue)
    return AppThemePreset.entries.firstOrNull { it.preferenceValue == value } ?: AppThemePreset.Default
}

fun saveThemePreset(context: Context, preset: AppThemePreset) {
    appPreferences(context).edit {
        putString(THEME_PRESET_KEY, preset.preferenceValue)
    }
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
        pageMode = preferences.enumValue(
            key = READER_PAGE_MODE_KEY,
            defaultValue = ReaderPageMode.SinglePage,
            storageValue = ReaderPageMode::preferenceValue,
        ),
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
        brightness = if (preferences.getBoolean(READER_BRIGHTNESS_ENABLED_KEY, false)) {
            preferences.getFloat(READER_BRIGHTNESS_KEY, 0.5f)
                .takeIf(Float::isFinite)
                ?.coerceIn(0.05f, 1f)
                ?: 0.5f
        } else {
            null
        },
        marginDp = preferences.getInt(READER_MARGIN_DP_KEY, 0).coerceIn(0, 32),
        keepScreenOn = preferences.getBoolean(READER_KEEP_SCREEN_ON_KEY, true),
        showPageNumber = preferences.getBoolean(READER_SHOW_PAGE_NUMBER_KEY, true),
    )
}

fun saveReaderPreferences(context: Context, readerPreferences: ReaderPreferences) {
    val brightness = readerPreferences.brightness
        ?.takeIf(Float::isFinite)
        ?.coerceIn(0.05f, 1f)
    appPreferences(context).edit {
        putString(READER_PAGE_MODE_KEY, readerPreferences.pageMode.preferenceValue)
        putString(READER_READING_DIRECTION_KEY, readerPreferences.readingDirection.preferenceValue)
        putString(READER_PAGE_FIT_MODE_KEY, readerPreferences.pageFitMode.preferenceValue)
        putString(READER_BACKGROUND_KEY, readerPreferences.background.preferenceValue)
        putBoolean(READER_BRIGHTNESS_ENABLED_KEY, brightness != null)
        putFloat(READER_BRIGHTNESS_KEY, brightness ?: 0.5f)
        putInt(READER_MARGIN_DP_KEY, readerPreferences.marginDp.coerceIn(0, 32))
        putBoolean(READER_KEEP_SCREEN_ON_KEY, readerPreferences.keepScreenOn)
        putBoolean(READER_SHOW_PAGE_NUMBER_KEY, readerPreferences.showPageNumber)
    }
}

fun resolvedReaderPreferences(
    readerPreferences: ReaderPreferences,
    isProUnlocked: Boolean,
): ReaderPreferences =
    if (isProUnlocked) {
        readerPreferences
    } else {
        readerPreferences.copy(
            // Two-page spreads are Pro; single page and vertical scroll are free.
            pageMode = if (readerPreferences.pageMode == ReaderPageMode.TwoPage) {
                ReaderPageMode.SinglePage
            } else {
                readerPreferences.pageMode
            },
            readingDirection = ReaderReadingDirection.LeftToRight,
            pageFitMode = ReaderPageFitMode.FitScreen,
            background = ReaderBackground.Black,
            brightness = null,
            marginDp = 0,
        )
    }

fun loadLibraryAutomationPreferences(context: Context): LibraryAutomationPreferences {
    val preferences = appPreferences(context)
    return LibraryAutomationPreferences(
        importFolderUri = preferences.getString(IMPORT_FOLDER_URI_KEY, null)?.takeIf(String::isNotBlank),
        importFolderName = preferences.getString(IMPORT_FOLDER_NAME_KEY, null)?.takeIf(String::isNotBlank),
        autoScanEnabled = preferences.getBoolean(AUTO_SCAN_LIBRARY_KEY, false),
        lastAutoScanAt = preferences.getLong(LAST_AUTO_SCAN_AT_KEY, 0L).coerceAtLeast(0L),
        autoDeleteTempFiles = preferences.getBoolean(AUTO_DELETE_TEMP_FILES_KEY, true),
    )
}

fun saveLibraryAutomationPreferences(
    context: Context,
    preferences: LibraryAutomationPreferences,
) {
    appPreferences(context).edit {
        putString(IMPORT_FOLDER_URI_KEY, preferences.importFolderUri)
        putString(IMPORT_FOLDER_NAME_KEY, preferences.importFolderName)
        putBoolean(AUTO_SCAN_LIBRARY_KEY, preferences.autoScanEnabled)
        putLong(LAST_AUTO_SCAN_AT_KEY, preferences.lastAutoScanAt.coerceAtLeast(0L))
        putBoolean(AUTO_DELETE_TEMP_FILES_KEY, preferences.autoDeleteTempFiles)
    }
}

internal fun shouldRunAutomaticLibraryScan(
    enabled: Boolean,
    lastScanAt: Long,
    nowMs: Long,
    intervalMs: Long = 24L * 60L * 60L * 1_000L,
): Boolean {
    if (!enabled) return false
    val normalizedLastScanAt = lastScanAt.coerceAtLeast(0L)
    val normalizedNow = nowMs.coerceAtLeast(0L)
    val normalizedInterval = intervalMs.coerceAtLeast(0L)
    if (normalizedNow < normalizedLastScanAt) return false
    return normalizedNow - normalizedLastScanAt >= normalizedInterval
}

fun effectiveProUnlocked(
    billingUnlocked: Boolean,
    debugOverrideUnlocked: Boolean,
    isDebugBuild: Boolean,
): Boolean = billingUnlocked || (isDebugBuild && debugOverrideUnlocked)

fun loadDebugProUnlocked(context: Context): Boolean =
    appPreferences(context).getBoolean(DEBUG_PRO_UNLOCK_KEY, false)

fun saveDebugProUnlocked(context: Context, unlocked: Boolean) {
    appPreferences(context).edit {
        putBoolean(DEBUG_PRO_UNLOCK_KEY, unlocked)
    }
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
    appPreferences(context).edit {
        putBoolean(WELCOME_SEEN_KEY, seen)
    }
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
    configuration.setLocales(LocaleList(locale))
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

private fun systemLocale(context: Context): Locale = context.resources.configuration.locales[0]
