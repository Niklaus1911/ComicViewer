package com.comicviewer

import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppBackupTest {
    @Test
    fun appBackup_roundTripsAllAppDataAndExpandedReaderPreferences() {
        val libraryComic = sampleLibraryComic(
            title = "Batman: Year One",
            path = "/storage/emulated/0/Comics/Batman.cbz",
            addedAt = 100L,
        )
        val recentComic = sampleRecentComic(
            title = libraryComic.title,
            path = libraryComic.path,
            lastPageIndex = 86,
            pageCount = 184,
            lastOpenedAt = 200L,
        ).copy(completedAt = 250L)
        val readerPreferences = ReaderPreferences(
            pageMode = ReaderPageMode.TwoPage,
            readingDirection = ReaderReadingDirection.RightToLeft,
            pageFitMode = ReaderPageFitMode.Original,
            background = ReaderBackground.Sepia,
            brightness = 0.73f,
            marginDp = 18,
            keepScreenOn = false,
            showPageNumber = false,
        )
        val backup = AppBackupData(
            libraryComics = listOf(libraryComic),
            recentComics = listOf(recentComic),
            libraryMetadata = listOf(
                LibraryComicMetadata(
                    sourceType = libraryComic.sourceType,
                    path = libraryComic.path,
                    favorite = true,
                    tags = listOf("Crime", "Noir"),
                    updatedAt = 300L,
                ),
            ),
            libraryDetails = listOf(
                LibraryComicDetails(
                    sourceType = libraryComic.sourceType,
                    path = libraryComic.path,
                    pageCount = 184,
                    fileSizeBytes = 78_000_000L,
                    lastModifiedAt = 400L,
                    metadata = ComicInfoMetadata(
                        title = "Year One",
                        series = "Batman",
                        writers = listOf("Frank Miller"),
                        publisher = "DC Comics",
                        year = 1987,
                        language = "en",
                        genres = listOf("Crime"),
                        pageCount = 184,
                    ),
                    refreshedAt = 500L,
                ),
            ),
            readerPreferences = readerPreferences,
            automationPreferences = LibraryAutomationPreferences(
                importFolderUri = "content://provider/tree/primary%3AComics",
                importFolderName = "Comics",
                autoScanEnabled = true,
                lastAutoScanAt = 600L,
                autoDeleteTempFiles = false,
            ),
            themePreferences = BackupThemePreferences(
                language = AppLanguage.Italian,
                themeMode = AppThemeMode.Dark,
                dynamicColorEnabled = false,
                accentColor = AppColor.PURPLE,
                themePreset = AppThemePreset.Aurora,
                amoledEnabled = true,
            ),
        )

        val decoded = decodeAppBackup(encodeAppBackup(backup))

        assertEquals(backup, decoded)
        assertEquals(readerPreferences, decoded.readerPreferences)
    }

    @Test
    fun appBackup_rejectsOversizedAndUnsupportedPayloads() {
        assertThrows(IllegalArgumentException::class.java) {
            readBackupBytesBounded(ByteArrayInputStream(ByteArray(MAX_BACKUP_BYTES + 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeAppBackup(ByteArray(MAX_BACKUP_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeAppBackup(
                """
                {"schemaVersion":2,"libraryComics":[],"recentComics":[],"libraryMetadata":[],"libraryDetails":[],"readerPreferences":{},"automationPreferences":{},"themePreferences":{}}
                """.trimIndent().toByteArray(),
            )
        }
        val excessiveLibraryRecords = List(MAX_LIBRARY_COMICS + 1) { "{}" }.joinToString(",")
        assertThrows(IllegalArgumentException::class.java) {
            decodeAppBackup(
                """
                {"schemaVersion":1,"libraryComics":[$excessiveLibraryRecords],"recentComics":[],"libraryMetadata":[],"libraryDetails":[]}
                """.trimIndent().toByteArray(),
            )
        }
    }
}
