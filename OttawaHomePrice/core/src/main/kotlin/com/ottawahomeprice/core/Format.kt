package com.ottawahomeprice.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** Dollar and ratio wording, independent of the phone's locale so figures always read as Canadian dollars. */
object Format {

    /** Rounds to a precision that doesn't pretend to more than the estimate knows. */
    fun roundPrice(price: Double): Long {
        val step = when {
            price >= 100_000 -> 1_000.0
            price >= 10_000 -> 100.0
            price >= 1_000 -> 10.0
            else -> 1.0
        }
        return ((price / step).roundToLong() * step).roundToLong()
    }

    /** "$812,000". */
    fun money(price: Double): String = "$" + group(roundPrice(price))

    /** "$812K", "$1.24M", "$4.5K", "$980": for chart axes and tight spaces. */
    fun compact(price: Double): String = when {
        price >= 9_995_000 -> "$" + decimals(price / 1_000_000, 0) + "M"
        price >= 999_500 -> "$" + decimals(price / 1_000_000, 2).trimEnd('0').trimEnd('.') + "M"
        price >= 100_000 -> "$" + decimals(price / 1_000, 0) + "K"
        price >= 10_000 -> "$" + decimals(price / 1_000, 0) + "K"
        price >= 1_000 -> "$" + decimals(price / 1_000, 1).trimEnd('0').trimEnd('.') + "K"
        else -> "$" + decimals(price, 0)
    }

    /** "$700K – $940K". */
    fun range(low: Double, high: Double): String = compact(low) + " – " + compact(high)

    /** "1.24×". */
    fun times(ratio: Double): String = decimals(ratio, 2) + "×"

    /** "24% above", "18% below" or "about the same as" the city average. */
    fun versusAverage(ratio: Double): String {
        val percent = ((ratio - 1) * 100).roundToLong()
        return when {
            abs(percent) < 3 -> "About the same as the Ottawa average"
            percent > 0 -> "$percent% above the Ottawa average"
            else -> "${-percent}% below the Ottawa average"
        }
    }

    /** "+4.2%" / "−1.0%". */
    fun signedPercent(fraction: Double): String {
        val text = decimals(abs(fraction) * 100, 1)
        return (if (fraction < 0) "−" else "+") + text + "%"
    }

    fun distance(km: Double): String = when {
        km < 1 -> "${(km * 1000 / 50).roundToLong() * 50} m"
        km < 10 -> decimals(km, 1) + " km"
        else -> "${km.roundToLong()} km"
    }

    fun group(value: Long): String {
        val digits = abs(value).toString()
        val out = StringBuilder()
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
            out.append(digits[i])
        }
        return (if (value < 0) "-" else "") + out
    }

    fun decimals(value: Double, places: Int): String {
        if (places == 0) return group(value.roundToLong())
        var scale = 1L
        repeat(places) { scale *= 10 }
        val scaled = (abs(value) * scale).roundToLong()
        val whole = scaled / scale
        val fraction = (scaled % scale).toString().padStart(places, '0')
        return (if (value < 0 && scaled != 0L) "-" else "") + group(whole) + "." + fraction
    }
}
