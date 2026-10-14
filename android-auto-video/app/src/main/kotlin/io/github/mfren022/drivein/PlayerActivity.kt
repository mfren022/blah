package io.github.mfren022.drivein

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView

/**
 * Full-screen video. Fits any screen shape (phone, tablet, wide or portrait car displays)
 * and works with touch, rotary controllers and keyboards. Only runs while parked: Android
 * Auto hides it when the car moves, and on Android Automotive OS [DrivingRestrictions]
 * pauses it. The audio can carry on through [PlaybackService].
 */
class PlayerActivity : Activity(), PlayerEngine.Listener, SurfaceHolder.Callback {

    private lateinit var videoFrame: AspectRatioFrameLayout
    private lateinit var controls: View
    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var buffering: ProgressBar
    private lateinit var playPause: ImageButton
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private lateinit var rewind: ImageButton
    private lateinit var fastForward: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var driving: DrivingRestrictions

    private var controlsVisible = true
    private var userSeeking = false
    private var pausedForDriving = false

    private val hideControls = Runnable { setControlsVisible(false) }
    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgress()
            Background.main.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        videoFrame = findViewById(R.id.video_frame)
        controls = findViewById(R.id.controls)
        title = findViewById(R.id.title)
        message = findViewById(R.id.message)
        buffering = findViewById(R.id.buffering)
        playPause = findViewById(R.id.play_pause)
        previous = findViewById(R.id.previous)
        next = findViewById(R.id.next)
        rewind = findViewById(R.id.rewind)
        fastForward = findViewById(R.id.fast_forward)
        seekBar = findViewById(R.id.seek_bar)
        positionText = findViewById(R.id.position)
        durationText = findViewById(R.id.duration)

        hideSystemBars()
        // Keep the controls clear of notches and the bars that slide in on swipe.
        controls.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }

        findViewById<SurfaceView>(R.id.surface).holder.addCallback(this)
        findViewById<View>(R.id.root).setOnClickListener { setControlsVisible(!controlsVisible) }
        controls.setOnClickListener { setControlsVisible(false) }
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        playPause.setOnClickListener { pausedForDriving = false; engine.togglePlayPause(); poke() }
        previous.setOnClickListener { engine.skipToPrevious(); poke() }
        next.setOnClickListener { engine.skipToNext(); poke() }
        rewind.setOnClickListener { engine.seekBy(-10_000); poke() }
        fastForward.setOnClickListener { engine.seekBy(30_000); poke() }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) positionText.text = formatDuration(progress.toLong())
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                userSeeking = true
                Background.main.removeCallbacks(hideControls)
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                userSeeking = false
                engine.seekTo(bar.progress.toLong())
                poke()
            }
        })

        driving = DrivingRestrictions(this) { restricted ->
            if (restricted) pauseForDriving() else hideMessageIfDriving()
        }
        driving.start()

        if (savedInstanceState == null) openFromIntent(intent)
        if (engine.currentItem == null) finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        engine.addListener(this)
        onVideoSizeChanged(engine.videoWidth, engine.videoHeight)
        onPlayerStateChanged()
        Background.main.post(progressTicker)
        poke()
    }

    override fun onStop() {
        engine.removeListener(this)
        Background.main.removeCallbacks(progressTicker)
        Background.main.removeCallbacks(hideControls)
        if (driving.restricted) pauseForDriving()
        super.onStop()
    }

    override fun onDestroy() {
        driving.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun surfaceCreated(holder: SurfaceHolder) = engine.attachSurface(holder.surface)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = engine.detachSurface(holder.surface)

    override fun onVideoSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) videoFrame.aspectRatio = width.toFloat() / height
    }

    override fun onPlayerStateChanged() {
        val item = engine.currentItem
        title.text = item?.title
        val playing = engine.isPlaying
        playPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPause.contentDescription = getString(if (playing) R.string.pause else R.string.play)
        buffering.visibility = if (engine.isBuffering) View.VISIBLE else View.GONE
        previous.isEnabled = item != null
        next.isEnabled = engine.hasNext
        next.alpha = if (engine.hasNext) 1f else 0.4f
        val seekable = !engine.isStopped && !engine.isBuffering
        rewind.isEnabled = seekable
        fastForward.isEnabled = seekable

        val error = engine.errorMessage
        when {
            error != null -> showMessage(error)
            pausedForDriving -> showMessage(getString(R.string.driving_paused))
            else -> message.visibility = View.GONE
        }

        if (playing) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setControlsVisible(true)
        }
        updateProgress()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!controlsVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    poke()
                    playPause.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    engine.seekBy(-10_000)
                    poke()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    engine.seekBy(30_000)
                    poke()
                    return true
                }
            }
        }
        if (keyCode != KeyEvent.KEYCODE_BACK) poke()
        return super.onKeyDown(keyCode, event)
    }

    private fun openFromIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (engine.currentItem?.uri == uri) return
        engine.play(listOf(VideoLibrary.itemForUri(this, uri)), 0)
    }

    private fun pauseForDriving() {
        if (driving.backgroundAudioSupported || !engine.isPlaying) return
        pausedForDriving = true
        engine.pause()
    }

    private fun hideMessageIfDriving() {
        if (!pausedForDriving) return
        pausedForDriving = false
        onPlayerStateChanged()
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    private fun updateProgress() {
        val duration = engine.duration
        val live = duration <= 0 && !engine.isStopped
        seekBar.visibility = if (duration > 0) View.VISIBLE else View.INVISIBLE
        durationText.text = if (live) getString(R.string.live) else formatDuration(duration)
        if (userSeeking) return
        val position = engine.position
        seekBar.max = duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        seekBar.progress = position.coerceIn(0, seekBar.max.toLong()).toInt()
        positionText.text = formatDuration(position)
    }

    /** Shows the controls and restarts the timer that hides them. */
    private fun poke() {
        setControlsVisible(true)
        Background.main.removeCallbacks(hideControls)
        if (engine.isPlaying) Background.main.postDelayed(hideControls, HIDE_CONTROLS_AFTER_MS)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (!visible && !engine.isPlaying) return
        if (visible == controlsVisible && controls.visibility == (if (visible) View.VISIBLE else View.INVISIBLE)) return
        controlsVisible = visible
        controls.animate().alpha(if (visible) 1f else 0f).setDuration(200).withEndAction {
            controls.visibility = if (controlsVisible) View.VISIBLE else View.INVISIBLE
        }.start()
        if (visible) {
            controls.visibility = View.VISIBLE
        } else {
            Background.main.removeCallbacks(hideControls)
        }
    }

    private fun hideSystemBars() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val HIDE_CONTROLS_AFTER_MS = 4_000L
    }
}
