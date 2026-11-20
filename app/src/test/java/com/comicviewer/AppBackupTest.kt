package com.comicviewer

import com.comicviewer.ui.theme.AppColor
import com.comicviewer.ui.theme.AppThemePreset
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppBackupTest {
    @Test
    fun restoredRecentComics_mergeKeepsNewerReadingActivityOnEitherSide() {
        val local = sampleRecentComic(lastPageIndex = 8, pageCount = 20, lastOpenedAt = 300L)
        val older = local.copy(lastPageIndex = 2, lastOpenedAt = 100L)
        val newer = local.copy(lastPageIndex = 4, lastOpenedAt = 400L)
        val localOnly = local.copy(path = "/local.cbz", lastOpenedAt = 50L)
        val backupOnly = local.copy(path = "/backup.cbz", lastOpenedAt = 200L)

        assertEquals(
            listOf(local, backupOnly, localOnly),
            restoredRecentComics(listOf(local, localOnly), listOf(older, backupOnly), RestoreMode.Merge),
        )
        assertEquals(listOf(newer), restoredRecentComics(listOf(local), listOf(newer), RestoreMode.Merge))
        assertEquals(listOf(local), restoredRecentComics(listOf(local), listOf(older.copy(lastOpenedAt = 300L)), RestoreMode.Merge))
        assertEquals(
            listOf(local.copy(completedAt = 500L)),
            restoredRecentComics(listOf(newer), listOf(local.copy(completedAt = 500L)), RestoreMode.Merge),
        )
    }

    @Test
    fun restoredRecentComics_preservesReplaceEmptyAndHistoryLimitBehavior() {
        val local = sampleRecentComic(lastOpenedAt = 300L)
        val older = local.copy(lastPageIndex = 2, lastOpenedAt = 100L)
        assertEquals(listOf(older), restoredRecentComics(listOf(local), listOf(older), RestoreMode.Replace))
        assertEquals(emptyList<RecentComic>(), restoredRecentComics(listOf(local), emptyList(), RestoreMode.Replace))
        assertEquals(listOf(local), restoredRecentComics(listOf(local), emptyList(), RestoreMode.Merge))
        assertEquals(listOf(local), restoredRecentComics(emptyList(), listOf(local), RestoreMode.Merge))

        val history = (1..MAX_RECENT_COMICS).map { local.copy(path = "/$it.cbz", lastOpenedAt = it.toLong()) }.reversed()
        val added = local.copy(path = "/new.cbz", lastOpenedAt = 1_000L)
        val merged = restoredRecentComics(history, listOf(added), RestoreMode.Merge)
        assertEquals(MAX_RECENT_COMICS, merged.size)
        assertEquals(added, merged.first())
        assertEquals(2L, merged.last().lastOpenedAt)
    }

    @Test
    fun restoredRecentComics_keepsLocalProgressUpdatedAfterBackupWasRead() {
        val opened = sampleRecentComic(lastPageIndex = 1, pageCount = 20, lastOpenedAt = 100L)
        val backup = opened.copy(lastPageIndex = 3, lastOpenedAt = 200L)
        val local = updateRecentComicPage(listOf(opened), opened.sourceType, opened.path, 8, 20, nowMs = 300L)

        assertEquals(300L, local.single().lastOpenedAt)
        assertEquals(local, restoredRecentComics(local, listOf(backup), RestoreMode.Merge))
    }

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
            autoCrop = true,
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
