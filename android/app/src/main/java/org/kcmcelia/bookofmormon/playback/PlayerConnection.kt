package org.kcmcelia.bookofmormon.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val mediaId: Int? = null,
    val mediaIndex: Int = -1,
    val title: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/**
 * Thin wrapper around MediaController: owns the connection, publishes playback state to
 * Compose, and exposes the handful of actions the UI needs.
 */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    fun connect() {
        if (controller != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(listener)
            controller?.let { publish(it) }
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val player = controller
                if (player != null && player.isPlaying) publish(player)
                delay(500)
            }
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val duration = player.duration
        _state.value = PlayerState(
            connected = true,
            isPlaying = player.isPlaying,
            mediaId = item?.mediaId?.toIntOrNull(),
            mediaIndex = player.currentMediaItemIndex,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration > 0L) duration else 0L,
            speed = player.playbackParameters.speed,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
        )
    }

    /** Replaces the queue and starts at [startIndex] of [items]. */
    fun play(items: List<MediaItem>, startIndex: Int, startPositionMs: Long = 0L) {
        val player = controller ?: return
        player.setMediaItems(items, startIndex, startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.play()
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    /** Pass null to cancel. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
        }
    }

    fun sleepAtEndOfChapter() {
        sleepJob?.cancel()
        val player = controller ?: return
        val once = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                player.pause()
                player.removeListener(this)
            }
        }
        player.addListener(once)
    }

    fun release() {
        ticker?.cancel()
        sleepJob?.cancel()
        controller?.release()
        controller = null
    }
}
