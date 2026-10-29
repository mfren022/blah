package com.ottawahomeprice.core

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatorTest {
    private val estimator = Estimator(TestData.data())
    private val glebe = estimator.locate(LatLon(45.4010, -75.6900))

    @Test
    fun priceIsCityTimesLocationTimesType() {
        val month = Month.of(2010, 7)
        val city = estimator.index.price(month)
        val all = estimator.estimate(glebe, month, PropertyType.ALL)
        assertEquals(city * glebe.factor, all.price, 1e-6)
        assertEquals(city, all.cityAverage, 1e-6)
        val detached = estimator.estimate(glebe, month, PropertyType.DETACHED)
        assertEquals(all.price * 1.2, detached.price, 1e-6)
        // Types missing from types.csv count as all homes.
        assertEquals(all.price, estimator.estimate(glebe, month, PropertyType.SEMI).price, 1e-6)
        assertTrue(detached.sigma > all.sigma)
        assertEquals(detached.price * exp(-detached.sigma), detached.low, 1e-6)
        assertEquals(detached.price * exp(detached.sigma), detached.high, 1e-6)
    }

    @Test
    fun withoutALocationItIsTheCityAverage() {
        val e = estimator.estimate(null, Month.of(2000, 7), PropertyType.ALL)
        assertEquals(e.cityAverage, e.price, 1e-6)
        assertEquals(1.0, e.relative, 0.0)
        assertEquals(0.04, e.sigma, 1e-12)
    }

    @Test
    fun seriesMatchesSingleEstimates() {
        val from = Month.of(1950, 1)
        val to = Month.of(2030, 12)
        val series = estimator.series(glebe, PropertyType.CONDO, from, to)
        assertEquals(to - from + 1, series.size)
        for (m in listOf(Month.of(1950, 1), Month.of(1987, 6), Month.of(2026, 9), Month.of(2030, 12))) {
            val e = estimator.estimate(glebe, m, PropertyType.CONDO)
            val i = series.indexOf(m)
            assertEquals(e.price, series.price[i], 1e-6)
            assertEquals(e.low, series.low[i], 1e-6)
            assertEquals(e.high, series.high[i], 1e-6)
            assertEquals(e.inReferenceDollars, series.price[i] * series.toReference[i], 1e-6)
        }
        assertEquals(0, series.indexOf(Month.of(1900, 1)))
        assertEquals(series.size - 1, series.indexOf(Month.of(2100, 1)))
    }

    @Test
    fun rangeComesFromMeta() {
        assertEquals(Month.of(1914, 1), estimator.range.start)
        assertEquals(Month.of(2060, 12), estimator.range.endInclusive)
        assertEquals(Month.of(2026, 9), estimator.referenceMonth)
    }

    @Test
    fun boundaryCheck() {
        assertTrue(estimator.inOttawa(LatLon(45.42, -75.70)))
        assertTrue(!estimator.inOttawa(LatLon(45.70, -75.70)))
        assertTrue(estimator.inOttawa(LatLon(45.605, -75.70), toleranceKm = 1.0))
    }
}
