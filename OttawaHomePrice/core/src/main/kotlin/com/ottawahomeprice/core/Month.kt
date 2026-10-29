package com.ottawahomeprice.core

/**
 * A calendar month, stored as a count of months since year 0 so ranges and arithmetic are plain
 * integers. [time] is the middle of the month in fractional years (June 1990 is 1990.458).
 */
@JvmInline
value class Month(val index: Int) : Comparable<Month> {
    val year: Int get() = Math.floorDiv(index, 12)

    /** 1 to 12. */
    val month: Int get() = Math.floorMod(index, 12) + 1

    val time: Double get() = year + (month - 0.5) / 12.0

    operator fun plus(months: Int): Month = Month(index + months)
    operator fun minus(months: Int): Month = Month(index - months)
    operator fun minus(other: Month): Int = index - other.index
    override fun compareTo(other: Month): Int = index.compareTo(other.index)

    fun coerceIn(range: ClosedRange<Month>): Month = when {
        this < range.start -> range.start
        this > range.endInclusive -> range.endInclusive
        else -> this
    }

    /** "1990-06", the form the data files use. */
    fun iso(): String = year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0')

    /** "June 1990". */
    fun label(): String = "${NAMES[month - 1]} $year"

    /** "Jun 1990". */
    fun shortLabel(): String = "${NAMES[month - 1].take(3)} $year"

    override fun toString(): String = iso()

    companion object {
        val NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        fun of(year: Int, month: Int): Month {
            require(month in 1..12) { "Month must be 1-12, was $month" }
            return Month(year * 12 + month - 1)
        }

        /** Parses "1990-06". */
        fun parse(text: String): Month {
            val parts = text.trim().split('-')
            require(parts.size == 2) { "Expected YYYY-MM, found '$text'" }
            return of(parts[0].toInt(), parts[1].toInt())
        }

        /** The month containing fractional-year [time]. */
        fun containing(time: Double): Month = Month(Math.floor(time * 12).toInt())
    }
}
