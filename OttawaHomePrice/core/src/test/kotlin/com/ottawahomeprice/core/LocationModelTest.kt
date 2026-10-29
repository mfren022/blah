package com.ottawahomeprice.core

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationModelTest {
    private fun place(name: String, lat: Double, lon: Double, factor: Double, radius: Double = 1.0, confidence: Confidence = Confidence.HIGH) =
        Neighbourhood(name, emptyList(), LatLon(lat, lon), radius, factor, confidence, "", emptyList())

    @Test
    fun atAnAnchorItsFactorDominates() {
        val a = place("A", 45.40, -75.70, 2.0)
        val b = place("B", 45.40, -75.60, 0.5) // ~7.8 km east
        val model = LocationModel(listOf(a, b))
        val e = model.estimate(a.location)
        assertEquals(2.0, e.factor, 0.01)
        assertEquals("A", e.nearest.name)
        assertEquals(0.0, e.nearestKm, 1e-9)
        assertEquals("A", e.areaLabel)
    }

    @Test
    fun halfwayBetweenEqualAnchorsIsTheGeometricMean() {
        val a = place("A", 45.40, -75.70, 2.0)
        val b = place("B", 45.40, -75.68, 0.5)
        val model = LocationModel(listOf(a, b))
        val mid = model.estimate(LatLon(45.40, -75.69))
        assertEquals(sqrt(2.0 * 0.5), mid.factor, 1e-9)
        assertEquals(2, mid.contributors.size)
        assertEquals(0.5, mid.contributors[0].second, 1e-9)
        // A blend of very different neighbours is less certain than either one.
        assertTrue(mid.sigma > model.estimate(a.location).sigma)
    }

    @Test
    fun farFromEverythingTheNearestWins() {
        val a = place("A", 45.40, -75.70, 2.0)
        val b = place("B", 45.40, -75.50, 0.5)
        val model = LocationModel(listOf(a, b))
        val e = model.estimate(LatLon(45.40, -75.80)) // 7.8 km west of A, 23 km from B
        assertEquals(2.0, e.factor, 0.05)
        assertTrue(e.areaLabel.startsWith("near "))
        // Poorly covered: extra uncertainty.
        assertTrue(e.sigma > model.estimate(a.location).sigma + 0.1)
    }

    @Test
    fun widerAnchorsReachFurther() {
        val town = place("Town", 45.40, -75.70, 3.0, radius = 4.0)
        val street = place("Street", 45.40, -75.60, 1.0, radius = 0.5)
        val model = LocationModel(listOf(town, street))
        // 2 km from the town centre and 5.8 km from the street: the town.
        assertEquals(3.0, model.estimate(LatLon(45.40, -75.6745)).factor, 0.05)
    }

    @Test
    fun theAreaIsTheOneThePointIsMostInside() {
        // 1.2 km from a small neighbourhood's centre, 3 km from a big village's: in the village.
        val small = place("Small", 45.40, -75.70, 1.0, radius = 0.5)
        val village = place("Village", 45.40, -75.7536, 1.0, radius = 4.0)
        val e = LocationModel(listOf(small, village)).estimate(LatLon(45.40, -75.7153))
        assertEquals("Small", e.nearest.name)
        assertEquals("Village", e.area.name)
        assertEquals("Village", e.areaLabel)
    }

    @Test
    fun lowConfidenceAnchorsCountLess() {
        val sure = place("Sure", 45.40, -75.70, 2.0, confidence = Confidence.HIGH)
        val guess = place("Guess", 45.40, -75.68, 0.5, confidence = Confidence.LOW)
        val mid = LocationModel(listOf(sure, guess)).estimate(LatLon(45.40, -75.69))
        assertTrue(mid.factor > 1.0)
    }

    @Test
    fun factorAtMatchesEstimate() {
        val data = TestData.data()
        val model = LocationModel(data.neighbourhoods)
        for (p in listOf(LatLon(45.41, -75.70), LatLon(45.30, -75.90), LatLon(45.45, -75.60))) {
            assertEquals(model.estimate(p).factor, model.factorAt(p.lat, p.lon), 1e-12)
        }
    }
}
