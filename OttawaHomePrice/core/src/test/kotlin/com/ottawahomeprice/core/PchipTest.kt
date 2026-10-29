package com.ottawahomeprice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PchipTest {
    private val xs = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 5.0, 6.0)
    private val ys = doubleArrayOf(1.0, 2.0, 2.0, 5.0, 6.0, 4.0)
    private val curve = Pchip(xs, ys)

    @Test
    fun passesThroughEveryKnot() {
        for (i in xs.indices) assertEquals(ys[i], curve.value(xs[i]), 1e-12)
    }

    @Test
    fun neverOvershootsBetweenKnots() {
        for (i in 0 until xs.size - 1) {
            val lo = minOf(ys[i], ys[i + 1])
            val hi = maxOf(ys[i], ys[i + 1])
            var x = xs[i]
            while (x <= xs[i + 1]) {
                val y = curve.value(x)
                assertTrue("overshoot at $x: $y", y >= lo - 1e-12 && y <= hi + 1e-12)
                x += 0.01
            }
        }
    }

    @Test
    fun flatStretchStaysFlat() {
        var x = 1.0
        while (x <= 2.0) { assertEquals(2.0, curve.value(x), 1e-12); x += 0.05 }
    }

    @Test
    fun monotoneDataGivesMonotoneCurve() {
        val c = Pchip(doubleArrayOf(0.0, 1.0, 4.0, 4.5, 10.0), doubleArrayOf(0.0, 0.1, 3.0, 3.05, 9.0))
        var previous = -1.0
        var x = 0.0
        while (x <= 10.0) {
            val y = c.value(x)
            assertTrue(y >= previous - 1e-12)
            previous = y
            x += 0.01
        }
    }

    @Test
    fun slopeMatchesFiniteDifference() {
        for (x in listOf(0.3, 1.7, 2.5, 4.1, 5.5)) {
            val numeric = (curve.value(x + 1e-6) - curve.value(x - 1e-6)) / 2e-6
            assertEquals(numeric, curve.slope(x), 1e-5)
        }
    }

    @Test
    fun clampsOutside() {
        assertEquals(1.0, curve.value(-3.0), 0.0)
        assertEquals(4.0, curve.value(9.0), 0.0)
    }
}
