package com.ottawahomeprice.core

import kotlin.math.abs
import kotlin.math.sign

/**
 * Monotone piecewise-cubic Hermite interpolation (Fritsch-Carlson, as in PCHIP): smooth through
 * every knot, and never overshoots between them, so a run of rising annual averages never
 * interpolates to a dip and a flat stretch stays flat.
 */
class Pchip(private val xs: DoubleArray, private val ys: DoubleArray) {
    private val slopes: DoubleArray

    init {
        require(xs.size == ys.size && xs.size >= 2) { "Need at least two knots" }
        for (i in 1 until xs.size) require(xs[i] > xs[i - 1]) { "Knots must be strictly increasing" }
        slopes = tangents()
    }

    val first: Double get() = xs.first()
    val last: Double get() = xs.last()

    /** Value at [x]; clamped to the end values outside the knots. */
    fun value(x: Double): Double {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val i = segment(x)
        val h = xs[i + 1] - xs[i]
        val t = (x - xs[i]) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * slopes[i] +
            (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * slopes[i + 1]
    }

    /** First derivative at [x] (0 outside the knots). */
    fun slope(x: Double): Double {
        if (x < xs.first() || x > xs.last()) return 0.0
        if (x == xs.last()) return slopes.last()
        val i = segment(x)
        val h = xs[i + 1] - xs[i]
        val t = (x - xs[i]) / h
        val t2 = t * t
        return ((6 * t2 - 6 * t) * ys[i] + (3 * t2 - 4 * t + 1) * h * slopes[i] +
            (-6 * t2 + 6 * t) * ys[i + 1] + (3 * t2 - 2 * t) * h * slopes[i + 1]) / h
    }

    /** Index of the knot at or before [x], for x inside the knots. */
    fun segment(x: Double): Int {
        var lo = 0
        var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        return lo
    }

    private fun tangents(): DoubleArray {
        val n = xs.size
        val h = DoubleArray(n - 1) { xs[it + 1] - xs[it] }
        val delta = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / h[it] }
        val m = DoubleArray(n)
        if (n == 2) {
            m[0] = delta[0]; m[1] = delta[0]
            return m
        }
        for (k in 1 until n - 1) {
            m[k] = if (delta[k - 1] == 0.0 || delta[k] == 0.0 || sign(delta[k - 1]) != sign(delta[k])) {
                0.0
            } else {
                // Weighted harmonic mean (Fritsch-Butland), which keeps each piece monotone.
                val w1 = 2 * h[k] + h[k - 1]
                val w2 = h[k] + 2 * h[k - 1]
                (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k])
            }
        }
        m[0] = endTangent(h[0], h[1], delta[0], delta[1])
        m[n - 1] = endTangent(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3])
        return m
    }

    /** Three-point end slope, shape-preserving (as in MATLAB/SciPy pchip). */
    private fun endTangent(h0: Double, h1: Double, d0: Double, d1: Double): Double {
        val d = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
        return when {
            sign(d) != sign(d0) -> 0.0
            sign(d0) != sign(d1) && abs(d) > abs(3 * d0) -> 3 * d0
            else -> d
        }
    }
}
