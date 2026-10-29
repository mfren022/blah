package com.ottawahomeprice.app

import com.ottawahomeprice.app.geo.AddressLookup
import com.ottawahomeprice.app.geo.GeocodedAddress
import com.ottawahomeprice.app.geo.LookupResult
import com.ottawahomeprice.app.geo.Nominatim
import com.ottawahomeprice.app.geo.Place
import com.ottawahomeprice.core.LatLon
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The search logic on its own: what goes to the geocoders and what comes back. */
@RunWith(RobolectricTestRunner::class)
class AddressLookupTest : ScreenTest() {

    private val parliament = LatLon(45.4236, -75.7009)

    private fun lookupWith(vararg providers: FakeGeocoder) = AddressLookup(app.estimator, providers.toList())

    @Test
    fun addsTheCityUnlessThereIsOne() {
        val fake = FakeGeocoder(emptyMap())
        val lookup = lookupWith(fake)
        lookup.lookup("111 Wellington St")
        lookup.lookup("111 Wellington St, Ottawa")
        lookup.lookup("111 Wellington St K1A 0A9")
        assertEquals(listOf("111 Wellington St, Ottawa, ON", "111 Wellington St, Ottawa", "111 Wellington St K1A 0A9"), fake.queries)
    }

    @Test
    fun fallsThroughToTheNextProvider() {
        val broken = FakeGeocoder(emptyMap(), offline = true)
        val osm = FakeGeocoder(mapOf("111 Wellington" to listOf(GeocodedAddress("111 Wellington St, Ottawa, ON", parliament, "Ottawa", "Ontario"))))
        val result = lookupWith(broken, osm).lookup("111 Wellington St")
        assertTrue(result is LookupResult.Found)
        assertEquals(parliament, (result as LookupResult.Found).places.single().location)
        assertEquals(1, broken.queries.size)
        // A geocoder that only finds Gatineau doesn't stop the next one from finding Ottawa.
        val quebec = FakeGeocoder(mapOf("111" to listOf(GeocodedAddress("111 Rue Wellington, Gatineau", LatLon(45.48, -75.70), "Gatineau", "Québec"))))
        assertTrue(lookupWith(quebec, osm).lookup("111 Wellington St") is LookupResult.Found)
    }

    @Test
    fun duplicatesCollapse() {
        val fake = FakeGeocoder(
            mapOf(
                "1 Main" to listOf(
                    GeocodedAddress("1 Main St, Ottawa", LatLon(45.4085, -75.6765), "Ottawa", "Ontario"),
                    GeocodedAddress("1 Main Street, Ottawa", LatLon(45.40852, -75.67651), "Ottawa", "Ontario"),
                    GeocodedAddress("1 Main St, Kanata, Ottawa", LatLon(45.3200, -75.9000), "Ottawa", "Ontario"),
                ),
            ),
        )
        val found = lookupWith(fake).lookup("1 Main St") as LookupResult.Found
        assertEquals(2, found.places.size)
    }

    @Test
    fun exactNeighbourhoodNamesNeedNoNetwork() {
        val fake = FakeGeocoder(emptyMap(), offline = true)
        val n = app.estimator.data.neighbourhoods.first()
        val found = lookupWith(fake).lookup(n.name.uppercase()) as LookupResult.Found
        assertEquals(Place.Source.NEIGHBOURHOOD, found.places.single().source)
        assertEquals(n.location, found.places.single().location)
        assertTrue(fake.queries.isEmpty())
    }

    @Test
    fun fullPostalCodesAskTheGeocoderFirst() {
        val exact = LatLon(45.3985, -75.6870)
        val fake = FakeGeocoder(mapOf("K1S 5B6" to listOf(GeocodedAddress("Ottawa, ON K1S 5B6", exact, "Ottawa", "Ontario"))))
        val found = lookupWith(fake).lookup("k1s5b6") as LookupResult.Found
        assertEquals(exact, found.places.single().location)
        // Unknown to the geocoder: the postal area's centre, not marked approximate.
        val offline = lookupWith(FakeGeocoder(emptyMap(), offline = true)).lookup("K1S 5B6") as LookupResult.Found
        assertEquals(Place.Source.POSTAL_AREA, offline.places.single().source)
        assertTrue(offline.places.single().detail.startsWith("Centre of postal area K1S"))
    }

    @Test
    fun saysUnreachableOnlyWhenNoServiceAnswered() {
        // The phone has no geocoder, but OpenStreetMap answers: a spelling problem, not a network one.
        val noGeocoder = FakeGeocoder(emptyMap(), offline = true)
        val result = lookupWith(noGeocoder, FakeGeocoder(emptyMap())).lookup("qwertyuiop street") as LookupResult.NotFound
        assertTrue(result.message, result.message.contains("No Ottawa address matches"))
        val offline = lookupWith(noGeocoder, FakeGeocoder(emptyMap(), offline = true)).lookup("qwertyuiop street") as LookupResult.NotFound
        assertTrue(offline.message, offline.message.contains("Couldn't reach"))
    }

    @Test
    fun emptyAndUnknown() {
        assertTrue(lookupWith().lookup(" ") is LookupResult.NotFound)
        val result = lookupWith(FakeGeocoder(emptyMap())).lookup("qwertyuiop street")
        assertTrue(result is LookupResult.NotFound)
        assertTrue((result as LookupResult.NotFound).message.contains("No Ottawa address matches"))
    }

    @Test
    fun mapPointsAreNamedByTheirArea() {
        val place = app.lookup.mapPlace(parliament)
        assertEquals(Place.Source.MAP, place.source)
        assertTrue(place.title, place.title.startsWith("Map point in ") || place.title.startsWith("Map point near "))
        assertEquals("45.42360, -75.70090", place.detail)
    }

    @Test
    fun readsNominatimAnswers() {
        val json = """
            [{"lat":"45.4085","lon":"-75.6765","display_name":"100, Main Street, Old Ottawa East, Ottawa, Ontario, K1S 1C2, Canada",
              "address":{"house_number":"100","road":"Main Street","suburb":"Old Ottawa East","city":"Ottawa","state":"Ontario","postcode":"K1S 1C2"}},
             {"lat":"45.48","lon":"-75.70","display_name":"Gatineau, Québec","address":{"city":"Gatineau","state":"Québec"}},
             {"lat":"oops","lon":"-75.70"}]
        """.trimIndent()
        val parsed = Nominatim.parse(json)
        assertEquals(2, parsed.size)
        assertEquals("100 Main Street, Old Ottawa East, Ottawa, Ontario K1S 1C2", parsed[0].label)
        assertEquals("Ottawa", parsed[0].locality)
        assertEquals("Gatineau, Québec", parsed[1].label)
        assertEquals("Québec", parsed[1].province)
        try {
            Nominatim.parse("<html>rate limited</html>")
            throw AssertionError("expected IOException")
        } catch (expected: IOException) {
        }
    }
}
