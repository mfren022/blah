package com.topplecorp.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Every sound effect in the game. All are synthesized at startup; there are no audio assets. */
enum class Sound {
    CLICK, BACK, SELECT, AIM, DENY, WHOOSH,
    HAMMER, CLANK, DRILL, DYNAMITE, LASER, WRECK,
    BREAK_GLASS, BREAK_WOOD, BREAK_STONE, BREAK_METAL, BREAK_PLASTIC,
    THUD, CRUSH, SHOCK, EXPLOSION, SPLASH,
    COIN, GOLD, STAR, WIN, LOSE, BONUS, PURCHASE,
}

/**
 * Procedural sound synthesizer. Each effect is rendered to a small 16-bit WAV in the cache dir
 * once (keyed by a version number), then played through a SoundPool.
 */
class Sfx(private val context: Context) {
    private val rate = 22050
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = HashMap<Sound, Int>()
    private val lastPlayed = HashMap<Sound, Long>()

    var enabled = true
    var volume = 0.8f

    fun load() {
        val dir = File(context.cacheDir, "sfx_v$VERSION").apply { mkdirs() }
        for (s in Sound.entries) {
            val f = File(dir, "${s.name.lowercase()}.wav")
            if (!f.exists() || f.length() < 64) writeWav(f, synth(s))
            ids[s] = pool.load(f.absolutePath, 1)
        }
    }

    fun play(s: Sound, vol: Float = 1f, pitch: Float = 1f) {
        if (!enabled || volume <= 0f) return
        val now = System.nanoTime()
        // Avoid stacking the same sound in the same frame (chain reactions).
        if (now - (lastPlayed[s] ?: 0L) < 35_000_000L) return
        lastPlayed[s] = now
        val id = ids[s] ?: return
        val v = (vol * volume).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    // ------------------------------------------------------------------ synthesis

    private fun buf(seconds: Float) = FloatArray((seconds * rate).toInt())

    private fun env(t: Float, attack: Float, decay: Float): Float =
        if (t < attack) t / attack else exp(-(t - attack) / decay)

    private fun square(phase: Double) = if ((phase % 1.0) < 0.5) 1f else -1f
    private fun tri(phase: Double): Float { val p = phase % 1.0; return (if (p < 0.5) 4 * p - 1 else 3 - 4 * p).toFloat() }

    /** Tone sweep from f0 to f1 with a waveform. */
    private fun tone(
        out: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, amp: Float,
        wave: Int = 0, attack: Float = 0.005f, decay: Float = dur / 3f,
    ) {
        var phase = 0.0
        val s0 = (start * rate).toInt()
        val n = (dur * rate).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i.toFloat() / rate
            val f = f0 + (f1 - f0) * (i.toFloat() / n)
            phase += f / rate
            val v = when (wave) {
                0 -> sin(2 * PI * phase).toFloat()
                1 -> square(phase) * 0.5f
                else -> tri(phase)
            }
            out[idx] += v * amp * env(t, attack, decay)
        }
    }

    /** Filtered noise burst. [lp] in 0..1, lower = darker. */
    private fun noise(out: FloatArray, start: Float, dur: Float, amp: Float, lp: Float, decay: Float = dur / 3f, seed: Int = 1) {
        val r = Random(seed)
        var y = 0f
        val s0 = (start * rate).toInt()
        val n = (dur * rate).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i.toFloat() / rate
            y += lp * ((r.nextFloat() * 2 - 1) - y)
            out[idx] += y * amp * env(t, 0.002f, decay)
        }
    }

    private fun synth(s: Sound): FloatArray = when (s) {
        Sound.CLICK -> buf(0.06f).also { tone(it, 0f, 0.06f, 1400f, 900f, 0.5f, 1, decay = 0.02f) }
        Sound.BACK -> buf(0.08f).also { tone(it, 0f, 0.08f, 900f, 500f, 0.45f, 1, decay = 0.03f) }
        Sound.SELECT -> buf(0.12f).also {
            tone(it, 0f, 0.05f, 700f, 700f, 0.35f, 1, decay = 0.03f)
            tone(it, 0.05f, 0.07f, 1050f, 1050f, 0.35f, 1, decay = 0.03f)
        }
        Sound.AIM -> buf(0.07f).also { tone(it, 0f, 0.07f, 600f, 1200f, 0.3f, 2, decay = 0.04f) }
        Sound.DENY -> buf(0.18f).also {
            tone(it, 0f, 0.08f, 220f, 200f, 0.4f, 1, decay = 0.06f)
            tone(it, 0.09f, 0.09f, 180f, 160f, 0.4f, 1, decay = 0.06f)
        }
        Sound.WHOOSH -> buf(0.25f).also { noise(it, 0f, 0.25f, 0.5f, 0.15f, decay = 0.1f, seed = 3) }
        Sound.HAMMER -> buf(0.18f).also {
            noise(it, 0f, 0.05f, 0.8f, 0.6f, decay = 0.015f, seed = 4)
            tone(it, 0f, 0.15f, 180f, 90f, 0.7f, 0, decay = 0.05f)
        }
        Sound.CLANK -> buf(0.35f).also {
            tone(it, 0f, 0.35f, 1320f, 1300f, 0.35f, 0, decay = 0.09f)
            tone(it, 0f, 0.35f, 1870f, 1850f, 0.25f, 0, decay = 0.07f)
            noise(it, 0f, 0.03f, 0.6f, 0.8f, decay = 0.01f, seed = 5)
        }
        Sound.DRILL -> buf(0.45f).also {
            val r = Random(6)
            for (i in it.indices) {
                val t = i.toFloat() / rate
                val f = 95f + 30f * sin(t * 40f)
                it[i] += square((t * f).toDouble()) * 0.35f * env(t, 0.02f, 0.25f) + (r.nextFloat() - 0.5f) * 0.15f * env(t, 0.02f, 0.2f)
            }
        }
        Sound.DYNAMITE -> buf(0.7f).also {
            noise(it, 0f, 0.25f, 0.15f, 0.9f, decay = 0.2f, seed = 7) // fuse
            noise(it, 0.2f, 0.5f, 1.0f, 0.12f, decay = 0.16f, seed = 8)
            tone(it, 0.2f, 0.4f, 90f, 35f, 0.8f, 0, decay = 0.15f)
        }
        Sound.LASER -> buf(0.4f).also {
            tone(it, 0f, 0.4f, 1800f, 300f, 0.35f, 1, decay = 0.2f)
            tone(it, 0f, 0.4f, 1805f, 305f, 0.25f, 2, decay = 0.2f)
        }
        Sound.WRECK -> buf(0.6f).also {
            noise(it, 0f, 0.3f, 0.4f, 0.08f, decay = 0.2f, seed = 9) // swing
            noise(it, 0.25f, 0.3f, 0.9f, 0.3f, decay = 0.08f, seed = 10)
            tone(it, 0.25f, 0.3f, 120f, 50f, 0.8f, 0, decay = 0.1f)
        }
        Sound.BREAK_GLASS -> buf(0.5f).also {
            noise(it, 0f, 0.4f, 0.6f, 0.95f, decay = 0.08f, seed = 11)
            for (k in 0 until 6) tone(it, k * 0.04f, 0.15f, 2500f + k * 410f, 2400f + k * 400f, 0.12f, 0, decay = 0.04f)
        }
        Sound.BREAK_WOOD -> buf(0.35f).also {
            noise(it, 0f, 0.05f, 0.9f, 0.5f, decay = 0.012f, seed = 12)
            noise(it, 0.04f, 0.2f, 0.5f, 0.3f, decay = 0.05f, seed = 13)
            tone(it, 0f, 0.2f, 240f, 130f, 0.45f, 2, decay = 0.05f)
        }
        Sound.BREAK_STONE -> buf(0.5f).also {
            noise(it, 0f, 0.5f, 0.9f, 0.18f, decay = 0.12f, seed = 14)
            tone(it, 0f, 0.3f, 110f, 60f, 0.5f, 0, decay = 0.08f)
        }
        Sound.BREAK_METAL -> buf(0.6f).also {
            tone(it, 0f, 0.6f, 880f, 820f, 0.3f, 0, decay = 0.18f)
            tone(it, 0f, 0.6f, 1190f, 1100f, 0.22f, 0, decay = 0.14f)
            noise(it, 0f, 0.1f, 0.7f, 0.5f, decay = 0.03f, seed = 15)
        }
        Sound.BREAK_PLASTIC -> buf(0.25f).also {
            tone(it, 0f, 0.2f, 500f, 900f, 0.4f, 2, decay = 0.05f)
            noise(it, 0f, 0.05f, 0.4f, 0.6f, decay = 0.015f, seed = 16)
        }
        Sound.THUD -> buf(0.3f).also {
            tone(it, 0f, 0.3f, 100f, 45f, 0.9f, 0, decay = 0.08f)
            noise(it, 0f, 0.08f, 0.4f, 0.2f, decay = 0.03f, seed = 17)
        }
        Sound.CRUSH -> buf(0.45f).also {
            tone(it, 0f, 0.3f, 80f, 40f, 0.8f, 0, decay = 0.1f)
            noise(it, 0f, 0.4f, 0.8f, 0.25f, decay = 0.1f, seed = 18)
        }
        Sound.SHOCK -> buf(0.5f).also {
            val r = Random(19)
            for (i in it.indices) {
                val t = i.toFloat() / rate
                val buzz = square((t * 120f).toDouble()) * (if (r.nextFloat() < 0.3f) 1f else 0.2f)
                it[i] += buzz * 0.45f * env(t, 0.005f, 0.18f)
            }
            tone(it, 0f, 0.4f, 2400f, 900f, 0.15f, 1, decay = 0.1f)
        }
        Sound.EXPLOSION -> buf(0.9f).also {
            noise(it, 0f, 0.9f, 1.0f, 0.1f, decay = 0.25f, seed = 20)
            noise(it, 0f, 0.1f, 0.8f, 0.7f, decay = 0.03f, seed = 21)
            tone(it, 0f, 0.6f, 70f, 30f, 0.9f, 0, decay = 0.2f)
        }
        Sound.SPLASH -> buf(0.6f).also {
            noise(it, 0f, 0.6f, 0.7f, 0.45f, decay = 0.18f, seed = 22)
            for (k in 0 until 5) tone(it, k * 0.07f, 0.08f, 600f + k * 90f, 1200f + k * 120f, 0.12f, 0, decay = 0.03f)
        }
        Sound.COIN -> buf(0.25f).also {
            tone(it, 0f, 0.06f, 988f, 988f, 0.35f, 1, decay = 0.05f)
            tone(it, 0.06f, 0.19f, 1319f, 1319f, 0.35f, 1, decay = 0.08f)
        }
        Sound.GOLD -> buf(0.7f).also {
            val notes = floatArrayOf(784f, 988f, 1175f, 1568f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.08f, 0.35f, f, f, 0.3f, 1, decay = 0.12f) }
            tone(it, 0.32f, 0.38f, 2093f, 2093f, 0.2f, 0, decay = 0.15f)
        }
        Sound.STAR -> buf(0.35f).also {
            tone(it, 0f, 0.35f, 1047f, 1047f, 0.3f, 2, decay = 0.12f)
            tone(it, 0f, 0.35f, 1568f, 1568f, 0.2f, 0, decay = 0.1f)
        }
        Sound.WIN -> buf(1.0f).also {
            val notes = floatArrayOf(523f, 659f, 784f, 1047f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.11f, 0.3f, f, f, 0.3f, 1, decay = 0.12f) }
            tone(it, 0.45f, 0.55f, 1047f, 1047f, 0.25f, 1, decay = 0.25f)
            tone(it, 0.45f, 0.55f, 1319f, 1319f, 0.18f, 2, decay = 0.25f)
        }
        Sound.LOSE -> buf(0.9f).also {
            val notes = floatArrayOf(392f, 370f, 349f, 262f)
            notes.forEachIndexed { i, f -> tone(it, i * 0.16f, 0.3f, f, f * 0.98f, 0.3f, 2, decay = 0.15f) }
        }
        Sound.BONUS -> buf(0.4f).also {
            tone(it, 0f, 0.4f, 600f, 1600f, 0.3f, 2, decay = 0.15f)
            tone(it, 0.1f, 0.3f, 1200f, 2400f, 0.15f, 0, decay = 0.1f)
        }
        Sound.PURCHASE -> buf(0.5f).also {
            tone(it, 0f, 0.08f, 1319f, 1319f, 0.3f, 1, decay = 0.05f)
            tone(it, 0.08f, 0.08f, 1568f, 1568f, 0.3f, 1, decay = 0.05f)
            tone(it, 0.16f, 0.3f, 2093f, 2093f, 0.3f, 1, decay = 0.12f)
        }
    }

    private fun writeWav(f: File, data: FloatArray) {
        // Normalise softly and add a tiny fade-out to avoid clicks.
        val peak = data.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(0.001f) ?: 1f
        val gain = if (peak > 0.95f) 0.95f / peak else 1f
        val fade = (rate * 0.01f).toInt()
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in data.indices) {
            var v = data[i] * gain
            if (i > data.size - fade) v *= (data.size - i).toFloat() / fade
            pcm.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size * 2)
        }
        FileOutputStream(f).use { it.write(header.array()); it.write(pcm.array()) }
    }

    companion object {
        const val VERSION = 1
    }
}
