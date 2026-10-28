package com.comicviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccessTest {
    @Test
    fun storageAccessKindForSdk_usesLegacyTreeAndAllFilesByPlatform() {
        assertEquals(StorageAccessKind.LegacyReadPermission, storageAccessKindForSdk(24))
        assertEquals(StorageAccessKind.LegacyReadPermission, storageAccessKindForSdk(28))
        assertEquals(StorageAccessKind.DocumentTree, storageAccessKindForSdk(29))
        assertEquals(StorageAccessKind.AllFiles, storageAccessKindForSdk(30))
        assertEquals(StorageAccessKind.AllFiles, storageAccessKindForSdk(36))
    }

    @Test
    fun documentPermissionCovers_matchesExactAndSharedTreeGrantsOnly() {
        val tree = "content://provider/tree/primary%3AComics"
        val firstChild = "content://provider/tree/primary%3AComics/document/primary%3AComics%2FIssue1.cbz"
        val secondChild = "content://provider/tree/primary%3aComics/document/primary%3AComics%2FIssue2.cbz"

        assertTrue(documentPermissionCovers(tree, tree))
        assertTrue(documentPermissionCovers(tree, firstChild))
        assertTrue(documentPermissionCovers(firstChild, secondChild))
        assertFalse(documentPermissionCovers(tree, "content://provider/tree/primary%3AOther"))
        assertFalse(documentPermissionCovers(tree, "content://other/tree/primary%3AComics"))
        assertFalse(documentPermissionCovers("content://provider/document/one", "content://provider/document/two"))
    }

    @Test
    fun documentIdentityKey_matchesTheSameDocumentAcrossUriShapes() {
        val picked = "content://Provider/document/primary%3AComics%2FIssue1.cbz"
        val scanned = "content://provider/tree/primary%3AComics/document/primary%3aComics%2FIssue1.cbz"
        val pickedFolder = "content://provider/tree/primary%3AComics%2FSaga"
        val scannedFolder = "content://provider/tree/primary%3AComics/document/primary%3AComics%2FSaga"

        assertEquals(documentIdentityKey(picked), documentIdentityKey(scanned))
        assertEquals(documentIdentityKey(pickedFolder), documentIdentityKey(scannedFolder))
        assertNotEquals(documentIdentityKey(picked), documentIdentityKey(pickedFolder))
        assertEquals("/storage/emulated/0/Comic.cbz", documentIdentityKey("/storage/emulated/0/Comic.cbz"))
    }

    @Test
    fun readableComicLocation_namesStorageLikePeopleDo() {
        val names = StorageNames(internalStorage = "Internal storage", sdCard = "SD card", downloads = "Downloads")
        fun location(type: RecentComicSourceType, path: String, provider: String? = null) =
            readableComicLocation(type, path, names) { provider }

        assertEquals(
            "Internal storage/Comics/Storm Beacon.cbz",
            location(RecentComicSourceType.LocalArchive, "/storage/emulated/0/Comics/Storm Beacon.cbz"),
        )
        assertEquals("SD card/Manga/One", location(RecentComicSourceType.LocalFolder, "/storage/1A2B-3C4D/Manga/One"))
        assertEquals("Internal storage/Comics", location(RecentComicSourceType.LocalFolder, "/storage/emulated/10/Comics"))
        assertEquals("/data/local/tmp/Comic.cbz", location(RecentComicSourceType.LocalArchive, "/data/local/tmp/Comic.cbz"))
        assertEquals(
            "Internal storage/Comics/Storm Beacon.cbz",
            location(
                RecentComicSourceType.DocumentArchive,
                "content://com.android.externalstorage.documents/document/primary%3AComics%2FStorm%20Beacon.cbz",
            ),
        )
        assertEquals(
            "SD card/Comics/Series/Issue 1.pdf",
            location(
                RecentComicSourceType.DocumentArchive,
                "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AComics" +
                    "/document/1A2B-3C4D%3AComics%2FSeries%2FIssue%201.pdf",
            ),
        )
        assertEquals(
            "Internal storage/Comics/Webtoon",
            location(
                RecentComicSourceType.DocumentFolder,
                "content://com.android.externalstorage.documents/tree/primary%3AComics%2FWebtoon",
            ),
        )
        assertEquals(
            "Internal storage/Download/Big Comic.pdf",
            location(
                RecentComicSourceType.DocumentArchive,
                "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FBig%20Comic.pdf",
            ),
        )
        assertEquals(
            "Downloads",
            location(RecentComicSourceType.DocumentArchive, "content://com.android.providers.downloads.documents/document/msf%3A1000000123"),
        )
        // Apps that keep their paths to themselves are named by the app, or by their authority.
        assertEquals(
            "Drive",
            location(RecentComicSourceType.DocumentArchive, "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D9", "Drive"),
        )
        assertEquals(
            "com.example.cloud",
            location(RecentComicSourceType.DocumentArchive, "content://com.example.cloud/document/42"),
        )
    }
}
