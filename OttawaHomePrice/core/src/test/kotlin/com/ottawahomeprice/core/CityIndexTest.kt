package com.ottawahomeprice.core

import kotlin.math.abs
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityIndexTest {
    private val index = CityIndex(TestData.data())

    @Test
    fun yearlyAveragesSitAtMidYear() {
        // July 1990 straddles 1990.5 (mid-June to mid-July is 1990.458..1990.542).
        val june = index.price(Month.of(1990, 6))
        val july = index.price(Month.of(1990, 7))
        assertTrue(june < 140000 && july > 140000)
        assertEquals(140000.0, kotlin.math.exp(index.logPrice(1990.5)), 1e-6)
        assertEquals(Basis.ANNUAL, index.basis(Month.of(1995, 1)))
    }

    @Test
    fun censusSegmentsAreMarked() {
        assertEquals(Basis.CENSUS, index.basis(Month.of(1955, 1)))
        assertEquals(0.12, index.sigma(Month.of(1955, 1)), 1e-12)
    }

    @Test
    fun backCastFollowsInflation() {
        val first = 1951.5
        // 1914 CPI is half of 1951's, so the price halves (no real growth assumed).
        val price1914 = index.price(Month.of(1914, 7))
        assertEquals(5000.0, price1914, 5000.0 * 0.01)
        assertEquals(Basis.BACKCAST, index.basis(Month.of(1930, 1)))
        // Uncertainty grows the further back.
        assertTrue(index.sigma(Month.of(1920, 1)) > index.sigma(Month.of(1945, 1)))
        assertTrue(index.sigma(Month.of(1945, 1)) > 0.12)
        // Continuous at the first data point.
        assertEquals(10000.0, kotlin.math.exp(index.logPrice(first - 1e-9)), 1.0)
    }

    @Test
    fun projectionGrowsAtTheAssumedRate() {
        val a = index.price(Month.of(2030, 7))
        val b = index.price(Month.of(2031, 7))
        assertEquals(1.035, b / a, 1e-9)
        assertEquals(Basis.PROJECTION, index.basis(Month.of(2030, 1)))
        assertTrue(index.sigma(Month.of(2040, 1)) > index.sigma(Month.of(2030, 1)))
        assertTrue(index.sigma(Month.of(2060, 12)) <= 0.8)
    }

    @Test
    fun monthlyDataExtendsPastTheLastFullYear() {
        // 2024-10 .. 2026-09 at 625k, 10% higher from 2025-10 on, 1000 sales a month.
        val rows = StringBuilder("month,avg_price,units,source,note\n")
        var m = Month.of(2024, 10)
        while (m <= Month.of(2026, 9)) {
            val k = if (m >= Month.of(2025, 10)) 1.1 else 1.0
            rows.append("${m.iso()},${625000 * k},1000,s,\n")
            m += 1
        }
        val idx = CityIndex(TestData.data("monthly.csv" to rows.toString()))
        // Trailing twelve months (2025-10..2026-09) are all at +10%, centred at 2026.25.
        assertEquals(625000 * 1.1, kotlin.math.exp(idx.logPrice(2026.25)), 1.0)
        // Year-over-year change of the last three months is +10%: the latest month is carried forward at that rate.
        val latest = Month.of(2026, 9)
        val expected = ln(625000 * 1.1) + ln(1.1) * (latest.time - 2026.25)
        assertEquals(expected, idx.logPrice(latest.time), 1e-9)
        assertEquals(Basis.RECENT, idx.basis(Month.of(2026, 6)))
        assertEquals(Basis.PROJECTION, idx.basis(Month.of(2026, 10)))
        assertEquals(latest, idx.lastDataMonth)
    }

    @Test
    fun aSingleMonthAndItsYearAgoAreEnough() {
        // Only September 2026 and September 2025: 1% lower over the year.
        val rows = "month,avg_price,units,source,note\n2025-09,700000,1081,s,\n2026-09,693000,1010,s,"
        val idx = CityIndex(TestData.data("monthly.csv" to rows))
        val latest = Month.of(2026, 9)
        // Carried from the 2025 annual average (650k at mid-2025) at -1% a year.
        val expected = ln(650000.0) + ln(0.99) * (latest.time - 2025.5)
        assertEquals(expected, idx.logPrice(latest.time), 1e-9)
        assertEquals(latest, idx.lastDataMonth)
        assertEquals(Basis.RECENT, idx.basis(Month.of(2026, 1)))
    }

    @Test
    fun partialYearOfMonthsStillCounts() {
        val rows = "month,avg_price,units,source,note\n" + (1..5).joinToString("\n") { "2026-0$it,700000,,s," }
        val idx = CityIndex(TestData.data("monthly.csv" to rows))
        val center = (Month.of(2026, 1).time + Month.of(2026, 5).time) / 2
        assertEquals(700000.0, kotlin.math.exp(idx.logPrice(center)), 1.0)
        // No year-ago months: the curve ends at the partial-year average.
        assertEquals(Month.containing(center), idx.lastDataMonth)
    }

    @Test
    fun inflationAdjustment() {
        val cpi = index.cpi
        assertEquals(168.0, cpi.at(Month.of(2026, 9)), 1e-9)
        // Flat before the first CPI year, grown at 2% after the latest month.
        assertEquals(6.0, cpi.at(1914.0), 1e-9)
        assertEquals(168.0 * 1.02, cpi.at(Month.of(2027, 9)), 1e-6)
        val ref = Month.of(2026, 9)
        assertEquals(100.0 * 168.0 / 44.0, index.inDollarsOf(100.0, Month.of(1980, 7), ref), 2.0)
    }

    @Test
    fun smoothAcrossMonths() {
        // No month-to-month jump bigger than 3% anywhere in the observed era.
        var m = Month.of(1952, 1)
        var previous = index.price(m)
        while (m < Month.of(2025, 6)) {
            m += 1
            val p = index.price(m)
            assertTrue("jump at $m", abs(p / previous - 1) < 0.03)
            previous = p
        }
    }
}
