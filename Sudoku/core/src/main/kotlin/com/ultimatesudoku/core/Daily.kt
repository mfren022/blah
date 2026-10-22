package com.ultimatesudoku.core

import java.util.Locale
import kotlin.random.Random

/** One puzzle per calendar day, identical on every device, easing in early in the week. */
object Daily {
    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    fun difficulty(dayOfWeek: Int): Difficulty = when (dayOfWeek) {
        1 -> Difficulty.EASY
        2, 3 -> Difficulty.MEDIUM
        4, 5 -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }

    /** ISO date, the same text as java.time.LocalDate.toString(), whatever the device language. */
    fun tag(year: Int, month: Int, day: Int) = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    fun puzzle(year: Int, month: Int, day: Int, dayOfWeek: Int): Puzzle {
        val seed = (year * 10_000L + month * 100L + day) * 7_919L + 0x5D0C
        return Generator(Random(seed)).generate(difficulty(dayOfWeek))
    }
}
