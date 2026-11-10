package org.kcmcelia.bookofmormon.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Resolves each chapter to a local file or a remote URL.
 *
 * Chapters are stored as "<file stem>.m4a" under the app's external files dir, so an
 * uninstall removes them and no storage permission is ever needed.
 */
class ContentRepository(
    private val context: Context,
    val manifest: ContentManifest,
) {
    private val audioDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "audio").apply {
            if (!exists()) mkdirs()
        }

    fun fileNameOf(chapter: Chapter): String = chapter.file.substringBeforeLast('.') + ".m4a"

    fun localFile(chapter: Chapter): File = File(audioDir, fileNameOf(chapter))

    fun isDownloaded(chapter: Chapter): Boolean {
        val file = localFile(chapter)
        return file.exists() && file.length() > 0
    }

    /** Local file when present, otherwise the remote URL, otherwise null. */
    fun playUri(chapter: Chapter): Uri? = when {
        isDownloaded(chapter) -> Uri.fromFile(localFile(chapter))
        !chapter.audioUrl.isNullOrBlank() -> Uri.parse(chapter.audioUrl)
        else -> null
    }

    fun downloadedCount(): Int = manifest.items.count { isDownloaded(it) }

    fun downloadedBytes(): Long = manifest.items.sumOf { if (isDownloaded(it)) localFile(it).length() else 0L }

    fun missing(bookIndex: Int? = null): List<Chapter> = manifest.items.filter {
        (bookIndex == null || it.bookIndex == bookIndex) && !isDownloaded(it)
    }
}
