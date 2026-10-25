package com.comicviewer

import org.junit.Assert.assertEquals
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
}
