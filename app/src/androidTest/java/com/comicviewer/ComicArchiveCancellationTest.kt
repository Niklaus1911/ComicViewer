package com.comicviewer

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicArchiveCancellationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDir = File(context.cacheDir, "archive-cancellation-test")

    @After
    fun cleanUp() {
        runBlocking { closeComicArchives() }
        testDir.deleteRecursively()
    }

    @Test
    fun cancelledPageLoads_doNotBreakLaterPagesOfTheSameArchive() = runBlocking {
        val book = loadLocalArchiveComic(context, writeArchiveWithLargePages(pageCount = 4))
        val imageLoader = SingletonImageLoader.get(context)

        // Cancel page loads at different moments, so some are cut off while their page is decoding,
        // as happens when the reader drops preloads or swaps a page for a sharper copy.
        repeat(75) { attempt ->
            val page = book.pages[attempt % 3]
            val load = imageLoader.enqueue(uncachedRequest(page.imageUri.toString()))
            delay(2L + (attempt % 25) * 8L)
            load.dispose()
        }

        val result = imageLoader.execute(uncachedRequest(book.pages.last().imageUri.toString()))
        assertTrue("Later page failed: $result", result is SuccessResult)
    }

    @Test
    fun archiveClosedUnderneathTheReader_isReopenedForTheNextPage() = runBlocking {
        val archive = writeArchiveWithLargePages(pageCount = 2)
        val book = loadLocalArchiveComic(context, archive)
        ComicFileSessions.acquire(context, ComicArchiveLocation.LocalFile(archive.absolutePath)).use { lease ->
            (lease.source as OpenedComicFile.Zip).archive.close()
        }

        val result = SingletonImageLoader.get(context).execute(uncachedRequest(book.pages.last().imageUri.toString()))

        assertTrue("Page after a closed archive failed: $result", result is SuccessResult)
    }

    private fun uncachedRequest(data: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()

    /** Noise pages decode slowly, which leaves room to cancel a load in the middle of decoding. */
    private fun writeArchiveWithLargePages(pageCount: Int): File {
        testDir.mkdirs()
        val archive = File(testDir, "Large.cbz")
        val random = Random(42)
        ZipOutputStream(archive.outputStream()).use { zip ->
            repeat(pageCount) { index ->
                val bitmap = Bitmap.createBitmap(2400, 3600, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(bitmap.width * bitmap.height) { random.nextInt() or (0xFF shl 24) }
                bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val jpeg = ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                    output.toByteArray()
                }
                bitmap.recycle()
                zip.putNextEntry(ZipEntry("%03d.jpg".format(index + 1)))
                zip.write(jpeg)
                zip.closeEntry()
            }
        }
        return archive
    }
}
