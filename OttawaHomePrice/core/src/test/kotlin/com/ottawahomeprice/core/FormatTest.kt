package com.ottawahomeprice.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun money() {
        assertEquals("$812,000", Format.money(812_345.0))
        assertEquals("$45,700", Format.money(45_678.0))
        assertEquals("$4,570", Format.money(4_567.0))
        assertEquals("$1,250,000", Format.money(1_249_600.0))
    }

    @Test
    fun compact() {
        assertEquals("$812K", Format.compact(812_345.0))
        assertEquals("$1.24M", Format.compact(1_240_000.0))
        assertEquals("$1M", Format.compact(1_000_000.0))
        assertEquals("$1M", Format.compact(999_700.0))
        assertEquals("$12M", Format.compact(12_300_000.0))
        assertEquals("$46K", Format.compact(45_678.0))
        assertEquals("$4.6K", Format.compact(4_567.0))
        assertEquals("$5K", Format.compact(5_000.0))
        assertEquals("$980", Format.compact(980.0))
    }

    @Test
    fun ratios() {
        assertEquals("1.24×", Format.times(1.2381))
        assertEquals("24% above the Ottawa average", Format.versusAverage(1.24))
        assertEquals("18% below the Ottawa average", Format.versusAverage(0.82))
        assertEquals("About the same as the Ottawa average", Format.versusAverage(1.02))
        assertEquals("+4.2%", Format.signedPercent(0.042))
        assertEquals("−1.0%", Format.signedPercent(-0.0099))
    }

    @Test
    fun distances() {
        assertEquals("350 m", Format.distance(0.34))
        assertEquals("2.5 km", Format.distance(2.46))
        assertEquals("14 km", Format.distance(13.6))
    }

    @Test
    fun decimals() {
        assertEquals("1,234.50", Format.decimals(1234.5, 2))
        assertEquals("-0.30", Format.decimals(-0.3, 2))
        assertEquals("0.0", Format.decimals(-0.01, 1))
    }
}
