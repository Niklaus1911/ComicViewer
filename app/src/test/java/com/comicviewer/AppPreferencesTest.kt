package com.comicviewer

import com.comicviewer.ui.theme.AppThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun shouldShowWelcome_onlyForFreshUnseenInstalls() {
        assertTrue(
            shouldShowWelcome(
                welcomeSeen = false,
                hasLibraryComics = false,
                hasRecentComics = false,
            ),
        )

        assertFalse(
            shouldShowWelcome(
                welcomeSeen = true,
                hasLibraryComics = false,
                hasRecentComics = false,
            ),
        )
        assertFalse(
            shouldShowWelcome(
                welcomeSeen = false,
                hasLibraryComics = true,
                hasRecentComics = false,
            ),
        )
        assertFalse(
            shouldShowWelcome(
                welcomeSeen = false,
                hasLibraryComics = false,
                hasRecentComics = true,
            ),
        )
    }

    @Test
    fun resolvedReaderPreferences_returnsDefaultsUntilProUnlocked() {
        val proPreferences = ReaderPreferences(
            readingDirection = ReaderReadingDirection.RightToLeft,
            pageFitMode = ReaderPageFitMode.FitWidth,
            background = ReaderBackground.Sepia,
        )

        assertEquals(ReaderPreferences(), resolvedReaderPreferences(proPreferences, isProUnlocked = false))
        assertEquals(proPreferences, resolvedReaderPreferences(proPreferences, isProUnlocked = true))
    }

    @Test
    fun effectiveProUnlocked_usesDebugOverrideOnlyForDebugBuilds() {
        assertTrue(
            effectiveProUnlocked(
                billingUnlocked = false,
                debugOverrideUnlocked = true,
                isDebugBuild = true,
            ),
        )
        assertFalse(
            effectiveProUnlocked(
                billingUnlocked = false,
                debugOverrideUnlocked = true,
                isDebugBuild = false,
            ),
        )
        assertTrue(
            effectiveProUnlocked(
                billingUnlocked = true,
                debugOverrideUnlocked = false,
                isDebugBuild = false,
            ),
        )
    }

    @Test
    fun resolvedThemePreset_defaultsLockedProPresetsUntilProUnlocked() {
        assertEquals(
            AppThemePreset.Default,
            resolvedThemePreset(AppThemePreset.Ink, isProUnlocked = false),
        )
        assertEquals(
            AppThemePreset.Ink,
            resolvedThemePreset(AppThemePreset.Ink, isProUnlocked = true),
        )
        assertEquals(
            AppThemePreset.Default,
            resolvedThemePreset(AppThemePreset.Default, isProUnlocked = false),
        )
    }

    @Test
    fun themePresetForDynamicColorSelection_resetsPresetWhenDynamicColorIsEnabled() {
        assertEquals(
            AppThemePreset.Default,
            themePresetForDynamicColorSelection(
                dynamicColorEnabled = true,
                currentThemePreset = AppThemePreset.Ink,
            ),
        )
        assertEquals(
            AppThemePreset.Ink,
            themePresetForDynamicColorSelection(
                dynamicColorEnabled = false,
                currentThemePreset = AppThemePreset.Ink,
            ),
        )
    }

    @Test
    fun resolveDarkTheme_followsSystemOnlyInSystemMode() {
        assertTrue(resolveDarkTheme(AppThemeMode.System, systemInDark = true))
        assertFalse(resolveDarkTheme(AppThemeMode.System, systemInDark = false))
        assertFalse(resolveDarkTheme(AppThemeMode.Light, systemInDark = true))
        assertFalse(resolveDarkTheme(AppThemeMode.Light, systemInDark = false))
        assertTrue(resolveDarkTheme(AppThemeMode.Dark, systemInDark = false))
        assertTrue(resolveDarkTheme(AppThemeMode.Dark, systemInDark = true))
    }

    @Test
    fun resolvedReaderPreferences_keepsVerticalScrollFreeButNotTwoPage() {
        val vertical = ReaderPreferences(pageMode = ReaderPageMode.Vertical, background = ReaderBackground.Sepia)
        val twoPage = ReaderPreferences(pageMode = ReaderPageMode.TwoPage)

        assertEquals(ReaderPageMode.Vertical, resolvedReaderPreferences(vertical, isProUnlocked = false).pageMode)
        assertEquals(ReaderBackground.Black, resolvedReaderPreferences(vertical, isProUnlocked = false).background)
        assertEquals(ReaderPageMode.SinglePage, resolvedReaderPreferences(twoPage, isProUnlocked = false).pageMode)
        assertEquals(ReaderPageMode.TwoPage, resolvedReaderPreferences(twoPage, isProUnlocked = true).pageMode)
    }
}
