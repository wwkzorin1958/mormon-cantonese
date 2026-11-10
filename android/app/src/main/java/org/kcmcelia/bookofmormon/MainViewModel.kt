package org.kcmcelia.bookofmormon

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kcmcelia.bookofmormon.data.Chapter
import org.kcmcelia.bookofmormon.data.ContentManifest
import org.kcmcelia.bookofmormon.data.ContentRepository
import org.kcmcelia.bookofmormon.data.DownloadRepository
import org.kcmcelia.bookofmormon.data.DownloadSummary
import org.kcmcelia.bookofmormon.data.ManifestLoader
import org.kcmcelia.bookofmormon.data.Progress
import org.kcmcelia.bookofmormon.data.ProgressStore
import org.kcmcelia.bookofmormon.playback.PlayerConnection
import org.kcmcelia.bookofmormon.playback.PlayerState
import org.kcmcelia.bookofmormon.util.estimatedEncodedBytes
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val manifest: ContentManifest = ManifestLoader.load(app)
    val content = ContentRepository(app, manifest)

    private val progressStore = ProgressStore(app)
    private val downloads = DownloadRepository(app)
    val player = PlayerConnection(app, viewModelScope)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _downloadedCount = MutableStateFlow(0)
    val downloadedCount: StateFlow<Int> = _downloadedCount.asStateFlow()

    /** False until the first disk scan finishes; the UI hides download prompts until then. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _downloadSummary = MutableStateFlow(DownloadSummary())
    val downloadSummary: StateFlow<DownloadSummary> = _downloadSummary.asStateFlow()

    private val _newVersion = MutableStateFlow<String?>(null)
    val newVersion: StateFlow<String?> = _newVersion.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val playerState: StateFlow<PlayerState> = player.state

    private var lastSavedAt: Long = 0L

    init {
        player.connect()

        viewModelScope.launch {
            progressStore.progress.collect { _progress.value = it }
        }

        viewModelScope.launch {
            while (true) {
                refreshDownloads()
                delay(2_000)
            }
        }

        viewModelScope.launch { checkForUpdates() }

        viewModelScope.launch {
            while (true) {
                savePosition()
                delay(5_000)
            }
        }
    }

    // ---------------------------------------------------------------- downloads

    fun estimatedDownloadSize(): Long = manifest.items.sumOf { estimatedEncodedBytes(it.durationSec) }

    fun downloadAll() {
        val missing = content.missing()
        if (missing.isEmpty()) return
        if (!hasSpace(missing.sumOf { estimatedEncodedBytes(it.durationSec) })) {
            _message.value = getApplication<Application>().getString(R.string.download_no_space)
            return
        }
        missing.forEach { downloads.enqueue(it, content.fileNameOf(it)) }
        viewModelScope.launch { refreshDownloads() }
    }

    fun downloadBook(bookIndex: Int) {
        val missing = content.missing(bookIndex)
        if (missing.isEmpty()) return
        if (!hasSpace(missing.sumOf { estimatedEncodedBytes(it.durationSec) })) {
            _message.value = getApplication<Application>().getString(R.string.download_no_space)
            return
        }
        missing.forEach { downloads.enqueue(it, content.fileNameOf(it)) }
        viewModelScope.launch { refreshDownloads() }
    }

    private fun hasSpace(neededBytes: Long): Boolean = runCatching {
        val dir = getApplication<Application>().getExternalFilesDir(null) ?: return false
        val stat = StatFs(dir.path)
        stat.availableBytes > neededBytes + 200L * 1024 * 1024
    }.getOrDefault(true)

    private suspend fun refreshDownloads() = withContext(Dispatchers.IO) {
        _downloadSummary.value = downloads.summary()
        _downloadedCount.value = content.downloadedCount()
        _ready.value = true
    }

    // ---------------------------------------------------------------- playback

    fun playChapter(chapter: Chapter, positionMs: Long = 0L) {
        val (items, chapters) = buildQueue()
        val start = chapters.indexOfFirst { it.index == chapter.index }
        if (start < 0) {
            _message.value = getApplication<Application>().getString(R.string.no_audio_url)
            return
        }
        player.play(items, start, positionMs)
    }

    fun resume() {
        val progress = _progress.value
        val chapter = progress.index?.let { manifest.chapterAt(it) } ?: manifest.items.firstOrNull()
        if (chapter == null) return
        playChapter(chapter, progress.positionMs)
    }

    /**
     * Every chapter that can actually play right now — downloaded, or streamable — in
     * canonical order. Skipping unplayable chapters keeps autoplay moving instead of
     * stopping on an undownloaded item.
     */
    private fun buildQueue(): Pair<List<MediaItem>, List<Chapter>> {
        val items = ArrayList<MediaItem>(manifest.items.size)
        val chapters = ArrayList<Chapter>(manifest.items.size)

        manifest.items.forEach { chapter ->
            val uri = content.playUri(chapter) ?: return@forEach
            chapters += chapter
            items += MediaItem.Builder()
                .setMediaId(chapter.index.toString())
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.displayTitle)
                        .setArtist(manifest.title)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
        return items to chapters
    }

    private suspend fun savePosition() {
        val state = player.state.value
        val index = state.mediaId ?: return
        if (state.positionMs <= 0L) return
        progressStore.save(index, state.positionMs)
        lastSavedAt = System.currentTimeMillis()
    }

    // ---------------------------------------------------------------- misc

    fun clearMessage() {
        _message.value = null
    }

    fun markIntroSeen() {
        viewModelScope.launch { progressStore.markIntroSeen() }
    }

    /** One request. Fails silently offline, changes nothing. */
    private suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(BuildConfig.LATEST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val latest = Json.parseToJsonElement(body).jsonObject["appVersion"]?.jsonPrimitive?.content
            // "1.0.0" != "1.0.1" would otherwise nag users who are already newer.
            if (!latest.isNullOrBlank() && isNewer(latest, BuildConfig.VERSION_NAME)) {
                _newVersion.value = latest
            }
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split(".").mapNotNull { it.trim().toIntOrNull() }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
