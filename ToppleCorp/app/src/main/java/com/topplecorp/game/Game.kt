package com.topplecorp.game

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.topplecorp.game.audio.Sfx
import com.topplecorp.game.audio.Sound
import com.topplecorp.game.data.LevelRepository
import com.topplecorp.game.data.SaveData
import com.topplecorp.game.scene.MenuScene
import com.topplecorp.game.scene.Scene
import com.topplecorp.game.ui.Ease
import com.topplecorp.game.ui.Touch
import com.topplecorp.game.ui.Ui

/**
 * Root of the game: owns services (save, sound, levels), the active scene and scene transitions.
 * All drawing happens in a low-res "virtual" pixel buffer of size [vw] x [vh].
 */
class Game(val context: Context) {
    var vw = 270; private set
    var vh = 585; private set

    val save = SaveData(context)
    val sfx = Sfx(context)
    val levels = LevelRepository(context)

    /** Set by the Activity; closes the app. */
    var exitHandler: () -> Unit = {}

    private var scene: Scene? = null
    private var next: (() -> Scene)? = null
    private var transition = 0f // 0 = none, (0..1) covering, (1..2) revealing
    private var time = 0f

    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        sfx.enabled = save.soundOn
        sfx.volume = save.volume
        Thread { sfx.load() }.start()
        scene = MenuScene(this).also { it.layout(); it.enter() }
    }

    fun resize(w: Int, h: Int) {
        if (w == vw && h == vh) return
        vw = w; vh = h
        scene?.layout()
    }

    fun play(s: Sound, vol: Float = 1f, pitch: Float = 1f) = sfx.play(s, vol, pitch)

    fun vibrate(ms: Long, strength: Int = 120) {
        if (!save.vibration) return
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, strength.coerceIn(1, 255)))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    /** Switches scene with a pixel wipe transition. */
    fun goTo(factory: () -> Scene) {
        if (next != null) return
        next = factory
        transition = 0.0001f
        play(Sound.WHOOSH, 0.5f)
    }

    val isTransitioning get() = transition > 0f

    @Volatile private var backRequested = false

    /** May be called from the UI thread; handled on the game thread in [update]. */
    fun back() {
        backRequested = true
    }

    fun touch(t: Touch) {
        if (isTransitioning) return
        scene?.touch(t)
    }

    fun update(dt: Float) {
        time += dt
        if (backRequested) {
            backRequested = false
            if (!isTransitioning) scene?.onBack()
        }
        if (transition > 0f) {
            val before = transition
            transition += dt / TRANSITION_TIME
            if (before < 1f && transition >= 1f) {
                next?.let { f ->
                    scene?.exit()
                    scene = f().also { it.layout(); it.enter() }
                }
                next = null
            }
            if (transition >= 2f) transition = 0f
        }
        scene?.update(dt)
    }

    /** World pass into the low-res pixel buffer. */
    fun draw(c: Canvas) {
        scene?.draw(c)
    }

    /** UI pass at screen resolution (canvas already scaled to virtual units). */
    fun drawUi(c: Canvas) {
        scene?.drawUi(c)
        if (transition > 0f) {
            val t = transition
            val cover = if (t < 1f) Ease.inOutCubic(t) else 1f - Ease.inOutCubic(t - 1f)
            Ui.dim(c, vw.toFloat() + 2f, vh.toFloat() + 2f, cover)
        }
    }

    fun destroy() = sfx.release()

    companion object {
        const val TRANSITION_TIME = 0.32f
    }
}
