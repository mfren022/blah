package com.ottawahomeprice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceIndexTest {
    private val data = TestData.data()
    private val index = PlaceIndex(data.neighbourhoods, data.postalAreas)

    @Test
    fun normalizesAccentsCaseAndPunctuation() {
        assertEquals("orleans", Text.normalize("Orléans"))
        assertEquals("morgans grant", Text.normalize("Morgan’s Grant"))
        assertEquals("st laurent blvd", Text.normalize("  St-Laurent   Blvd. "))
    }

    @Test
    fun findsPostalCodes() {
        assertEquals(PostalCode("K1S", "K1S 5B6"), PostalCode.find("123 Bank St, Ottawa, ON k1s5b6"))
        assertEquals(PostalCode("K2P", "K2P 1L4"), PostalCode.find("K2P-1L4"))
        assertEquals(PostalCode("K1L", null), PostalCode.find("k1l"))
        assertNull(PostalCode.find("123 Bank Street"))
        // D, F, I, O, Q, U are never used.
        assertNull(PostalCode.find("K1D"))
        assertTrue(PostalCode.isOnlyPostal(" K1S 5B6 "))
        assertTrue(PostalCode.isOnlyPostal("K1S"))
        assertFalse(PostalCode.isOnlyPostal("12 Main St K1S 5B6"))
    }

    @Test
    fun searchRanksExactThenPrefix() {
        val results = index.search("orleans")
        assertEquals("Orléans", results.first().label)
        assertEquals("Westboro", index.search("west").first().label)
        assertEquals("Westboro", index.search("Westboro Beach").first().label)
        assertEquals("Old Ottawa East", index.search("ottawa east").first().label)
        // An exact alias still wins over a mere prefix of a name.
        assertEquals("Vanier", index.search("eastview").first().label)
        assertTrue(index.search("x").isEmpty())
        assertTrue(index.search("zzzz").isEmpty())
    }

    @Test
    fun postalAreasComeFirst() {
        val results = index.search("K1S 5B6")
        assertEquals(PlaceIndex.Kind.POSTAL_AREA, results.first().kind)
        assertEquals(LatLon(45.3990, -75.6880), results.first().location)
        assertNull(index.postalMatch("K9Z 1A1"))
    }

    @Test
    fun findsANeighbourhoodInsideAnAddress() {
        assertEquals("Old Ottawa East", index.neighbourhoodIn("12 Main St, Old Ottawa East, Ottawa")?.name)
        assertEquals("Vanier", index.neighbourhoodIn("300 Montreal Rd, Eastview")?.name)
        assertNull(index.neighbourhoodIn("12 Main Street"))
        // Part of a street name: no.
        assertNull(index.neighbourhoodIn("1000 Vanier Pkwy"))
        assertNull(index.neighbourhoodIn("200 Westboro Road"))
        assertEquals("Westboro", index.neighbourhoodIn("200 Main St, Westboro")?.name)
    }
}
