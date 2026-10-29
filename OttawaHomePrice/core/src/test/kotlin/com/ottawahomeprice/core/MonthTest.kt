package com.ottawahomeprice.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthTest {
    @Test
    fun arithmeticAndLabels() {
        val m = Month.of(1990, 6)
        assertEquals(1990, m.year)
        assertEquals(6, m.month)
        assertEquals("1990-06", m.iso())
        assertEquals("June 1990", m.label())
        assertEquals("Jun 1990", m.shortLabel())
        assertEquals(Month.of(1991, 1), m + 7)
        assertEquals(Month.of(1989, 12), m - 6)
        assertEquals(19, Month.of(1992, 1) - m)
        assertEquals(m, Month.parse("1990-06"))
        assertEquals(1990 + 5.5 / 12, m.time, 1e-12)
        assertEquals(m, Month.containing(m.time))
        assertEquals(Month.of(1990, 1), Month.containing(1990.0))
        assertEquals(Month.of(1989, 12), Month.containing(1989.999))
    }

    @Test
    fun clamps() {
        val range = Month.of(1914, 1)..Month.of(2060, 12)
        assertEquals(range.start, Month.of(1800, 3).coerceIn(range))
        assertEquals(range.endInclusive, Month.of(2100, 3).coerceIn(range))
        assertEquals(Month.of(2000, 3), Month.of(2000, 3).coerceIn(range))
    }
}
