package com.ottawahomeprice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun distances() {
        // Parliament Hill to Kanata, about 20 km as the crow flies.
        val parliament = LatLon(45.4236, -75.7009)
        val kanata = LatLon(45.3088, -75.8987)
        assertEquals(20.05, Geo.distanceKm(parliament, kanata), 0.2)
        assertEquals(Geo.distanceKm(parliament, kanata), LocalProjection.OTTAWA.distanceKm(parliament, kanata), 0.1)
    }

    @Test
    fun polygonContainsAndDistance() {
        val square = Polygon(listOf(LatLon(45.0, -76.0), LatLon(45.0, -75.0), LatLon(46.0, -75.0), LatLon(46.0, -76.0)))
        assertTrue(square.contains(LatLon(45.5, -75.5)))
        assertFalse(square.contains(LatLon(46.5, -75.5)))
        assertFalse(square.contains(LatLon(45.5, -74.9)))
        assertEquals(0.0, square.distanceToEdgeKm(LatLon(45.0, -75.5)), 1e-6)
        assertEquals(11.1, square.distanceToEdgeKm(LatLon(45.1, -75.5)), 0.1)
        // A concave shape: an L.
        val l = Polygon(listOf(LatLon(0.0, 0.0), LatLon(0.0, 2.0), LatLon(1.0, 2.0), LatLon(1.0, 1.0), LatLon(2.0, 1.0), LatLon(2.0, 0.0)))
        assertTrue(l.contains(LatLon(1.5, 0.5)))
        assertFalse(l.contains(LatLon(1.5, 1.5)))
    }
}
