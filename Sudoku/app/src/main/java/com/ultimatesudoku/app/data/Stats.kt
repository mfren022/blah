package com.ultimatesudoku.app.data

import android.content.SharedPreferences
import com.ultimatesudoku.core.Difficulty
import java.time.LocalDate

/** Results per difficulty, plus daily challenge completions. */
class Stats(private val prefs: SharedPreferences) {

    class Record(
        val started: Int,
        val won: Int,
        val perfect: Int,
        val totalTimeMs: Long,
        val bestTimeMs: Long,
        val streak: Int,
        val bestStreak: Int,
    ) {
        val winRate: Int get() = if (started == 0) 0 else won * 100 / started
        val averageMs: Long get() = if (won == 0) 0 else totalTimeMs / won
    }

    fun record(d: Difficulty) = Record(
        started = prefs.getInt(key(d, "started"), 0),
        won = prefs.getInt(key(d, "won"), 0),
        perfect = prefs.getInt(key(d, "perfect"), 0),
        totalTimeMs = prefs.getLong(key(d, "total_ms"), 0),
        bestTimeMs = prefs.getLong(key(d, "best_ms"), 0),
        streak = prefs.getInt(key(d, "streak"), 0),
        bestStreak = prefs.getInt(key(d, "best_streak"), 0),
    )

    fun started(d: Difficulty) {
        prefs.edit().putInt(key(d, "started"), record(d).started + 1).apply()
    }

    /** Records a win and returns true if it beat the previous best time. */
    fun won(d: Difficulty, timeMs: Long, perfect: Boolean): Boolean {
        val r = record(d)
        val best = r.bestTimeMs == 0L || timeMs < r.bestTimeMs
        val streak = r.streak + 1
        prefs.edit()
            .putInt(key(d, "won"), r.won + 1)
            .putInt(key(d, "perfect"), r.perfect + if (perfect) 1 else 0)
            .putLong(key(d, "total_ms"), r.totalTimeMs + timeMs)
            .putLong(key(d, "best_ms"), if (best) timeMs else r.bestTimeMs)
            .putInt(key(d, "streak"), streak)
            .putInt(key(d, "best_streak"), maxOf(streak, r.bestStreak))
            .apply()
        return best && r.won > 0
    }

    /** A loss or an abandoned game ends the winning streak. */
    fun lost(d: Difficulty) {
        prefs.edit().putInt(key(d, "streak"), 0).apply()
    }

    fun completeDaily(tag: String, timeMs: Long) {
        val done = dailyDone() + tag
        prefs.edit().putStringSet(DAILY_DONE, done).putLong("daily.time.$tag", timeMs).apply()
    }

    fun isDailyDone(tag: String) = tag in dailyDone()

    fun dailyTime(tag: String): Long = prefs.getLong("daily.time.$tag", 0)

    val dailyCount: Int get() = dailyDone().size

    /** Consecutive days solved, ending today (or yesterday if today is still open). */
    fun dailyStreak(today: LocalDate): Int {
        val done = dailyDone()
        var day = if (done.contains(today.toString())) today else today.minusDays(1)
        var streak = 0
        while (day.toString() in done) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun bestDailyStreak(): Int {
        val days = dailyDone().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    fun reset() {
        val edit = prefs.edit()
        for (d in Difficulty.entries) {
            for (field in FIELDS) edit.remove(key(d, field))
        }
        for (tag in dailyDone()) edit.remove("daily.time.$tag")
        edit.remove(DAILY_DONE).apply()
    }

    private fun dailyDone(): Set<String> = prefs.getStringSet(DAILY_DONE, emptySet())!!.toSet()

    private fun key(d: Difficulty, field: String) = "stats.${d.name}.$field"

    private companion object {
        const val DAILY_DONE = "daily.done"
        val FIELDS = listOf("started", "won", "perfect", "total_ms", "best_ms", "streak", "best_streak")
    }
}
