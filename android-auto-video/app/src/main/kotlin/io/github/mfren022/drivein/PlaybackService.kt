package io.github.mfren022.drivein

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.os.Bundle
import android.os.Process
import android.service.media.MediaBrowserService
import android.util.Log

/**
 * Two jobs:
 *  - Media browser for Android Auto and car media centres. Android Auto lists the library
 *    and plays any video as audio-only, which is allowed while driving.
 *  - Foreground service with a media notification, so the audio keeps playing when the
 *    video screen is hidden (the car started moving, or the user left the app).
 */
class PlaybackService : MediaBrowserService(), PlayerEngine.Listener {

    private lateinit var notificationManager: NotificationManager
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            },
        )
        sessionToken = engine.session.sessionToken
        engine.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.togglePlayPause()
            ACTION_REWIND -> engine.seekBy(-10_000)
            ACTION_FORWARD -> engine.seekBy(30_000)
            ACTION_STOP -> engine.stop()
            // Sent by startForegroundService(), which has to be answered with startForeground().
            null -> goForeground()
        }
        updateNotification()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!engine.isPlaying) {
            engine.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        engine.removeListener(this)
        super.onDestroy()
    }

    override fun onPlayerStateChanged() = updateNotification()

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        // Anyone may connect to control playback, but only trusted callers may list the
        // user's own videos.
        if (!isTrustedCaller(clientPackageName, clientUid)) return BrowserRoot(EMPTY_ROOT, null)
        val extras = Bundle().apply {
            putInt(EXTRA_CONTENT_STYLE_BROWSABLE, CONTENT_STYLE_GRID)
            putInt(EXTRA_CONTENT_STYLE_PLAYABLE, CONTENT_STYLE_LIST)
        }
        return BrowserRoot(VideoLibrary.ROOT, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        when (parentId) {
            EMPTY_ROOT -> result.sendResult(mutableListOf())
            VideoLibrary.ROOT -> result.sendResult(rootItems())
            else -> {
                result.detach()
                Background.load({ playableItems(VideoLibrary.children(this, parentId)) }) { result.sendResult(it) }
            }
        }
    }

    private fun rootItems(): MutableList<MediaItem> {
        val device = MediaDescription.Builder()
            .setMediaId(VideoLibrary.CATEGORY_DEVICE)
            .setTitle(getString(R.string.tab_device))
            .setSubtitle(
                getString(
                    if (VideoLibrary.hasPermission(this)) R.string.browse_device_subtitle else R.string.browse_device_locked,
                ),
            )
            .setIconUri(VideoLibrary.resourceUri(this, R.drawable.ic_phone))
            .build()
        val samples = MediaDescription.Builder()
            .setMediaId(VideoLibrary.CATEGORY_SAMPLES)
            .setTitle(getString(R.string.tab_samples))
            .setSubtitle(getString(R.string.browse_samples_subtitle))
            .setIconUri(VideoLibrary.resourceUri(this, R.drawable.ic_movie))
            .build()
        return mutableListOf(MediaItem(device, MediaItem.FLAG_BROWSABLE), MediaItem(samples, MediaItem.FLAG_BROWSABLE))
    }

    private fun playableItems(items: List<VideoItem>): MutableList<MediaItem> =
        items.mapTo(mutableListOf()) { MediaItem(it.toDescription(this), MediaItem.FLAG_PLAYABLE) }

    private fun isTrustedCaller(packageName: String, uid: Int) =
        uid == Process.myUid() || uid == Process.SYSTEM_UID || packageName in TRUSTED_PACKAGES

    private fun goForeground() {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            inForeground = true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't enter the foreground", e)
        }
    }

    private fun updateNotification() {
        val stopped = engine.currentItem == null || (engine.isStopped && engine.errorMessage == null)
        when {
            stopped -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(NOTIFICATION_ID)
                inForeground = false
                stopSelf()
            }
            engine.isPlaying -> if (inForeground) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            } else {
                goForeground()
            }
            else -> {
                // Paused: keep the controls, but let the system reclaim the app and let the
                // user swipe the notification away.
                if (inForeground) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    inForeground = false
                }
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    private fun buildNotification(): Notification {
        val item = engine.currentItem
        val playing = engine.isPlaying
        val controller = engine.session.controller
        val playPause = if (playing) {
            action(R.drawable.ic_pause, R.string.pause, ACTION_TOGGLE)
        } else {
            action(R.drawable.ic_play, R.string.play, ACTION_TOGGLE)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item?.title ?: getString(R.string.app_name))
            .setContentText(engine.errorMessage ?: item?.subtitle)
            .setLargeIcon(controller.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART))
            .setContentIntent(controller.sessionActivity)
            .setDeleteIntent(servicePendingIntent(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(action(R.drawable.ic_rewind, R.string.rewind, ACTION_REWIND))
            .addAction(playPause)
            .addAction(action(R.drawable.ic_fast_forward, R.string.fast_forward, ACTION_FORWARD))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(engine.session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun action(icon: Int, title: Int, action: String) = Notification.Action.Builder(
        Icon.createWithResource(this, icon), getString(title), servicePendingIntent(action),
    ).build()

    private fun servicePendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_TOGGLE = "io.github.mfren022.drivein.TOGGLE"
        private const val ACTION_REWIND = "io.github.mfren022.drivein.REWIND"
        private const val ACTION_FORWARD = "io.github.mfren022.drivein.FORWARD"
        private const val ACTION_STOP = "io.github.mfren022.drivein.STOP"

        private const val EMPTY_ROOT = "empty"

        // Browse hints understood by Android Auto and Android Automotive OS.
        private const val EXTRA_CONTENT_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val EXTRA_CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST = 1
        private const val CONTENT_STYLE_GRID = 2

        private val TRUSTED_PACKAGES = setOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.carassistant", // Assistant on Android Automotive OS
            "com.google.android.googlequicksearchbox", // Assistant / Gemini
            "com.android.car.media", // Android Automotive OS media centre
            "com.android.systemui", // Media controls in quick settings
        )
    }
}
