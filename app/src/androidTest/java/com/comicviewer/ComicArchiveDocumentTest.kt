package com.comicviewer

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicArchiveDocumentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdUris = mutableListOf<Uri>()

    @After
    fun cleanUp() = runBlocking {
        clearArchiveCache(context)
        createdUris.forEach { uri -> context.contentResolver.delete(uri, null, null) }
    }

    @Test
    fun loadArchiveComic_readsPickedDocumentInPlaceAndStreamsPages() = runBlocking {
        val uri = insertDownload(
            "live-read-test-${System.nanoTime()}.cbz",
            linkedMapOf(
                "Issue/010.jpg" to jpegPage(width = 300, height = 450),
                "Issue/001.jpg" to jpegPage(width = 100, height = 150),
                "Issue/notes.txt" to "not a page".encodeToByteArray(),
                "Issue/002.jpg" to jpegPage(width = 200, height = 300),
            ),
        )

        val book = loadArchiveComic(context, uri)

        assertEquals(listOf("001.jpg", "002.jpg", "010.jpg"), book.pages.map { it.displayName })
        assertTrue(book.pages.all { page -> page.imageUri.scheme == COMIC_ARCHIVE_PAGE_SCHEME })
        assertFalse(File(context.cacheDir, "archive_copies").exists())

        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(book.pages.last().imageUri).build(),
        )
        assertTrue(result is SuccessResult)
        assertEquals(300, (result as SuccessResult).image.width)
    }

    private fun insertDownload(displayName: String, entries: Map<String, ByteArray>): Uri {
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        )
        createdUris += uri
        requireNotNull(resolver.openOutputStream(uri)).use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun jpegPage(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
