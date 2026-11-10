package org.kcmcelia.bookofmormon.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.kcmcelia.bookofmormon.R

data class DownloadSummary(
    val active: Int = 0,
    val failed: Int = 0,
    val done: Int = 0,
    val bytes: Long = 0L,
    val total: Long = 0L,
) {
    val fraction: Float
        get() = if (total > 0L) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Uses the platform DownloadManager: queueing, retry, resume, Wi-Fi-only constraints and
 * progress notifications are system behaviour, so there is nothing here to maintain.
 */
class DownloadRepository(private val context: Context) {
    private val manager: DownloadManager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("download_ids", Context.MODE_PRIVATE)

    fun enqueue(chapter: Chapter, fileName: String) {
        val url = chapter.audioUrl ?: return
        if (prefs.contains(fileName)) return

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(chapter.displayTitle)
            .setDescription(context.getString(R.string.app_name))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, "audio/$fileName")
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)

        runCatching {
            prefs.edit().putLong(fileName, manager.enqueue(request)).apply()
        }
    }

    fun summary(): DownloadSummary {
        val ids = prefs.all.values.filterIsInstance<Long>().toLongArray()
        if (ids.isEmpty()) return DownloadSummary()

        var active = 0
        var failed = 0
        var done = 0
        var bytes = 0L
        var total = 0L

        manager.query(DownloadManager.Query().setFilterById(*ids))?.use { cursor ->
            while (cursor.moveToNext()) {
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING,
                    -> active++

                    DownloadManager.STATUS_SUCCESSFUL -> done++
                    DownloadManager.STATUS_FAILED -> failed++
                }
                bytes += cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                total += cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
            }
        }
        return DownloadSummary(active, failed, done, bytes, total)
    }
}
