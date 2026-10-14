package io.github.mfren022.drivein

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Surface

/**
 * Owns the [MediaPlayer] and the [MediaSession]. The phone UI draws video into it through
 * [attachSurface]; Android Auto, the notification and media buttons drive it through the
 * session. With no surface attached it keeps playing the audio, which is what lets a video
 * carry on as audio-only when the car screen hides it.
 */
class PlayerEngine(private val context: Context) {

    interface Listener {
        fun onPlayerStateChanged() {}
        fun onVideoSizeChanged(width: Int, height: Int) {}
    }

    val session = MediaSession(context, "DriveIn")

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val resumePositions = context.getSharedPreferences("resume_positions", Context.MODE_PRIVATE)
    private val listeners = linkedSetOf<Listener>()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(::onAudioFocusChange, Background.main)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Headphones unplugged or Bluetooth dropped: don't blast audio out of the speaker.
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val rewindAction = PlaybackState.CustomAction.Builder(
        ACTION_REWIND_10, context.getString(R.string.rewind), R.drawable.ic_rewind,
    ).build()
    private val forwardAction = PlaybackState.CustomAction.Builder(
        ACTION_FORWARD_30, context.getString(R.string.fast_forward), R.drawable.ic_fast_forward,
    ).build()

    private var player: MediaPlayer? = null
    private var prepared = false
    private var playWhenReady = false
    private var buffering = false
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var noisyReceiverRegistered = false
    private var surface: Surface? = null
    private var artwork: Bitmap? = null

    var queue: List<VideoItem> = emptyList()
        private set
    var queueIndex = -1
        private set
    val currentItem: VideoItem? get() = queue.getOrNull(queueIndex)

    var videoWidth = 0
        private set
    var videoHeight = 0
        private set
    var errorMessage: String? = null
        private set

    /** True while the user wants playback running, including while it buffers. */
    val isPlaying get() = playWhenReady
    val isBuffering get() = playWhenReady && player != null && (!prepared || buffering)
    val isStopped get() = player == null
    val hasNext get() = queueIndex + 1 < queue.size
    val hasPrevious get() = queueIndex > 0

    val position: Long
        get() = if (prepared) runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0 else 0

    /** Duration in ms, or 0 when unknown or live. */
    val duration: Long
        get() {
            val known = if (prepared) runCatching { player?.duration?.toLong() }.getOrNull() ?: 0 else 0
            return if (known > 0) known else currentItem?.durationMs ?: 0
        }

    init {
        session.setCallback(SessionCallback(), Background.main)
        session.setSessionActivity(
            PendingIntent.getActivity(
                context, 0,
                Intent(context, PlayerActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        publishState()
    }

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun play(items: List<VideoItem>, startIndex: Int) {
        if (items.isEmpty()) return
        queue = items
        session.setQueue(items.mapIndexed { i, item -> MediaSession.QueueItem(item.toDescription(context), i.toLong()) })
        load(startIndex.coerceIn(items.indices), autoPlay = true)
    }

    fun resume() {
        if (currentItem == null) return
        if (player == null) {
            // Stopped or failed earlier: open the video again, picking up where it left off.
            load(queueIndex, autoPlay = true)
            return
        }
        if (!requestAudioFocus()) return
        playWhenReady = true
        if (prepared) runCatching { player?.start() }
        publishState()
        startPlaybackService()
    }

    fun pause() {
        resumeOnFocusGain = false
        if (!playWhenReady) return
        playWhenReady = false
        if (prepared) runCatching { player?.pause() }
        saveResumePosition()
        publishState()
    }

    fun togglePlayPause() = if (playWhenReady) pause() else resume()

    fun stop() {
        saveResumePosition()
        resumeOnFocusGain = false
        playWhenReady = false
        releasePlayer()
        abandonAudioFocus()
        session.isActive = false
        publishState()
    }

    fun seekTo(positionMs: Long) {
        if (!prepared) return
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        runCatching { player?.seekTo(target, MediaPlayer.SEEK_CLOSEST_SYNC) }
        publishState()
    }

    fun seekBy(deltaMs: Long) = seekTo(position + deltaMs)

    fun skipToNext() {
        if (hasNext) load(queueIndex + 1, autoPlay = true)
    }

    fun skipToPrevious() {
        when {
            position > 3_000 || !hasPrevious -> seekTo(0)
            else -> load(queueIndex - 1, autoPlay = true)
        }
    }

    fun attachSurface(newSurface: Surface) {
        surface = newSurface
        runCatching { player?.setSurface(newSurface) }
    }

    fun detachSurface(oldSurface: Surface) {
        if (surface != oldSurface) return
        surface = null
        // Audio keeps going without a surface.
        runCatching { player?.setSurface(null) }
    }

    private fun load(index: Int, autoPlay: Boolean) {
        saveResumePosition()
        releasePlayer()
        queueIndex = index
        val item = queue[index]
        errorMessage = null
        artwork = null
        videoWidth = 0
        videoHeight = 0
        notifyVideoSize()
        playWhenReady = autoPlay && requestAudioFocus()

        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setAudioAttributes(audioAttributes)
        mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        mediaPlayer.setOnPreparedListener { onPrepared(it, item) }
        mediaPlayer.setOnCompletionListener { onCompletion(item) }
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Playback error $what/$extra for ${item.uri}")
            fail(context.getString(R.string.error_playback, what, extra))
            true
        }
        mediaPlayer.setOnInfoListener { _, what, _ -> onInfo(what) }
        mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
            videoWidth = width
            videoHeight = height
            notifyVideoSize()
        }
        mediaPlayer.setOnSeekCompleteListener { publishState() }
        surface?.let { mediaPlayer.setSurface(it) }
        try {
            mediaPlayer.setDataSource(context, item.uri)
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Can't open ${item.uri}", e)
            fail(context.getString(R.string.error_cannot_open))
            return
        }

        session.isActive = true
        publishMetadata()
        publishState()
        if (playWhenReady) startPlaybackService()

        Thumbnails.load(context, item) { bitmap ->
            if (currentItem?.mediaId == item.mediaId && bitmap != null) {
                artwork = bitmap
                publishMetadata()
            }
        }
    }

    private fun onPrepared(mediaPlayer: MediaPlayer, item: VideoItem) {
        if (mediaPlayer != player) return
        prepared = true
        val resumeAt = resumePositions.getLong(item.mediaId, 0)
        if (resumeAt > 5_000 && (mediaPlayer.duration <= 0 || resumeAt < mediaPlayer.duration - 10_000)) {
            mediaPlayer.seekTo(resumeAt, MediaPlayer.SEEK_CLOSEST_SYNC)
        }
        if (playWhenReady) mediaPlayer.start()
        publishMetadata()
        publishState()
    }

    private fun onCompletion(item: VideoItem) {
        resumePositions.edit().remove(item.mediaId).apply()
        if (hasNext) {
            load(queueIndex + 1, autoPlay = true)
        } else {
            playWhenReady = false
            publishState()
        }
    }

    private fun onInfo(what: Int): Boolean {
        when (what) {
            MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
            MediaPlayer.MEDIA_INFO_BUFFERING_END -> buffering = false
            else -> return false
        }
        publishState()
        return true
    }

    private fun fail(message: String) {
        errorMessage = message
        playWhenReady = false
        releasePlayer()
        publishState()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        prepared = false
        buffering = false
    }

    private fun saveResumePosition() {
        val item = currentItem ?: return
        if (!prepared) return
        val pos = position
        val total = duration
        resumePositions.edit().apply {
            if (pos > 5_000 && (total <= 0 || pos < total - 10_000)) putLong(item.mediaId, pos) else remove(item.mediaId)
        }.apply()
    }

    private fun requestAudioFocus(): Boolean {
        if (!hasAudioFocus) {
            hasAudioFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
        }
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                player?.setVolume(1f, 1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // A navigation prompt or phone call: pause, then pick up again afterwards.
                val wasPlaying = playWhenReady
                pause()
                resumeOnFocusGain = wasPlaying
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f, 0.2f)
        }
    }

    private fun startPlaybackService() {
        try {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses this from the background in some cases. Playback still works
            // while Android Auto or the UI keeps the app alive; the notification just won't show.
            Log.w(TAG, "Couldn't start the playback service", e)
        }
    }

    private fun publishMetadata() {
        val item = currentItem ?: return
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, item.mediaId)
            .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, item.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, item.subtitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, item.subtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        artwork?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
        session.setMetadata(builder.build())
    }

    private fun publishState() {
        val state = when {
            errorMessage != null -> PlaybackState.STATE_ERROR
            currentItem == null -> PlaybackState.STATE_NONE
            player == null -> PlaybackState.STATE_STOPPED
            isBuffering -> PlaybackState.STATE_BUFFERING
            playWhenReady -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH or
            PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM
        if (prepared) {
            actions = actions or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (hasNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT

        val builder = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, if (state == PlaybackState.STATE_PLAYING) 1f else 0f, SystemClock.elapsedRealtime())
            .setActiveQueueItemId(queueIndex.toLong())
        if (prepared) builder.addCustomAction(rewindAction).addCustomAction(forwardAction)
        errorMessage?.let { builder.setErrorMessage(it) }
        session.setPlaybackState(builder.build())

        updateNoisyReceiver()
        listeners.toList().forEach { it.onPlayerStateChanged() }
    }

    private fun notifyVideoSize() {
        listeners.toList().forEach { it.onVideoSizeChanged(videoWidth, videoHeight) }
    }

    private fun updateNoisyReceiver() {
        if (playWhenReady && !noisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        } else if (!playWhenReady && noisyReceiverRegistered) {
            context.unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
    }

    private fun showError(message: String) {
        errorMessage = message
        publishState()
        errorMessage = null
    }

    /** Commands from Android Auto, the notification, Bluetooth buttons and voice. */
    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() = resume()
        override fun onPause() = pause()
        override fun onStop() = stop()
        override fun onSeekTo(pos: Long) = seekTo(pos)
        override fun onSkipToNext() = skipToNext()
        override fun onSkipToPrevious() = skipToPrevious()
        override fun onRewind() = seekBy(-10_000)
        override fun onFastForward() = seekBy(30_000)

        override fun onSkipToQueueItem(id: Long) {
            if (id >= 0 && id < queue.size) load(id.toInt(), autoPlay = true)
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            when (action) {
                ACTION_REWIND_10 -> seekBy(-10_000)
                ACTION_FORWARD_30 -> seekBy(30_000)
            }
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            Background.load({ VideoLibrary.find(context, mediaId) }) { found ->
                if (found != null) {
                    play(found.first, found.second)
                } else if (!VideoLibrary.hasPermission(context) && mediaId.startsWith("device:")) {
                    showError(context.getString(R.string.error_no_permission))
                } else {
                    showError(context.getString(R.string.error_not_found))
                }
            }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query.isNullOrBlank()) {
                // "Play Drive-In" with nothing specific: carry on, or start the samples.
                if (currentItem != null) resume() else onPlayFromMediaId("sample:BigBuckBunny", null)
                return
            }
            Background.load({ VideoLibrary.search(context, query) }) { results ->
                if (results.isNotEmpty()) play(results, 0) else showError(context.getString(R.string.error_not_found))
            }
        }
    }

    companion object {
        private const val TAG = "PlayerEngine"
        private const val ACTION_REWIND_10 = "io.github.mfren022.drivein.REWIND_10"
        private const val ACTION_FORWARD_30 = "io.github.mfren022.drivein.FORWARD_30"
    }
}
